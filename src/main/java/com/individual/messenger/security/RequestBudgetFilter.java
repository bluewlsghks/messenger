package com.individual.messenger.security;

import com.individual.messenger.service.RequestBudget;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;

public class RequestBudgetFilter extends OncePerRequestFilter {
    private final RequestBudget budget;
    private final boolean enabled;
    public RequestBudgetFilter(RequestBudget budget, boolean enabled) { this.budget = budget; this.enabled = enabled; }
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws IOException, ServletException {
        if (enabled && req.getRequestURI().startsWith("/api/") && !req.getMethod().equals("OPTIONS")) {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            String subject = auth != null && auth.isAuthenticated() ? "u:" + auth.getName() : "ip:" + req.getRemoteAddr();
            String path = req.getRequestURI();
            try {
                if (path.equals("/api/auth/register") && req.getMethod().equals("POST")) budget.consume(subject, "signup", 30, 3600);
                else if (path.equals("/api/auth/login")) budget.consume(subject, "login", 60, 60);
                else if (path.equals("/api/files") && req.getMethod().equals("POST")) budget.consume(subject, "upload", 10, 60);
                else budget.consume(subject, req.getMethod().equals("GET") ? "read" : "write", req.getMethod().equals("GET") ? 1200 : 300, 60);
            } catch (ResponseStatusException limited) {
                res.setStatus(429); res.setHeader("Retry-After", "60"); res.setContentType("application/json;charset=UTF-8");
                res.getWriter().write("{\"error\":\"RATE_LIMITED\",\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.\"}"); return;
            }
        }
        chain.doFilter(req, res);
    }
}
