package hk.hkucs.memoryagent;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** V1 is one private memory space, protected by a deployment token; not a multi-user login system. */
@Component
public class ApiSecurity extends OncePerRequestFilter {
    private final String token;
    public ApiSecurity(@Value("${agent.token}") String token) { this.token = token; }
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        res.setHeader("Cache-Control", "no-store");
        if (req.getRequestURI().equals("/health") && req.getMethod().equals("GET")) {
            chain.doFilter(req, res); return;
        }
        if (token.length() < 16) { error(res, 503, "后端尚未配置至少 16 位的 AGENT_TOKEN"); return; }
        String supplied = req.getHeader("Authorization");
        if (supplied == null || !MessageDigest.isEqual(("Bearer " + token).getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8))) {
            error(res, 401, "访问令牌不正确"); return;
        }
        chain.doFilter(req, res);
    }
    private void error(HttpServletResponse res, int status, String message) throws IOException {
        res.setStatus(status); res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
