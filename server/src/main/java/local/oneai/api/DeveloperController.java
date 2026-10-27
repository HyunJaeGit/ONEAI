package local.oneai.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Map;
import java.util.NoSuchElementException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.*;
import local.oneai.service.EventQueryService;
import local.oneai.service.EventDeletionService;
import local.oneai.service.EventService;
import local.oneai.service.ManualContentService;

@RestController
public class DeveloperController {
    private final EventQueryService queries;
    private final EventDeletionService deletions;
    private final ManualContentService manuals;
    public DeveloperController(EventQueryService queries, EventDeletionService deletions, ManualContentService manuals) { this.queries=queries; this.deletions=deletions; this.manuals=manuals; }
    private boolean local(HttpServletRequest request) {
        try { return InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress(); }
        catch(Exception ignored) { return false; }
    }
    @GetMapping("/api/v1/dev/events")
    public ResponseEntity<?> events(HttpServletRequest request,
            @RequestParam(defaultValue="") String userId,@RequestParam(defaultValue="") String eventType,
            @RequestParam(defaultValue="") String rating,@RequestParam(defaultValue="") String criteriaVersion) {
        if(!local(request)) return response(403,Map.of("error",Map.of("code","LOCAL_ONLY")));
        try { return response(200,queries.recent(userId,eventType,rating,criteriaVersion)); }
        catch(EventService.Invalid ex) { return response(ex.status,Map.of("error",Map.of("code",ex.code))); }
        catch(Exception ignored) { return response(503,Map.of("error",Map.of("code","READ_UNAVAILABLE"))); }
    }
    @DeleteMapping("/api/v1/dev/events")
    public ResponseEntity<?> deleteEvents(HttpServletRequest request, @RequestBody JsonNode body) {
        if(!local(request)) return response(403,Map.of("error",Map.of("code","LOCAL_ONLY")));
        try {
            if(body == null || !body.isObject() || body.size()!=1 || !body.has("eventIds") || !body.get("eventIds").isArray())
                throw new EventService.Invalid("VALIDATION_FAILED",400);
            var ids=new ArrayList<String>();
            body.get("eventIds").forEach(id->{ if(id.isTextual()) ids.add(id.textValue()); else ids.add(null); });
            return response(200,deletions.deleteSelected(ids));
        } catch(EventService.Invalid ex) { return response(ex.status,Map.of("error",Map.of("code",ex.code))); }
        catch(Exception ignored) { return response(503,Map.of("error",Map.of("code","DELETE_UNAVAILABLE"))); }
    }
    @GetMapping("/dev/events")
    public ResponseEntity<?> page(HttpServletRequest request) {
        if(!local(request)) return response(403,Map.of("error",Map.of("code","LOCAL_ONLY")));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.TEXT_HTML).body(new ClassPathResource("dev/events.html"));
    }
    @GetMapping("/dev/manual")
    public ResponseEntity<?> manualPage(HttpServletRequest request) {
        if(!local(request)) return response(403,Map.of("error",Map.of("code","LOCAL_ONLY","message","로컬에서만 사용할 수 있습니다.")));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.TEXT_HTML).body(new ClassPathResource("dev/manual.html"));
    }
    @GetMapping("/api/v1/dev/manual/documents")
    public ResponseEntity<?> manualDocuments(HttpServletRequest request) {
        if(!local(request)) return response(403,Map.of("error",Map.of("code","LOCAL_ONLY")));
        try { return response(200,Map.of("documents",manuals.listForAdmin())); }
        catch(Exception ignored) { return response(503,Map.of("error",Map.of("code","MANUAL_LIST_UNAVAILABLE"))); }
    }
    @GetMapping("/api/v1/dev/manual/documents/{documentId}")
    public ResponseEntity<?> manualDocument(HttpServletRequest request, @PathVariable String documentId) {
        if(!local(request)) return response(403,Map.of("error",Map.of("code","LOCAL_ONLY")));
        try { return response(200,manuals.getForAdmin(documentId)); }
        catch(NoSuchElementException ex) { return response(404,Map.of("error",Map.of("code","DOCUMENT_NOT_FOUND"))); }
        catch(Exception ignored) { return response(503,Map.of("error",Map.of("code","MANUAL_READ_UNAVAILABLE"))); }
    }
    @PutMapping("/api/v1/dev/manual/documents/{documentId}")
    public ResponseEntity<?> saveManual(HttpServletRequest request, @PathVariable String documentId, @RequestBody JsonNode content) {
        if(!local(request)) return response(403,Map.of("error",Map.of("code","LOCAL_ONLY")));
        if(content == null || !jsonRequest(request))
            return response(415,Map.of("error",Map.of("code","JSON_REQUIRED")));
        try { manuals.save(documentId,content); return response(200,Map.of("documentId",documentId,"saved",true)); }
        catch(IllegalStateException | IllegalArgumentException ex) { return response(400,Map.of("error",Map.of("code","INVALID_CONTENT","message","지원하지 않는 콘텐츠 구조이거나 필수 값이 올바르지 않습니다."))); }
        catch(NoSuchElementException ex) { return response(404,Map.of("error",Map.of("code","DOCUMENT_NOT_FOUND"))); }
        catch(Exception ignored) { return response(503,Map.of("error",Map.of("code","MANUAL_SAVE_UNAVAILABLE"))); }
    }
    @PostMapping("/api/v1/dev/manual/documents/{documentId}/deactivate")
    public ResponseEntity<?> deactivateManual(HttpServletRequest request, @PathVariable String documentId) {
        return setManualActive(request,documentId,false);
    }
    @PostMapping("/api/v1/dev/manual/documents/{documentId}/reactivate")
    public ResponseEntity<?> reactivateManual(HttpServletRequest request, @PathVariable String documentId) {
        return setManualActive(request,documentId,true);
    }
    private ResponseEntity<?> setManualActive(HttpServletRequest request, String documentId, boolean active) {
        if(!local(request)) return response(403,Map.of("error",Map.of("code","LOCAL_ONLY")));
        try { manuals.setActive(documentId,active); return response(200,Map.of("documentId",documentId,"active",active)); }
        catch(NoSuchElementException ex) { return response(404,Map.of("error",Map.of("code","DOCUMENT_NOT_FOUND"))); }
        catch(Exception ignored) { return response(503,Map.of("error",Map.of("code","MANUAL_STATUS_UNAVAILABLE"))); }
    }
    private ResponseEntity<?> response(int status,Object body) { return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body); }
    private boolean jsonRequest(HttpServletRequest request) {
        try { return request.getContentType() != null && MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(request.getContentType())); }
        catch(IllegalArgumentException ex) { return false; }
    }
}
