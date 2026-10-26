package local.oneai.service;

import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/** Deletes selected event rows and the downstream event rows that depend on them. */
@Service
public class EventDeletionService {
    private static final int MAX_IDS = 100;
    private static final int SQL_BATCH_SIZE = 400;
    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private final JdbcTemplate db;
    private final TransactionTemplate write;

    public EventDeletionService(DataSource source) {
        db = new JdbcTemplate(source);
        write = new TransactionTemplate(new DataSourceTransactionManager(source));
    }

    public Map<String, Object> deleteSelected(List<String> requestedIds) {
        if (requestedIds == null || requestedIds.isEmpty() || requestedIds.size() > MAX_IDS)
            throw new EventService.Invalid("VALIDATION_FAILED", 400);

        LinkedHashSet<String> requested = new LinkedHashSet<>();
        for (String id : requestedIds) {
            if (id == null || !id.matches(UUID_PATTERN) || !requested.add(id))
                throw new EventService.Invalid("VALIDATION_FAILED", 400);
        }

        return write.execute(status -> {
            LinkedHashSet<String> existing = new LinkedHashSet<>(findIds("events", "event_id", requested));
            if (existing.isEmpty()) return Map.of("deletedEvents", 0, "deletedRelatedEvents", 0);

            Set<String> searchIds = findIds("searches", "search_id", existing);
            LinkedHashSet<String> impressionIds = new LinkedHashSet<>(findIds("result_exposures", "impression_id", existing));
            impressionIds.addAll(findRelatedIds("result_exposures", "impression_id", "search_id", searchIds));

            LinkedHashSet<String> clickIds = new LinkedHashSet<>(findIds("document_clicks", "click_id", existing));
            clickIds.addAll(findRelatedIds("document_clicks", "click_id", "impression_id", impressionIds));

            LinkedHashSet<String> viewIds = new LinkedHashSet<>(findIds("document_views", "view_id", existing));
            viewIds.addAll(findRelatedIds("document_views", "view_id", "click_id", clickIds));

            LinkedHashSet<String> allIds = new LinkedHashSet<>(existing);
            allIds.addAll(impressionIds);
            allIds.addAll(clickIds);
            allIds.addAll(viewIds);
            Set<String> sessionIds = findRelatedIds("events", "session_id", "event_id", allIds);

            deleteIn("document_views", "view_id", viewIds);
            deleteIn("document_clicks", "click_id", clickIds);
            deleteIn("result_items", "impression_id", impressionIds);
            deleteIn("result_exposures", "impression_id", impressionIds);
            deleteIn("searches", "search_id", searchIds);
            int deleted = deleteIn("events", "event_id", allIds);
            for (String sessionId : sessionIds) db.update("DELETE FROM collection_sessions WHERE session_id=? AND NOT EXISTS (SELECT 1 FROM events WHERE events.session_id=?)", sessionId, sessionId);

            int selectedDeleted = (int) existing.stream().filter(requested::contains).count();
            return Map.of("deletedEvents", deleted, "deletedRelatedEvents", Math.max(0, deleted - selectedDeleted));
        });
    }

    private Set<String> findIds(String table, String column, Collection<String> values) {
        return findRelatedIds(table, column, column, values);
    }

    private Set<String> findRelatedIds(String table, String selectColumn, String matchColumn, Collection<String> values) {
        if (values.isEmpty()) return Set.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        List<String> ids = new ArrayList<>(values);
        for (int start = 0; start < ids.size(); start += SQL_BATCH_SIZE) {
            List<String> batch = ids.subList(start, Math.min(ids.size(), start + SQL_BATCH_SIZE));
            String sql = "SELECT " + selectColumn + " FROM " + table + " WHERE " + matchColumn + " IN (" + placeholders(batch.size()) + ")";
            result.addAll(db.queryForList(sql, String.class, batch.toArray()));
        }
        return result;
    }

    private int deleteIn(String table, String column, Collection<String> values) {
        if (values.isEmpty()) return 0;
        int deleted = 0;
        List<String> ids = new ArrayList<>(values);
        for (int start = 0; start < ids.size(); start += SQL_BATCH_SIZE) {
            List<String> batch = ids.subList(start, Math.min(ids.size(), start + SQL_BATCH_SIZE));
            String sql = "DELETE FROM " + table + " WHERE " + column + " IN (" + placeholders(batch.size()) + ")";
            deleted += db.update(sql, batch.toArray());
        }
        return deleted;
    }

    private String placeholders(int count) { return String.join(",", Collections.nCopies(count, "?")); }
}
