package local.oneai;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.nio.file.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest
@AutoConfigureMockMvc
class CollectionTest {
    static Path file;
    static { try { file=Files.createTempDirectory("oneai-synthetic-").resolve("events.sqlite"); } catch(Exception e) { throw new RuntimeException(e); } }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { registry.add("oneai.db",()->file.toString()); }
    @Autowired MockMvc mvc;
    @Autowired DataSource source;
    final ObjectMapper mapper=new ObjectMapper();
    String session;
    JdbcTemplate db;
    @BeforeEach void setup() { session=UUID.randomUUID().toString(); db=new JdbcTemplate(source); }
    ObjectNode event(String type,String payload) throws Exception {
        ObjectNode e=mapper.createObjectNode();
        e.put("schemaVersion",1).put("eventId",UUID.randomUUID().toString()).put("eventType",type)
         .put("occurredAt","2026-10-26T00:00:00Z").put("userId","demo-test").put("sessionId",session);
        e.set("payload",mapper.readTree(payload)); return e;
    }
    ObjectNode search() throws Exception {
        var e=event("search_submitted","{\"searchId\":\"placeholder\",\"sourceDocumentId\":\"manual-home\",\"query\":\"합성 가나다\"}");
        ((ObjectNode)e.get("payload")).put("searchId",e.get("eventId").asText()); return e;
    }
    int post(JsonNode e) throws Exception { return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/events").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(e))).andReturn().getResponse().getStatus(); }
    ObjectNode exposure(ObjectNode search,int count) throws Exception {
        return event("results_exposed","{\"searchId\":\""+search.get("eventId").asText()+"\",\"totalCount\":"+count+",\"items\":"+(count==0?"[]":"[{\"documentId\":\"contact\",\"documentVersion\":\"local-v1\",\"rank\":1}]")+"}");
    }
    @Test void fullFlowAndKoreanTime() throws Exception {
        var s=search(); assertEquals(201,post(s)); var x=exposure(s,1); assertEquals(201,post(x));
        var c=event("document_clicked","{\"searchId\":\""+s.get("eventId").asText()+"\",\"impressionId\":\""+x.get("eventId").asText()+"\",\"documentId\":\"contact\",\"documentVersion\":\"local-v1\",\"rank\":1}"); assertEquals(201,post(c));
        var v=event("document_viewed",mapper.writeValueAsString(c.get("payload"))); ObjectNode p=(ObjectNode)v.get("payload"); p.remove("rank"); p.put("viewId",v.get("eventId").asText()).put("clickId",c.get("eventId").asText()).put("source","search"); assertEquals(201,post(v));
        assertEquals("합성 가나다",db.queryForObject("SELECT query FROM searches WHERE search_id=?",String.class,s.get("eventId").asText()));
        assertEquals("2026-10-26T00:00:00.000Z",db.queryForObject("SELECT occurred_at FROM events WHERE event_id=?",String.class,s.get("eventId").asText()));
    }
    @Test void zeroResults() throws Exception { var s=search(); assertEquals(201,post(s)); var x=exposure(s,0); assertEquals(201,post(x)); assertEquals(0,db.queryForObject("SELECT count(*) FROM result_items WHERE impression_id=?",Integer.class,x.get("eventId").asText())); }
    @Test void searchQueryValidationAndParameterizedStorage() throws Exception {
        var blank=search(); ((ObjectNode)blank.get("payload")).put("query","   "); assertEquals(400,post(blank));
        var tooLong=search(); ((ObjectNode)tooLong.get("payload")).put("query","x".repeat(201)); assertEquals(400,post(tooLong));
        var wrongType=search(); ((ObjectNode)wrongType.get("payload")).put("query",12); assertEquals(400,post(wrongType));
        var control=search(); ((ObjectNode)control.get("payload")).put("query","합성\u0001검색"); assertEquals(400,post(control));
        var safeText=search(); ((ObjectNode)safeText.get("payload")).put("query","합성 ' OR 1=1 --");
        assertEquals(201,post(safeText));
        assertEquals("합성 ' OR 1=1 --",db.queryForObject("SELECT query FROM searches WHERE search_id=?",String.class,safeText.get("eventId").asText()));
    }
    @Test void duplicatesAndConflict() throws Exception {
        var s=search(); assertEquals(201,post(s)); assertEquals(200,post(s));
        ((ObjectNode)s.get("payload")).put("query","합성 다른값"); assertEquals(409,post(s));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM events WHERE event_id=?",Integer.class,s.get("eventId").asText()));
    }
    @Test void invalidListRollsBack() throws Exception {
        var s=search(); assertEquals(201,post(s)); var x=exposure(s,1); ((ObjectNode)x.get("payload").get("items").get(0)).put("rank",2);
        assertEquals(400,post(x)); assertEquals(0,db.queryForObject("SELECT count(*) FROM events WHERE event_id=?",Integer.class,x.get("eventId").asText()));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM result_exposures WHERE impression_id=?",Integer.class,x.get("eventId").asText()));
    }
    @Test void otherSessionAndLabelRejected() throws Exception {
        var s=search(); assertEquals(201,post(s)); var x=exposure(s,0); x.put("sessionId",UUID.randomUUID().toString()); assertEquals(409,post(x));
        var another=search(); another.put("userId","demo-other"); assertEquals(409,post(another));
    }
    @Test void missingDependencyAndUnknownFields() throws Exception {
        assertEquals(409,post(exposure(search(),0))); var s=search(); s.put("password","synthetic"); assertEquals(400,post(s));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM collection_sessions WHERE session_id=?",Integer.class,session));
    }
    @Test void databaseFailureHasNoPartialSaveOrSensitiveResponse() throws Exception {
        db.execute("CREATE TRIGGER fail_synthetic BEFORE INSERT ON searches BEGIN SELECT RAISE(ABORT,'synthetic failure'); END");
        try { assertEquals(503,post(search())); assertEquals(0,db.queryForObject("SELECT count(*) FROM events WHERE session_id=?",Integer.class,session)); }
        finally { db.execute("DROP TRIGGER fail_synthetic"); }
    }
    ObjectNode linkedView() throws Exception {
        var s=search(); assertEquals(201,post(s)); var x=exposure(s,1); assertEquals(201,post(x));
        var c=event("document_clicked","{\"searchId\":\""+s.get("eventId").asText()+"\",\"impressionId\":\""+x.get("eventId").asText()+"\",\"documentId\":\"contact\",\"documentVersion\":\"local-v1\",\"rank\":1}"); assertEquals(201,post(c));
        var v=event("document_viewed",mapper.writeValueAsString(c.get("payload"))); ObjectNode p=(ObjectNode)v.get("payload"); p.remove("rank"); p.put("viewId",v.get("eventId").asText()).put("clickId",c.get("eventId").asText()).put("source","search"); assertEquals(201,post(v));
        return v;
    }
    ObjectNode rating(ObjectNode view,int value) throws Exception {
        return event("usefulness_rated","{\"viewId\":\""+view.get("eventId").asText()+"\",\"criteriaVersion\":1,\"value\":"+value+"}");
    }
    @Test void directViewAndRatingRules() throws Exception {
        var v=event("document_viewed","{\"viewId\":\"placeholder\",\"documentId\":\"contact\",\"documentVersion\":\"local-v1\",\"source\":\"unknown\",\"searchId\":null,\"impressionId\":null,\"clickId\":null}");
        ((ObjectNode)v.get("payload")).put("viewId",v.get("eventId").asText()); assertEquals(201,post(v));
        var one=rating(v,1); assertEquals(201,post(one)); assertEquals(200,post(one));
        assertEquals(409,post(rating(v,5)));
        assertEquals(400,post(rating(v,0))); assertEquals(400,post(rating(v,6)));
        assertEquals(400,post(event("usefulness_rated","{}")));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM usefulness_ratings WHERE view_id=?",Integer.class,v.get("eventId").asText()));
        var otherView=linkedView(); assertEquals(201,post(rating(otherView,5)));
        var oldContract=rating(otherView,4); ((ObjectNode)oldContract.get("payload")).put("criteriaVersion",0); assertEquals(400,post(oldContract));
    }
    @Test void searchViewRatingsAreLinkedAndFiltered() throws Exception {
        var v=linkedView(); var r=rating(v,5); assertEquals(201,post(r));
        var duplicate=mapper.readTree(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/events").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(r))).andReturn().getResponse().getContentAsByteArray());
        assertEquals("duplicate",duplicate.get("status").asText());
        var listed=mapper.readTree(mvc.perform(get("/api/v1/dev/events").param("eventType","usefulness_rated").param("rating","5").param("criteriaVersion","1")).andReturn().getResponse().getContentAsByteArray()).get("events").get(0);
        assertEquals(5,listed.at("/payload/value").asInt()); assertEquals(1,listed.at("/payload/criteriaVersion").asInt()); assertEquals(v.get("eventId").asText(),listed.at("/payload/viewId").asText());
        assertEquals(v.at("/payload/documentId").asText(),listed.at("/context/documentId").asText());
        assertEquals(v.at("/payload/searchId").asText(),listed.at("/context/searchId").asText());
        assertEquals("합성 가나다",listed.at("/context/query").asText());
        assertEquals(400,mvc.perform(get("/api/v1/dev/events").param("rating","6")).andReturn().getResponse().getStatus());
        var other=rating(v,4); other.put("sessionId",UUID.randomUUID().toString()); assertEquals(409,post(other));
    }
    @Test void deletingViewAlsoDeletesRatingFirst() throws Exception {
        var v=linkedView(); var r=rating(v,2); assertEquals(201,post(r));
        var response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/dev/events")
            .contentType(MediaType.APPLICATION_JSON).content("{\"eventIds\":[\""+v.get("eventId").asText()+"\"]}")).andReturn().getResponse();
        assertEquals(200,response.getStatus());
        assertEquals(0,db.queryForObject("SELECT count(*) FROM usefulness_ratings WHERE view_id=?",Integer.class,v.get("eventId").asText()));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM events WHERE event_id=?",Integer.class,r.get("eventId").asText()));
    }
    @Test void foreignKeysAndPrivateFiles() throws Exception {
        assertEquals(1,db.queryForObject("PRAGMA foreign_keys",Integer.class));
        assertEquals(404,mvc.perform(get("/local-data/oneai-collection.sqlite")).andReturn().getResponse().getStatus());
        assertEquals(404,mvc.perform(get("/AGENTS.md")).andReturn().getResponse().getStatus());
        assertEquals(200,mvc.perform(get("/")).andReturn().getResponse().getStatus());
    }
    @Test void invalidJsonAndSizeAreRejectedWithoutEcho() throws Exception {
        var response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/events").contentType(MediaType.APPLICATION_JSON).content("{} {}"))
            .andReturn().getResponse();
        assertEquals(400,response.getStatus()); assertFalse(response.getContentAsString().contains("{} {}"));
        assertEquals(413,mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/events").contentType(MediaType.APPLICATION_JSON).content(" ".repeat(65537))).andReturn().getResponse().getStatus());
    }
    @Test void concurrentDuplicateHasOneInsert() throws Exception {
        var event=search();
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(4)) {
            List<java.util.concurrent.Callable<Integer>> requests=List.of(()->post(event),()->post(event),()->post(event),()->post(event));
            var results=executor.invokeAll(requests);
            int inserted=0; for (var result:results) { int code=result.get(); assertTrue(code==201 || code==200); if(code==201) inserted++; }
            assertEquals(1,inserted);
        }
    }
    @Test void developerReadReconstructsExistingContractAndDoesNotWrite() throws Exception {
        fullFlowAndKoreanTime();
        int before=db.queryForObject("SELECT count(*) FROM events",Integer.class);
        var response=mvc.perform(get("/api/v1/dev/events").param("userId","demo-test")).andReturn().getResponse();
        assertEquals(200,response.getStatus()); assertEquals("no-store",response.getHeader("Cache-Control"));
        JsonNode events=mapper.readTree(response.getContentAsByteArray()).get("events");
        var own=new ArrayList<JsonNode>(); events.forEach(e->{if(e.get("sessionId").asText().equals(session)) own.add(e);});
        assertEquals(4,own.size());
        var search=own.stream().filter(e->e.get("eventType").asText().equals("search_submitted")).findFirst().orElseThrow();
        assertEquals("합성 가나다",search.at("/payload/query").asText());
        assertEquals("contact",search.at("/results/0/items/0/documentId").asText());
        for(var e:own) {
            assertEquals(search.get("eventId").asText(),e.at("/context/searchId").asText());
            assertEquals("합성 가나다",e.at("/context/query").asText());
        }
        assertEquals(before,db.queryForObject("SELECT count(*) FROM events",Integer.class));
        String filtered=mvc.perform(get("/api/v1/dev/events").param("eventType","document_clicked")).andReturn().getResponse().getContentAsString();
        for(var e:mapper.readTree(filtered).get("events")) assertEquals("document_clicked",e.get("eventType").asText());
    }
    @Test void developerEmptyInvalidAndExternalRequests() throws Exception {
        var empty=mvc.perform(get("/api/v1/dev/events").param("userId","demo-"+UUID.randomUUID())).andReturn().getResponse();
        assertEquals(200,empty.getStatus()); assertEquals(0,mapper.readTree(empty.getContentAsByteArray()).get("events").size());
        assertEquals(400,mvc.perform(get("/api/v1/dev/events").param("eventType","bad")).andReturn().getResponse().getStatus());
        assertEquals(403,mvc.perform(get("/api/v1/dev/events").with(r->{r.setRemoteAddr("192.0.2.10");return r;})).andReturn().getResponse().getStatus());
        assertEquals(403,mvc.perform(get("/dev/events").with(r->{r.setRemoteAddr("192.0.2.10");return r;})).andReturn().getResponse().getStatus());
        assertEquals(200,mvc.perform(get("/dev/events")).andReturn().getResponse().getStatus());
    }
    @Test void databasePathIsRepositoryRelative() {
        var env=new org.springframework.mock.env.MockEnvironment().withProperty("oneai.db","local-data/oneai-collection.sqlite");
        assertEquals(local.oneai.config.LocalConfig.root().resolve("local-data/oneai-collection.sqlite"),local.oneai.config.LocalConfig.databasePath(env));
    }
    @Test void freeUserLabelsCanBeStoredAndFiltered() throws Exception {
        for(String label:List.of("테스트상담사", "tester 01", "실험🌱", "<test>&")) {
            session=UUID.randomUUID().toString();
            var e=search(); e.put("userId",label); assertEquals(201,post(e));
            var response=mvc.perform(get("/api/v1/dev/events").param("userId",label)).andReturn().getResponse();
            assertEquals(200,response.getStatus());
            var events=mapper.readTree(response.getContentAsByteArray()).get("events");
            assertTrue(events.size()>0); for(var item:events) assertEquals(label,item.get("userId").asText());
        }
        for(String label:List.of(" ","x".repeat(65),"test\nlabel")) { var e=search(); e.put("userId",label); assertEquals(400,post(e)); }
    }
}
