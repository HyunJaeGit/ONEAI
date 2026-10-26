package local.oneai.service;

import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/** Read-only projection of the existing event tables; does not create or modify events. */
@Service
public class EventQueryService {
    private final JdbcTemplate db;
    private final TransactionTemplate read;
    public EventQueryService(DataSource source) {
        db=new JdbcTemplate(source);
        read=new TransactionTemplate(new DataSourceTransactionManager(source));
        read.setReadOnly(true);
    }
    public Map<String,Object> recent(String user,String type,String rating,String criteriaVersion) {
        if ((!user.isEmpty() && !EventService.validUserId(user)) ||
            (!type.isEmpty() && !Set.of("search_submitted","results_exposed","document_clicked","document_viewed","usefulness_rated").contains(type)))
            throw new EventService.Invalid("VALIDATION_FAILED",400);
        Integer ratingValue=null;
        if (!rating.isEmpty()) {
            try { ratingValue=Integer.valueOf(rating); }
            catch (NumberFormatException ex) { throw new EventService.Invalid("VALIDATION_FAILED",400); }
            if (ratingValue<0 || ratingValue>5 || !Integer.toString(ratingValue).equals(rating) || (!type.isEmpty() && !type.equals("usefulness_rated")))
                throw new EventService.Invalid("VALIDATION_FAILED",400);
        }
        Integer criteria=null;
        if (!criteriaVersion.isEmpty()) {
            try { criteria=Integer.valueOf(criteriaVersion); }
            catch (NumberFormatException ex) { throw new EventService.Invalid("VALIDATION_FAILED",400); }
            if ((criteria!=0 && criteria!=1) || !Integer.toString(criteria).equals(criteriaVersion))
                throw new EventService.Invalid("VALIDATION_FAILED",400);
            if (!type.isEmpty() && !type.equals("usefulness_rated")) throw new EventService.Invalid("VALIDATION_FAILED",400);
        }
        final Integer selectedRating=ratingValue;
        final Integer selectedCriteria=criteria;
        return read.execute(status -> {
            String sql="SELECT e.*,s.user_id FROM events e JOIN collection_sessions s ON s.session_id=e.session_id WHERE 1=1";
            List<Object> args=new ArrayList<>();
            if (!user.isEmpty()) { sql+=" AND s.user_id=?"; args.add(user); }
            if (!type.isEmpty()) { sql+=" AND e.event_type=?"; args.add(type); }
            if (selectedRating!=null) { sql+=" AND EXISTS (SELECT 1 FROM usefulness_ratings r WHERE r.rating_id=e.event_id AND r.value=?)"; args.add(selectedRating); }
            if (selectedCriteria!=null) { sql+=" AND EXISTS (SELECT 1 FROM usefulness_ratings r WHERE r.rating_id=e.event_id AND r.criteria_version=?)"; args.add(selectedCriteria); }
            sql+=" ORDER BY e.received_at DESC,e.rowid DESC LIMIT 100";
            List<Map<String,Object>> events=new ArrayList<>();
            for (var row:db.queryForList(sql,args.toArray())) {
                String id=(String)row.get("event_id"), eventType=(String)row.get("event_type");
                Map<String,Object> event=new LinkedHashMap<>();
                event.put("schemaVersion",row.get("schema_version")); event.put("eventId",id);
                event.put("eventType",eventType); event.put("userId",row.get("user_id"));
                event.put("sessionId",row.get("session_id")); event.put("occurredAt",row.get("occurred_at")); event.put("receivedAt",row.get("received_at"));
                Map<String,Object> p=new LinkedHashMap<>(), context=new LinkedHashMap<>();
                context.put("searchId",null); context.put("documentId",null); context.put("rank",null); context.put("query",null);
                switch (eventType) {
                    case "search_submitted" -> {
                        var s=one("SELECT * FROM searches WHERE search_id=?",id);
                        p.put("searchId",id); p.put("sourceDocumentId",s.get("source_document_id")); p.put("query",s.get("query"));
                        context.put("searchId",id); context.put("documentId",s.get("source_document_id")); context.put("query",s.get("query"));
                        List<Map<String,Object>> exposures=new ArrayList<>();
                        for(var x:db.queryForList("SELECT impression_id,total_count FROM result_exposures WHERE search_id=? ORDER BY rowid",id)) {
                            Map<String,Object> xview=new LinkedHashMap<>(); xview.put("impressionId",x.get("impression_id")); xview.put("totalCount",x.get("total_count")); xview.put("items",items((String)x.get("impression_id"))); exposures.add(xview);
                        }
                        event.put("results",exposures);
                    }
                    case "results_exposed" -> {
                        var x=one("SELECT x.*,q.query FROM result_exposures x JOIN searches q ON q.search_id=x.search_id WHERE x.impression_id=?",id);
                        p.put("searchId",x.get("search_id")); p.put("totalCount",x.get("total_count")); p.put("items",items(id)); context.put("searchId",x.get("search_id"));
                        context.put("query",x.get("query"));
                    }
                    case "document_clicked" -> {
                        var c=one("SELECT c.impression_id,c.rank,x.search_id,i.document_id,i.document_version,q.query FROM document_clicks c JOIN result_exposures x ON x.impression_id=c.impression_id JOIN searches q ON q.search_id=x.search_id JOIN result_items i ON i.impression_id=c.impression_id AND i.rank=c.rank WHERE c.click_id=?",id);
                        p.put("searchId",c.get("search_id")); p.put("impressionId",c.get("impression_id")); p.put("documentId",c.get("document_id")); p.put("documentVersion",c.get("document_version")); p.put("rank",c.get("rank"));
                        context.put("searchId",c.get("search_id")); context.put("documentId",c.get("document_id")); context.put("rank",c.get("rank")); context.put("query",c.get("query"));
                    }
                    case "document_viewed" -> {
                        var v=one("SELECT v.*,c.impression_id,c.rank,x.search_id,q.query FROM document_views v LEFT JOIN document_clicks c ON c.click_id=v.click_id LEFT JOIN result_exposures x ON x.impression_id=c.impression_id LEFT JOIN searches q ON q.search_id=x.search_id WHERE v.view_id=?",id);
                        p.put("viewId",id); p.put("documentId",v.get("document_id")); p.put("documentVersion",v.get("document_version")); p.put("source",v.get("source")); p.put("searchId",v.get("search_id")); p.put("impressionId",v.get("impression_id")); p.put("clickId",v.get("click_id"));
                        context.put("searchId",v.get("search_id")); context.put("documentId",v.get("document_id")); context.put("rank",v.get("rank")); context.put("query",v.get("query"));
                    }
                    case "usefulness_rated" -> {
                        var ratingRow=one("SELECT r.view_id,r.criteria_version,r.value,v.document_id,c.rank,x.search_id,q.query FROM usefulness_ratings r " +
                            "JOIN document_views v ON v.view_id=r.view_id " +
                            "LEFT JOIN document_clicks c ON c.click_id=v.click_id " +
                            "LEFT JOIN result_exposures x ON x.impression_id=c.impression_id " +
                            "LEFT JOIN searches q ON q.search_id=x.search_id WHERE r.rating_id=?",id);
                        p.put("viewId",ratingRow.get("view_id")); p.put("criteriaVersion",ratingRow.get("criteria_version")); p.put("value",ratingRow.get("value"));
                        context.put("searchId",ratingRow.get("search_id")); context.put("documentId",ratingRow.get("document_id")); context.put("rank",ratingRow.get("rank")); context.put("query",ratingRow.get("query"));
                    }
                    default -> throw new IllegalStateException("Unsupported stored event");
                }
                event.put("payload",p); event.put("context",context); events.add(event);
            }
            return Map.of("events",events,"limit",100);
        });
    }
    private Map<String,Object> one(String sql,String id) { return db.queryForMap(sql,id); }
    private List<Map<String,Object>> items(String impression) {
        return db.query("SELECT document_id,document_version,rank FROM result_items WHERE impression_id=? ORDER BY rank",(rs,n)-> {
            Map<String,Object> item=new LinkedHashMap<>(); item.put("documentId",rs.getString(1)); item.put("documentVersion",rs.getString(2)); item.put("rank",rs.getInt(3)); return item;
        },impression);
    }
}
