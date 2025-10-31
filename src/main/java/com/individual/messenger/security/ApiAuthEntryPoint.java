package com.individual.messenger.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

public class ApiAuthEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest req, HttpServletResponse res, AuthenticationException ex)
            throws IOException, ServletException {

        // API 요청 또는 JSON 선호 → 401 JSON
        String accept = req.getHeader("Accept");
        boolean isApi = req.getRequestURI().startsWith("/api/");
        boolean wantJson = accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE);

        if (isApi || wantJson) {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
            res.getWriter().write("""
                {"code":401,"message":"UNAUTHORIZED: JWT token missing or invalid"}
            """);
            return;
        }

        // 그 외(페이지 요청) → 로그인 페이지로
        res.sendRedirect("/login?error=unauthenticated");
    }
}
