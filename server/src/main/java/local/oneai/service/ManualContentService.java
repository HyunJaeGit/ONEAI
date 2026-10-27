package local.oneai.service;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

@Service
public class ManualContentService {
    private final JdbcTemplate db;
    private final ObjectMapper mapper = new ObjectMapper()
        .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public ManualContentService(DataSource source) {
        db = new JdbcTemplate(source);
    }

    public void initialize() throws Exception {
        Resource[] seeds = new PathMatchingResourcePatternResolver().getResources("classpath*:db/content/*.json");
        Arrays.sort(seeds, java.util.Comparator.comparing(Resource::getFilename));
        List<Resource> missingSeeds = new ArrayList<>();
        for (Resource seed : seeds) {
            String filename = seed.getFilename();
            if (filename == null || !filename.endsWith(".json")) throw new IllegalStateException("Invalid manual content seed name");
            String documentId = filename.substring(0, filename.length() - ".json".length());
            if (db.queryForList("SELECT 1 FROM manual_content WHERE document_id=? LIMIT 1", documentId).isEmpty())
                missingSeeds.add(seed);
        }
        for (Resource seed : missingSeeds) try (var input = seed.getInputStream()) {
            JsonNode content = mapper.readTree(input);
            if (content == null || !content.isObject()) throw new IllegalStateException("Invalid manual content seed: " + seed.getFilename());
            String documentId = text(content, "documentId"), version = text(content, "documentVersion");
            validate(documentId, content);
            if (db.queryForList("SELECT 1 FROM document_versions WHERE document_id=? AND version=?", documentId, version).isEmpty())
                throw new IllegalStateException("Manual content has no catalog version: " + documentId + "/" + version);
            db.update("INSERT OR IGNORE INTO manual_content(document_id,document_version,content_json) VALUES (?,?,?)",
                documentId, version, mapper.writeValueAsString(content));
        }
    }

    public Map<String,Object> get(String documentId) {
        List<String> rows = db.query("SELECT content_json FROM manual_content WHERE document_id=? AND is_active=1 ORDER BY rowid DESC LIMIT 1",
            (rs, row) -> rs.getString(1), documentId);
        if (rows.isEmpty()) throw new NoSuchElementException();
        return parse(documentId, rows.getFirst());
    }

    public List<Map<String,Object>> listForAdmin() {
        return db.query("SELECT m.document_id,m.document_version,m.is_active,v.path,m.content_json FROM manual_content m " +
                "JOIN document_versions v ON v.document_id=m.document_id AND v.version=m.document_version ORDER BY m.document_id,m.document_version",
            (rs, row) -> {
                JsonNode root = parseJson(rs.getString("content_json"));
                Map<String,Object> item = new LinkedHashMap<>();
                item.put("documentId", rs.getString("document_id"));
                item.put("documentVersion", rs.getString("document_version"));
                item.put("path", rs.getString("path"));
                item.put("title", root.path("title").asText());
                item.put("active", rs.getInt("is_active") == 1);
                return item;
            });
    }

    public Map<String,Object> getForAdmin(String documentId) {
        List<String> rows = db.query("SELECT content_json FROM manual_content WHERE document_id=? ORDER BY rowid DESC LIMIT 1",
            (rs, row) -> rs.getString(1), documentId);
        if (rows.isEmpty()) throw new NoSuchElementException();
        return parse(documentId, rows.getFirst());
    }

    public void save(String documentId, JsonNode content) {
        validate(documentId, content);
        String encoded;
        try { encoded = mapper.writeValueAsString(content); }
        catch (java.io.IOException ex) { throw new IllegalArgumentException("Invalid content JSON", ex); }
        int updated = db.update("UPDATE manual_content SET content_json=? WHERE document_id=? AND document_version=?",
            encoded, documentId, text(content, "documentVersion"));
        if (updated != 1) throw new NoSuchElementException();
    }

    public void setActive(String documentId, boolean active) {
        int updated = db.update("UPDATE manual_content SET is_active=? WHERE document_id=? AND document_version=(SELECT document_version FROM manual_content WHERE document_id=? ORDER BY rowid DESC LIMIT 1)",
            active ? 1 : 0, documentId, documentId);
        if (updated != 1) throw new NoSuchElementException();
    }

    private Map<String,Object> parse(String documentId, String json) {
        try {
            JsonNode root = mapper.readTree(json);
            validate(documentId, root);
            return mapper.convertValue(root, Map.class);
        } catch (java.io.IOException | IllegalArgumentException ex) {
            throw new IllegalStateException("Stored manual content is invalid", ex);
        }
    }

    private JsonNode parseJson(String json) {
        try { return mapper.readTree(json); }
        catch (java.io.IOException ex) { throw new IllegalStateException("Stored manual content is invalid", ex); }
    }

