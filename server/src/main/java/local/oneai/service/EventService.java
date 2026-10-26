package local.oneai.service;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

@Service
public class EventService {
    public static class Invalid extends RuntimeException {
        public final String code; public final int status;
        public Invalid(String code,int status) { super(code); this.code=code; this.status=status; }
    }
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper = new ObjectMapper();
    private final DateTimeFormatter time = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    public EventService(DataSource source) throws Exception {
        db = new JdbcTemplate(source);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql")).execute(source);
        try (var in = new ClassPathResource("db/documents.tsv").getInputStream()) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList()) {
                String[] cols=line.split("\t");
                db.update("INSERT OR IGNORE INTO documents VALUES (?)",cols[0]);
                db.update("INSERT OR IGNORE INTO document_versions VALUES (?, 'local-v1', ?)",cols[0],cols[1]);
            }
        }
    }
    public static boolean validUserId(String value) {
        return value != null && !value.isBlank() && value.length() <= 64 && value.codePoints().noneMatch(Character::isISOControl);
    }
    private void require(boolean condition) { if (!condition) throw new Invalid("VALIDATION_FAILED",400); }
    private void fields(JsonNode n,String... names) {
        require(n != null && n.isObject());
        Set<String> allowed=Set.of(names);
        n.fieldNames().forEachRemaining(key -> require(allowed.contains(key)));
        for (String key:names) require(n.has(key));
    }
    private String text(JsonNode n,String key,int max) {
        require(n.hasNonNull(key) && n.get(key).isTextual());
        String value=n.get(key).textValue();
        require(!value.isBlank() && value.length()<=max);
        return value;
    }
    private String id(JsonNode n,String key) {
        String value=text(n,key,36);
        try { require(UUID.fromString(value).toString().equals(value)); }
        catch (IllegalArgumentException e) { throw new Invalid("VALIDATION_FAILED",400); }
        return value;
    }
    private int number(JsonNode n,String key) {
        require(n.has(key) && n.get(key).isIntegralNumber() && n.get(key).canConvertToInt());
        return n.get(key).intValue();
    }
    private Map<String,Object> row(String sql,Object... args) {
        var rows=db.queryForList(sql,args);
        if (rows.isEmpty()) throw new Invalid("DEPENDENCY_NOT_READY",409);
        return rows.getFirst();
    }
    private void equal(Object a,Object b) { if (!Objects.equals(a,b)) throw new Invalid("RELATION_MISMATCH",409); }
    private void sameSession(String event,String session) { equal(row("SELECT session_id FROM events WHERE event_id=?",event).get("session_id"),session); }
    private void document(JsonNode p) {
        String doc=text(p,"documentId",64), version=text(p,"documentVersion",64);
        require(!db.queryForList("SELECT 1 FROM document_versions WHERE document_id=? AND version=?",doc,version).isEmpty());
    }
    private JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            ObjectNode out=mapper.createObjectNode();
            TreeSet<String> keys=new TreeSet<>(); node.fieldNames().forEachRemaining(keys::add);
            keys.forEach(key -> out.set(key,canonical(node.get(key)))); return out;
        }
        if (node.isArray()) { ArrayNode out=mapper.createArrayNode(); node.forEach(value -> out.add(canonical(value))); return out; }
        return node;
    }
    // SQLite has one writer. Serializing this local process also makes concurrent retries deterministic.
    public synchronized Map<String,Object> store(JsonNode event) throws Exception {
        fields(event,"schemaVersion","eventId","eventType","occurredAt","userId","sessionId","payload");
        if (number(event,"schemaVersion")!=1) throw new Invalid("UNSUPPORTED_SCHEMA",400);
        String eventId=id(event,"eventId"), session=id(event,"sessionId"), user=text(event,"userId",64);
        require(validUserId(user));
        String type=text(event,"eventType",32);
        require(Set.of("search_submitted","results_exposed","document_clicked","document_viewed").contains(type));
        String occurred;
        try { occurred=time.format(Instant.parse(text(event,"occurredAt",40))); }
        catch (DateTimeException ex) { throw new Invalid("VALIDATION_FAILED",400); }
        JsonNode p=event.get("payload");
        String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(canonical(event))));
        return tx.execute(status -> {
            var old=db.queryForList("SELECT payload_digest,received_at FROM events WHERE event_id=?",eventId);
            if (!old.isEmpty()) {
                if (!old.getFirst().get("payload_digest").equals(digest)) throw new Invalid("EVENT_ID_CONFLICT",409);
                return Map.of("eventId",eventId,"status","duplicate","receivedAt",old.getFirst().get("received_at"));
            }
            String received=time.format(Instant.now());
            var sessions=db.queryForList("SELECT user_id FROM collection_sessions WHERE session_id=?",session);
            if (sessions.isEmpty()) db.update("INSERT INTO collection_sessions VALUES (?,?,?)",session,user,received);
            else if (!sessions.getFirst().get("user_id").equals(user)) throw new Invalid("SESSION_LABEL_CONFLICT",409);
            db.update("INSERT INTO events VALUES (?,?,?,?,?,?,?)",eventId,session,type,1,occurred,received,digest);
            switch(type) {
                case "search_submitted" -> {
                    fields(p,"searchId","sourceDocumentId","query"); equal(id(p,"searchId"),eventId);
                    String query=text(p,"query",200); require(query.equals(query.trim()));
                    String source=p.get("sourceDocumentId").isNull()?null:text(p,"sourceDocumentId",64);
                    require(source==null || !db.queryForList("SELECT 1 FROM documents WHERE document_id=?",source).isEmpty());
                    db.update("INSERT INTO searches VALUES (?,?,?)",eventId,source,query);
                }
                case "results_exposed" -> {
                    fields(p,"searchId","totalCount","items"); String search=id(p,"searchId");
                    row("SELECT search_id FROM searches WHERE search_id=?",search); sameSession(search,session);
                    int count=number(p,"totalCount"); require(count>=0 && count<=100 && p.get("items").isArray() && p.get("items").size()==count);
                    db.update("INSERT INTO result_exposures VALUES (?,?,?)",eventId,search,count);
                    Set<String> seen=new HashSet<>(); int rank=0;
                    for (JsonNode item:p.get("items")) {
                        fields(item,"documentId","documentVersion","rank"); document(item);
                        require(number(item,"rank")==++rank && seen.add(item.get("documentId").textValue()));
                        db.update("INSERT INTO result_items VALUES (?,?,?,?)",eventId,rank,item.get("documentId").textValue(),item.get("documentVersion").textValue());
                    }
                }
                case "document_clicked" -> {
                    fields(p,"searchId","impressionId","documentId","documentVersion","rank"); document(p);
                    String impression=id(p,"impressionId"); int rank=number(p,"rank"); require(rank>=1);
                    equal(row("SELECT search_id FROM result_exposures WHERE impression_id=?",impression).get("search_id"),id(p,"searchId")); sameSession(impression,session);
                    var item=row("SELECT document_id,document_version FROM result_items WHERE impression_id=? AND rank=?",impression,rank);
                    equal(item.get("document_id"),p.get("documentId").textValue()); equal(item.get("document_version"),p.get("documentVersion").textValue());
                    db.update("INSERT INTO document_clicks VALUES (?,?,?)",eventId,impression,rank);
                }
                case "document_viewed" -> {
                    fields(p,"viewId","documentId","documentVersion","source","searchId","impressionId","clickId"); equal(id(p,"viewId"),eventId); document(p);
                    String source=text(p,"source",16); require(Set.of("search","navigation","direct","unknown").contains(source));
                    String click=null;
                    if (source.equals("search")) {
                        click=id(p,"clickId"); sameSession(click,session);
                        var related=row("SELECT c.impression_id,x.search_id,i.document_id,i.document_version FROM document_clicks c JOIN result_exposures x ON x.impression_id=c.impression_id JOIN result_items i ON i.impression_id=c.impression_id AND i.rank=c.rank WHERE c.click_id=?",click);
                        equal(related.get("search_id"),id(p,"searchId")); equal(related.get("impression_id"),id(p,"impressionId"));
                        equal(related.get("document_id"),p.get("documentId").textValue()); equal(related.get("document_version"),p.get("documentVersion").textValue());
                    } else require(p.get("searchId").isNull() && p.get("impressionId").isNull() && p.get("clickId").isNull());
                    db.update("INSERT INTO document_views VALUES (?,?,?,?,?)",eventId,p.get("documentId").textValue(),p.get("documentVersion").textValue(),source,click);
                }
                default -> throw new Invalid("VALIDATION_FAILED",400);
            }
            return Map.of("eventId",eventId,"status","stored","receivedAt",received);
        });
    }
}
