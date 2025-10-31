package com.individual.messenger.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

public class ApiAccessDeniedHandler implements AccessDeniedHandler {
    @Override
    public void handle(HttpServletRequest req, HttpServletResponse res, AccessDeniedException ex)
            throws IOException, ServletException {

        String accept = req.getHeader("Accept");
        boolean isApi = req.getRequestURI().startsWith("/api/");
        boolean wantJson = accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE);

        if (isApi || wantJson) {
            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
            res.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
            res.getWriter().write("""
                {"code":403,"message":"FORBIDDEN: You do not have permission to access this resource"}
            """);
            return;
        }

        res.sendRedirect("/access-denied");
    }
}
