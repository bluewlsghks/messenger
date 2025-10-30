package com.individual.messenger.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.*;

import java.util.List;

@Configuration
@EnableMethodSecurity // (기존 EnableGlobalMethodSecurity 대체)
public class SecurityConfig {

    // JWT 필터가 있다면 주입
    private final JwtAuthFilter jwtFilter; // 없다면 이 필드/생성자/등록 라인 제거
    public SecurityConfig(JwtAuthFilter jwtFilter) { this.jwtFilter = jwtFilter; }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 세션 상태 없음(JWT)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // CSRF: 폼이 아닌 fetch JSON 이므로 인증 API는 제외
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        new AntPathRequestMatcher("/ws-stomp/**"),
                        new AntPathRequestMatcher("/api/auth/**")
                ))

                // CORS
                .cors(Customizer.withDefaults())

                // 경로별 권한
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // ✅ 정적 리소스 URL 허용
                        .requestMatchers("/js/**", "/css/**", "/images/**", "/webjars/**", "/favicon.ico").permitAll()

                        // ✅ 뷰 페이지 허용
                        .requestMatchers("/", "/rooms", "/chat/**", "/login", "/register", "/ws-stomp/**").permitAll()

                        // ✅ 인증 API 허용
                        .requestMatchers("/api/auth/**").permitAll()

                        // ✅ 보호할 API
                        .requestMatchers("/api/rooms/**", "/api/messages/**").authenticated()

                        .anyRequest().authenticated()
                )

                // 폼/기본 인증 비활성화
                .httpBasic(b -> b.disable())
                .formLogin(b -> b.disable());

        // JWT 필터가 있다면 체인에 등록 (없으면 이 줄 제거)
        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // CORS 설정
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(List.of("*"));
        cfg.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }

    // 비밀번호 인코더
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // (필요 시) AuthenticationManager 주입이 필요한 경우
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }
}
