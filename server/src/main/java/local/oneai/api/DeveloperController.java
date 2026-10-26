package local.oneai.api;

import java.net.InetAddress;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.*;
import local.oneai.service.EventQueryService;
import local.oneai.service.EventService;

@RestController
public class DeveloperController {
    private final EventQueryService queries;
    public DeveloperController(EventQueryService queries) { this.queries=queries; }
    private boolean local(HttpServletRequest request) {
        try { return InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress(); }
        catch(Exception ignored) { return false; }
    }
    @GetMapping("/api/v1/dev/events")
    public ResponseEntity<?> events(HttpServletRequest request,
            @RequestParam(defaultValue="") String userId,@RequestParam(defaultValue="") String eventType) {
        if(!local(request)) return response(403,Map.of("error",Map.of("code","LOCAL_ONLY")));
        try { return response(200,queries.recent(userId,eventType)); }
        catch(EventService.Invalid ex) { return response(ex.status,Map.of("error",Map.of("code",ex.code))); }
        catch(Exception ignored) { return response(503,Map.of("error",Map.of("code","READ_UNAVAILABLE"))); }
    }
    @GetMapping("/dev/events")
    public ResponseEntity<?> page(HttpServletRequest request) {
        if(!local(request)) return response(403,Map.of("error",Map.of("code","LOCAL_ONLY")));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.TEXT_HTML).body(new ClassPathResource("dev/events.html"));
    }
    private ResponseEntity<?> response(int status,Object body) { return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body); }
}
