package local.oneai.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.*;
import local.oneai.service.EventQueryService;
import local.oneai.service.EventDeletionService;
import local.oneai.service.EventService;

@RestController
public class DeveloperController {
    private final EventQueryService queries;
    private final EventDeletionService deletions;
    public DeveloperController(EventQueryService queries, EventDeletionService deletions) { this.queries=queries; this.deletions=deletions; }
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
    private ResponseEntity<?> response(int status,Object body) { return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body); }
}
