package local.oneai.service;

import java.sql.*;
import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** Applies additive, versioned SQLite schema upgrades without replacing the database file. */
public final class DatabaseMigrator {
    private static final int CURRENT_VERSION = 6;
    private static final String CREATE_RATINGS = "CREATE TABLE IF NOT EXISTS usefulness_ratings(" +
        "rating_id TEXT NOT NULL PRIMARY KEY REFERENCES events(event_id)," +
        "view_id TEXT NOT NULL UNIQUE REFERENCES document_views(view_id)," +
        "criteria_version INTEGER NOT NULL CHECK(typeof(criteria_version)='integer' AND criteria_version IN (0,1))," +
        "value INTEGER NOT NULL CHECK(typeof(value)='integer' AND ((criteria_version=0 AND value BETWEEN 0 AND 5) OR (criteria_version=1 AND value BETWEEN 1 AND 5))))";

    public void initialize(DataSource source) throws Exception {
        int version;
        boolean hasEvents;
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
            version = pragmaInt(statement, "user_version");
            hasEvents = tableExists(statement, "events");
        }
        if (version > CURRENT_VERSION) throw new IllegalStateException("Database schema is newer than this application");

        if (!hasEvents) {
            applyCurrentSchema(source);
            setVersion(source, CURRENT_VERSION);
        } else if (version < 2) {
            migrateLegacyEvents(source);
            applyCurrentSchema(source);
        } else if (version < 3) {
            migrateRatingCriteria(source);
            applyCurrentSchema(source);
        } else {
            applyCurrentSchema(source);
        }
        ensureManualContentStatus(source);
        verifyForeignKeys(source);
    }

    private void ensureManualContentStatus(DataSource source) throws SQLException {
        boolean present = false;
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement();
             ResultSet columns = statement.executeQuery("PRAGMA table_info(manual_content)")) {
            while (columns.next()) if ("is_active".equals(columns.getString("name"))) present = true;
        }
        if (!present) try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE manual_content ADD COLUMN is_active INTEGER NOT NULL DEFAULT 1 CHECK(typeof(is_active)='integer' AND is_active IN (0,1))");
        }
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version=" + CURRENT_VERSION);
        }
    }

    private void migrateLegacyEvents(DataSource source) throws Exception {
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=OFF");
            if (pragmaInt(statement, "foreign_keys") != 0) throw new IllegalStateException("Could not disable foreign keys for schema migration");
            connection.setAutoCommit(false);
            try {
                statement.execute("CREATE TABLE events_v3(" +
                    "event_id TEXT NOT NULL PRIMARY KEY," +
                    "session_id TEXT NOT NULL REFERENCES collection_sessions," +
                    "event_type TEXT NOT NULL CHECK(event_type IN ('search_submitted','results_exposed','document_clicked','document_viewed','usefulness_rated'))," +
                    "schema_version INTEGER NOT NULL CHECK(schema_version=1)," +
                    "occurred_at TEXT NOT NULL,received_at TEXT NOT NULL,payload_digest TEXT NOT NULL)");
                statement.execute("INSERT INTO events_v3(event_id,session_id,event_type,schema_version,occurred_at,received_at,payload_digest) " +
                    "SELECT event_id,session_id,event_type,schema_version,occurred_at,received_at,payload_digest FROM events");
                statement.execute("DROP TABLE events");
                statement.execute("ALTER TABLE events_v3 RENAME TO events");
                statement.execute(CREATE_RATINGS);
                if (hasForeignKeyViolations(statement)) throw new IllegalStateException("Foreign key check failed during schema migration");
                statement.execute("PRAGMA user_version=" + CURRENT_VERSION);
                connection.commit();
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(true);
                statement.execute("PRAGMA foreign_keys=ON");
                if (pragmaInt(statement, "foreign_keys") != 1) throw new IllegalStateException("Could not restore foreign key enforcement");
            }
        }
    }

    /** Keeps prior 0–5 helpfulness ratings intact and tags them as criteria version 0. */
    private void migrateRatingCriteria(DataSource source) throws Exception {
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=OFF");
            if (pragmaInt(statement, "foreign_keys") != 0) throw new IllegalStateException("Could not disable foreign keys for schema migration");
            connection.setAutoCommit(false);
            try {
                if (tableExists(statement, "usefulness_ratings")) {
                    statement.execute(CREATE_RATINGS.replace("usefulness_ratings(", "usefulness_ratings_v3("));
                    statement.execute("INSERT INTO usefulness_ratings_v3(rating_id,view_id,criteria_version,value) " +
                        "SELECT rating_id,view_id,0,value FROM usefulness_ratings");
                    statement.execute("DROP TABLE usefulness_ratings");
                    statement.execute("ALTER TABLE usefulness_ratings_v3 RENAME TO usefulness_ratings");
                } else {
                    statement.execute(CREATE_RATINGS);
                }
                if (hasForeignKeyViolations(statement)) throw new IllegalStateException("Foreign key check failed during criteria migration");
                statement.execute("PRAGMA user_version=" + CURRENT_VERSION);
                connection.commit();
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(true);
                statement.execute("PRAGMA foreign_keys=ON");
                if (pragmaInt(statement, "foreign_keys") != 1) throw new IllegalStateException("Could not restore foreign key enforcement");
            }
        }
    }

    private void applyCurrentSchema(DataSource source) throws Exception {
        new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql")).execute(source);
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
            if (pragmaInt(statement, "user_version") < CURRENT_VERSION) statement.execute("PRAGMA user_version=" + CURRENT_VERSION);
        }
    }

    private void setVersion(DataSource source, int version) throws SQLException {
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version=" + version);
        }
    }

    private boolean tableExists(Statement statement, String name) throws SQLException {
        try (ResultSet rows = statement.executeQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name='" + name + "'")) {
            return rows.next();
        }
    }

    private int pragmaInt(Statement statement, String name) throws SQLException {
        try (ResultSet rows = statement.executeQuery("PRAGMA " + name)) {
            if (!rows.next()) throw new SQLException("Missing PRAGMA result: " + name);
            return rows.getInt(1);
        }
    }

    private boolean hasForeignKeyViolations(Statement statement) throws SQLException {
        try (ResultSet rows = statement.executeQuery("PRAGMA foreign_key_check")) { return rows.next(); }
    }

    private void verifyForeignKeys(DataSource source) throws SQLException {
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
            if (pragmaInt(statement, "foreign_keys") != 1) throw new IllegalStateException("SQLite foreign keys are not enabled");
            if (hasForeignKeyViolations(statement)) throw new IllegalStateException("SQLite foreign key check failed");
        }
    }
}
