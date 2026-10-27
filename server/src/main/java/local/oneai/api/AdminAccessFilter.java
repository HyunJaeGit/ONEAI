package local.oneai.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import local.oneai.service.AccountService;
import static local.oneai.api.AuthController.ACCOUNT_SESSION_KEY;
import static local.oneai.api.AuthController.CSRF_SESSION_KEY;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AdminAccessFilter extends OncePerRequestFilter {
    private final AccountService accounts;
    public AdminAccessFilter(AccountService accounts) { this.accounts=accounts; }

    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException, IOException {
        String path=request.getRequestURI();
        boolean adminPath=isAdminPath(path);
        if (adminPath) {
            HttpSession session=request.getSession(false);
            String accountId=session==null?null:(String)session.getAttribute(ACCOUNT_SESSION_KEY);
            var account=accounts.findActive(accountId);
            if (account.isEmpty()) {
                if (session!=null && accountId!=null) session.invalidate();
                if (path.startsWith("/api/")) jsonError(response,401,"AUTHENTICATION_REQUIRED");
                else { response.setStatus(302); response.setHeader("Location","/login?return="+URLEncoder.encode(path,StandardCharsets.UTF_8)); }
                return;
            }
            if (!"ADMIN".equals(account.get().memberType())) {
                if (path.startsWith("/api/")) jsonError(response,403,"ADMIN_REQUIRED");
                else { response.setStatus(403); response.setContentType("text/plain;charset=UTF-8"); response.getWriter().write("관리자 권한이 필요합니다."); }
                return;
            }
        }

        if (requiresCsrf(request,path) && !validCsrf(request)) {
            jsonError(response,403,"CSRF_TOKEN_INVALID");
            return;
        }
        chain.doFilter(request,response);
    }

    private boolean isAdminPath(String path) {
        return path.equals("/dev") || path.startsWith("/dev/") || path.startsWith("/api/v1/dev/");
    }

    private boolean requiresCsrf(HttpServletRequest request,String path) {
        String method=request.getMethod();
        if (method.equals("GET")||method.equals("HEAD")||method.equals("OPTIONS")) return false;
        boolean authMutation=path.equals("/api/v1/auth/login")||path.equals("/api/v1/auth/register")||path.equals("/api/v1/auth/logout");
        return authMutation || isAdminPath(path);
    }

    private boolean validCsrf(HttpServletRequest request) {
        HttpSession session=request.getSession(false);
        String expected=session==null?null:(String)session.getAttribute(CSRF_SESSION_KEY);
        String supplied=request.getHeader("X-CSRF-TOKEN");
        if (expected==null||supplied==null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8));
    }

    private void jsonError(HttpServletResponse response,int status,String code) throws IOException {
        response.setStatus(status); response.setContentType(MediaType.APPLICATION_JSON_VALUE); response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control","no-store");
        response.getWriter().write("{\"error\":{\"code\":\""+code+"\"}}");
    }
}
