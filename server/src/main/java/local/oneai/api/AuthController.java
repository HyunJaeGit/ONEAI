package local.oneai.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.InetAddress;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import local.oneai.service.AccountService;
import local.oneai.service.AccountService.Account;
import local.oneai.service.AccountService.AccountException;

@RestController
public class AuthController {
    public static final String ACCOUNT_SESSION_KEY = "oneai.accountId";
    public static final String CSRF_SESSION_KEY = "oneai.csrfToken";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final AccountService accounts;

    public AuthController(AccountService accounts) { this.accounts=accounts; }

    @GetMapping("/login")
    public ResponseEntity<?> loginPage(HttpServletRequest request) { return page(request,"dev/login.html"); }

    @GetMapping("/register")
    public ResponseEntity<?> registerPage(HttpServletRequest request) { return page(request,"dev/register.html"); }

    @GetMapping("/api/v1/auth/csrf")
    public ResponseEntity<?> csrf(HttpServletRequest request) {
        if (!local(request)) return error(403,"LOCAL_ONLY");
        HttpSession session=request.getSession(true);
        String token=(String)session.getAttribute(CSRF_SESSION_KEY);
        if (token == null) { token=newToken(); session.setAttribute(CSRF_SESSION_KEY,token); }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("csrfToken",token));
    }

    @GetMapping("/api/v1/auth/me")
    public ResponseEntity<?> me(HttpServletRequest request) {
        if (!local(request)) return error(403,"LOCAL_ONLY");
        HttpSession session=request.getSession(false);
        String accountId=session==null?null:(String)session.getAttribute(ACCOUNT_SESSION_KEY);
        return accounts.findActive(accountId).<ResponseEntity<?>>map(account -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(accountBody(account)))
            .orElseGet(() -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("authenticated",false)));
    }

    @PostMapping(value="/api/v1/auth/register",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> register(HttpServletRequest request, @RequestBody JsonNode body) {
        if (!local(request)) return error(403,"LOCAL_ONLY");
        if (!validFields(body,"loginId","password")) return error(400,"INVALID_REQUEST");
        try {
            Account account=accounts.register(body.path("loginId").asText(null),body.path("password").asText(null));
            return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(accountBody(account));
        } catch (AccountException ex) { return error(ex.status,ex.code); }
        catch (Exception ignored) { return error(503,"ACCOUNT_STORAGE_UNAVAILABLE"); }
    }

    @PostMapping(value="/api/v1/auth/login",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> login(HttpServletRequest request, @RequestBody JsonNode body) {
        if (!local(request)) return error(403,"LOCAL_ONLY");
        if (!validFields(body,"loginId","password","returnTo")) return error(400,"INVALID_REQUEST");
        try {
            Account account=accounts.authenticate(body.path("loginId").asText(null),body.path("password").asText(null),request.getRemoteAddr());
            HttpSession previous=request.getSession(false);
            if (previous!=null) previous.invalidate();
            HttpSession session=request.getSession(true);
            session.setAttribute(ACCOUNT_SESSION_KEY,account.accountId());
            String token=newToken(); session.setAttribute(CSRF_SESSION_KEY,token);
            Map<String,Object> response=accountBody(account);
            response.put("csrfToken",token);
            response.put("returnTo",safeReturn(body.path("returnTo").asText("")));
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
        } catch (AccountException ex) { return error(ex.status,ex.code); }
        catch (Exception ignored) { return error(503,"LOGIN_UNAVAILABLE"); }
    }

    @PostMapping("/api/v1/auth/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        if (!local(request)) return error(403,"LOCAL_ONLY");
        HttpSession session=request.getSession(false);
        if (session!=null) session.invalidate();
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private ResponseEntity<?> page(HttpServletRequest request,String resource) {
        if (!local(request)) return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("LOCAL_ONLY");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.TEXT_HTML).body(new ClassPathResource(resource));
    }

    private boolean validFields(JsonNode body,String... names) {
        if (body==null || !body.isObject()) return false;
        java.util.Set<String> allowed=java.util.Set.of(names);
        java.util.Iterator<String> fields=body.fieldNames();
        while(fields.hasNext()) if(!allowed.contains(fields.next())) return false;
        for(String name:names) if(!name.equals("returnTo") && !body.path(name).isTextual()) return false;
        return body.size()>0;
    }

    private Map<String,Object> accountBody(Account account) {
        Map<String,Object> body=new LinkedHashMap<>();
        body.put("authenticated",true); body.put("accountId",account.accountId()); body.put("loginId",account.loginId()); body.put("memberType",account.memberType());
        return body;
    }

    private String safeReturn(String path) {
        if (path!=null && path.startsWith("/dev/") && !path.startsWith("//") && !path.contains("\\") && path.length()<=200) return path;
        return "/";
    }

    private ResponseEntity<?> error(int status,String code) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("error",Map.of("code",code)));
    }

    private String newToken() { byte[] bytes=new byte[32]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private boolean local(HttpServletRequest request) {
        try { return InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress(); }
        catch (Exception ignored) { return false; }
    }
}
