package local.oneai.api;

import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.*;
import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.bind.annotation.*;
import local.oneai.config.LocalConfig;
import local.oneai.service.EventService;

@RestController
public class LocalController {
    private final EventService service;
    private final ObjectMapper mapper=new ObjectMapper()
        .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public LocalController(EventService service) { this.service=service; }
    @PostMapping("/api/v1/events")
    public ResponseEntity<?> collect(HttpServletRequest request) {
        String eventId=null;
        try {
            String content=request.getContentType();
            if (content==null || !MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(content))) return error(null,415,"UNSUPPORTED_MEDIA_TYPE",false);
            byte[] bytes=request.getInputStream().readNBytes(65537);
            if (bytes.length>65536) return error(null,413,"PAYLOAD_TOO_LARGE",false);
            JsonNode event=mapper.readTree(bytes);
            if (event!=null && event.path("eventId").isTextual()) {
                String candidate=event.get("eventId").textValue();
                try { if (UUID.fromString(candidate).toString().equals(candidate)) eventId=candidate; } catch (IllegalArgumentException ignored) { }
            }
            var result=service.store(event);
            return ResponseEntity.status(result.get("status").equals("stored")?201:200).body(result);
        } catch (EventService.Invalid ex) { return error(eventId,ex.status,ex.code,ex.code.equals("DEPENDENCY_NOT_READY")); }
        catch (DataAccessException ex) { return error(eventId,503,"STORAGE_UNAVAILABLE",true); }
        catch (org.springframework.transaction.TransactionException ex) { return error(eventId,503,"STORAGE_UNAVAILABLE",true); }
        catch (java.io.IOException | IllegalArgumentException ex) { return error(eventId,400,"INVALID_JSON",false); }
        catch (Exception ex) { return error(eventId,500,"INTERNAL_ERROR",true); }
    }
    private ResponseEntity<?> error(String id,int status,String code,boolean retryable) {
        Map<String,Object> body=new LinkedHashMap<>(); body.put("eventId",id);
        body.put("error",Map.of("code",code,"retryable",retryable,"message","이벤트를 저장하지 못했습니다."));
        return ResponseEntity.status(status).body(body);
    }
    @GetMapping({"/","/index.html","/pages/business/","/pages/school/","/pages/relocation/"})
    public ResponseEntity<?> index(HttpServletRequest request) {
        String uri=request.getRequestURI();
        Path file=LocalConfig.root().resolve(uri.equals("/")?"index.html":uri.substring(1)+(uri.endsWith("/")?"index.html":""));
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(new FileSystemResource(file));
    }
}