    public void validate(String documentId, JsonNode root) {
        require(root != null && root.isObject());
        require(documentId.equals(text(root, "documentId")));
        require(text(root, "documentVersion").matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}"));
        text(root, "title");
        if (root.has("eyebrow")) text(root, "eyebrow");
        if (root.has("lead")) validateInlineArray(root.get("lead"));
        JsonNode sections = root.get("sections");
        require(sections != null && sections.isArray() && sections.size() > 0);
        Set<String> sectionIds = new HashSet<>();
        Set<String> elementIds = new HashSet<>();
        for (JsonNode section : sections) {
            require(section.isObject());
            String sectionId = text(section, "id");
            require(sectionId.matches("[a-z][a-z0-9-]{0,63}") && sectionIds.add(sectionId) && elementIds.add(sectionId));
            if (section.has("title")) text(section, "title");
            require(integer(section, "order") > 0);
            if (section.has("helpTip")) text(section, "helpTip");
            if (section.has("helpTipAlignment")) require("left".equals(text(section, "helpTipAlignment")));
            if (section.has("variant")) require("supplementary".equals(text(section, "variant")));
            JsonNode blocks = section.get("blocks");
            require(blocks != null && blocks.isArray());
            for (JsonNode block : blocks) validateBlock(block, elementIds);
        }
    }

    private void validateBlock(JsonNode block, Set<String> elementIds) {
        require(block != null && block.isObject());
        String type = text(block, "type");
        require(integer(block, "order") > 0);
        if (block.has("id")) {
            String id = text(block, "id");
            require(id.matches("[a-z][a-z0-9-]{0,63}") && elementIds.add(id));
        }
        switch (type) {
            case "paragraph" -> {
                validateInlineArray(block.get("content"));
                if (block.has("variant")) require("source".equals(text(block, "variant")));
            }
            case "heading" -> { require(Set.of(3, 4).contains(integer(block, "level"))); text(block, "text"); }
            case "list" -> {
                require(Set.of("ordered", "unordered").contains(text(block, "listStyle")));
                JsonNode items = block.get("items");
                require(items != null && items.isArray() && items.size() > 0);
                for (JsonNode item : items) validateInlineArray(item);
            }
            case "notice" -> {
                require(Set.of("warning", "check", "reference").contains(text(block, "variant")));
                if (block.has("title")) text(block, "title");
                if (block.has("children")) {
                    require(block.get("children").isArray());
                    for (JsonNode child : block.get("children")) validateBlock(child, elementIds);
                } else validateInlineArray(block.get("content"));
            }
            case "table" -> {
                text(block, "caption");
                JsonNode columns = block.get("columns"), rows = block.get("rows");
                require(columns != null && columns.isArray() && columns.size() > 0 && rows != null && rows.isArray());
                for (JsonNode column : columns) text(column, "label");
                for (JsonNode row : rows) {
                    JsonNode cells = row.isArray() ? row : row.path("cells");
                    require(cells.isArray() && cells.size() == columns.size());
                    if (!row.isArray() && row.has("id")) {
                        String rowId = text(row, "id");
                        require(rowId.matches("[a-z][a-z0-9-]{0,63}") && elementIds.add(rowId));
                    }
                    for (JsonNode cell : cells) {
                        require(cell.isObject() && Set.of("header", "data").contains(text(cell, "kind")));
                        if ("header".equals(text(cell, "kind")) && cell.hasNonNull("scope"))
                            require(Set.of("row", "col").contains(text(cell, "scope")));
                        validateInlineArray(cell.get("content"));
                    }
                }
            }
            case "details" -> {
                text(block, "summary");
                JsonNode children = block.get("children");
                require(children != null && children.isArray());
                for (JsonNode child : children) {
                    require(!"details".equals(text(child, "type")));
                    validateBlock(child, elementIds);
                }
            }
            default -> require(false);
        }
    }

    private void validateInlineArray(JsonNode nodes) {
        require(nodes != null && nodes.isArray());
        for (JsonNode node : nodes) validateInline(node, 0);
    }

    private void validateInline(JsonNode node, int depth) {
        require(node != null && node.isObject() && depth <= 3);
        switch (text(node, "type")) {
            case "text" -> text(node, "value");
            case "strong" -> validateInlineArrayAtDepth(node.get("children"), depth + 1);
            case "link" -> {
                text(node, "label");
                String kind = text(node, "targetType");
                if (kind.equals("internal")) {
                    text(node, "documentId");
                    if (node.has("anchor")) text(node, "anchor");
                } else if (kind.equals("external")) {
                    String url = text(node, "url");
                    require(url.startsWith("https://") || url.startsWith("http://"));
                } else if (kind.equals("email")) {
                    require(text(node, "email").contains("@"));
                } else require(false);
            }
            default -> require(false);
        }
    }

    private void validateInlineArrayAtDepth(JsonNode nodes, int depth) {
        require(nodes != null && nodes.isArray());
        for (JsonNode node : nodes) validateInline(node, depth);
    }

    private String text(JsonNode node, String field) {
        require(node.hasNonNull(field) && node.get(field).isTextual() && !node.get(field).textValue().isBlank());
        return node.get(field).textValue();
    }

    private int integer(JsonNode node, String field) {
        require(node.has(field) && node.get(field).isIntegralNumber() && node.get(field).canConvertToInt());
        return node.get(field).intValue();
    }

    private void require(boolean condition) {
        if (!condition) throw new IllegalStateException("Invalid manual content structure");
    }
}
