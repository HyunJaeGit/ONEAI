package local.oneai;

import java.nio.file.Files;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import local.oneai.service.DatabaseMigrator;
import local.oneai.service.EventQueryService;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseMigratorTest {
    @Test void upgradesLegacyDatabaseWithoutDroppingEvents() throws Exception {
        var file=Files.createTempDirectory("oneai-migration-").resolve("legacy.sqlite");
        var config=new SQLiteConfig(); config.enforceForeignKeys(true);
        var source=new SQLiteDataSource(config); source.setUrl("jdbc:sqlite:"+file);
        var db=new JdbcTemplate(source);
        String session=UUID.randomUUID().toString(), event=UUID.randomUUID().toString();
        db.execute("CREATE TABLE collection_sessions(session_id TEXT NOT NULL PRIMARY KEY,user_id TEXT NOT NULL,created_at TEXT NOT NULL)");
        db.execute("CREATE TABLE events(event_id TEXT NOT NULL PRIMARY KEY,session_id TEXT NOT NULL REFERENCES collection_sessions,event_type TEXT NOT NULL CHECK(event_type IN ('search_submitted','results_exposed','document_clicked','document_viewed')),schema_version INTEGER NOT NULL CHECK(schema_version=1),occurred_at TEXT NOT NULL,received_at TEXT NOT NULL,payload_digest TEXT NOT NULL)");
        db.execute("CREATE TABLE searches(search_id TEXT NOT NULL PRIMARY KEY REFERENCES events,source_document_id TEXT,query TEXT NOT NULL)");
        db.update("INSERT INTO collection_sessions VALUES (?,?,?)",session,"synthetic","2026-10-26T00:00:00.000Z");
        db.update("INSERT INTO events VALUES (?,?,?,?,?,?,?)",event,session,"search_submitted",1,"2026-10-26T00:00:00.000Z","2026-10-26T00:00:01.000Z","synthetic-digest");
        db.update("INSERT INTO searches VALUES (?,?,?)",event,null,"합성 마이그레이션 확인");

        new DatabaseMigrator().initialize(source);

        assertEquals(1,db.queryForObject("SELECT count(*) FROM events WHERE event_id=?",Integer.class,event));
        assertEquals("합성 마이그레이션 확인",db.queryForObject("SELECT query FROM searches WHERE search_id=?",String.class,event));
        assertEquals(3,db.queryForObject("PRAGMA user_version",Integer.class));
        assertEquals(0,db.queryForList("PRAGMA foreign_key_check").size());
        assertEquals(1,db.queryForObject("PRAGMA foreign_keys",Integer.class));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM usefulness_ratings",Integer.class));
    }

    @Test void upgradesPreviousHelpfulnessRatingsWithoutChangingTheirMeaning() throws Exception {
        var file=Files.createTempDirectory("oneai-rating-migration-").resolve("legacy.sqlite");
        var config=new SQLiteConfig(); config.enforceForeignKeys(true);
        var source=new SQLiteDataSource(config); source.setUrl("jdbc:sqlite:"+file);
        var db=new JdbcTemplate(source);
        String session=UUID.randomUUID().toString(), view0=UUID.randomUUID().toString(), view5=UUID.randomUUID().toString(), rating0=UUID.randomUUID().toString(), rating5=UUID.randomUUID().toString();
        db.execute("CREATE TABLE collection_sessions(session_id TEXT NOT NULL PRIMARY KEY,user_id TEXT NOT NULL,created_at TEXT NOT NULL)");
        db.execute("CREATE TABLE events(event_id TEXT NOT NULL PRIMARY KEY,session_id TEXT NOT NULL REFERENCES collection_sessions,event_type TEXT NOT NULL CHECK(event_type IN ('search_submitted','results_exposed','document_clicked','document_viewed','usefulness_rated')),schema_version INTEGER NOT NULL CHECK(schema_version=1),occurred_at TEXT NOT NULL,received_at TEXT NOT NULL,payload_digest TEXT NOT NULL)");
        db.execute("CREATE TABLE documents(document_id TEXT NOT NULL PRIMARY KEY)");
        db.execute("CREATE TABLE document_versions(document_id TEXT NOT NULL REFERENCES documents,version TEXT NOT NULL,path TEXT NOT NULL,PRIMARY KEY(document_id,version))");
        db.execute("CREATE TABLE document_views(view_id TEXT NOT NULL PRIMARY KEY REFERENCES events,document_id TEXT NOT NULL,document_version TEXT NOT NULL,source TEXT NOT NULL,click_id TEXT,FOREIGN KEY(document_id,document_version) REFERENCES document_versions(document_id,version))");
        db.execute("CREATE TABLE usefulness_ratings(rating_id TEXT NOT NULL PRIMARY KEY REFERENCES events,view_id TEXT NOT NULL UNIQUE REFERENCES document_views,value INTEGER NOT NULL CHECK(typeof(value)='integer' AND value BETWEEN 0 AND 5))");
        db.update("INSERT INTO collection_sessions VALUES (?,?,?)",session,"synthetic","2026-10-26T00:00:00.000Z");
        db.update("INSERT INTO documents VALUES (?)","synthetic-doc");
        db.update("INSERT INTO document_versions VALUES (?,?,?)","synthetic-doc","local-v1","pages/synthetic.html");
        for (String id : new String[]{view0,view5,rating0,rating5}) db.update("INSERT INTO events VALUES (?,?,?,?,?,?,?)",id,session,id.equals(view0)||id.equals(view5)?"document_viewed":"usefulness_rated",1,"2026-10-26T00:00:00.000Z","2026-10-26T00:00:01.000Z","synthetic-digest-"+id);
        db.update("INSERT INTO document_views VALUES (?,?,?,?,?)",view0,"synthetic-doc","local-v1","unknown",null);
        db.update("INSERT INTO document_views VALUES (?,?,?,?,?)",view5,"synthetic-doc","local-v1","unknown",null);
        db.update("INSERT INTO usefulness_ratings VALUES (?,?,?)",rating0,view0,0);
        db.update("INSERT INTO usefulness_ratings VALUES (?,?,?)",rating5,view5,5);
        db.execute("PRAGMA user_version=2");
        new DatabaseMigrator().initialize(source);
        assertEquals(3,db.queryForObject("PRAGMA user_version",Integer.class));
        assertEquals(0,db.queryForObject("SELECT value FROM usefulness_ratings WHERE rating_id=?",Integer.class,rating0));
        assertEquals(5,db.queryForObject("SELECT value FROM usefulness_ratings WHERE rating_id=?",Integer.class,rating5));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM usefulness_ratings WHERE criteria_version<>0",Integer.class));
        assertEquals(4,db.queryForObject("SELECT count(*) FROM events",Integer.class));
        assertEquals(0,db.queryForList("PRAGMA foreign_key_check").size());
        var legacy= new EventQueryService(source).recent("","usefulness_rated","0","0");
        var oldEvent=(java.util.Map<?,?>)((java.util.List<?>)legacy.get("events")).get(0);
        assertEquals(0,((java.util.Map<?,?>)oldEvent.get("payload")).get("criteriaVersion"));
        assertEquals(0,((java.util.Map<?,?>)oldEvent.get("payload")).get("value"));
    }
}
