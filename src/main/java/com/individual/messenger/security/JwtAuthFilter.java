package com.individual.messenger.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtUtil jwt;
    public JwtAuthFilter(JwtUtil jwt) { this.jwt = jwt; }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String token = null;

        // 1) Authorization 헤더
        String auth = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (auth != null && auth.startsWith("Bearer ")) {
            token = auth.substring(7);
        }

        // 2) 없으면 쿠키에서 시도 (예: 쿠키명 AUTH)
        if (token == null && req.getCookies() != null) {
            for (var c : req.getCookies()) {
                if ("AUTH".equals(c.getName())) {
                    token = c.getValue();
                    break;
                }
            }
        }

        if (token != null) {
            try {
                String subject = jwt.getSubject(token); // 검증+만료확인 포함 권장
                var principal = new org.springframework.security.core.userdetails.User(subject, "", java.util.Collections.emptyList());
                var authToken = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());
                authToken.setDetails(new org.springframework.security.web.authentication.WebAuthenticationDetailsSource().buildDetails(req));
                org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authToken);
            } catch (Exception ignored) { /* 검증 실패시 인증 미설정 */ }
        }

        chain.doFilter(req, res);
    }
}
