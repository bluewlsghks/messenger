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
@EnableMethodSecurity // (湲곗〈 EnableGlobalMethodSecurity ?泥?
public class SecurityConfig {

    // JWT ?꾪꽣媛 ?덈떎硫?二쇱엯
    private final JwtAuthFilter jwtFilter; // ?녿떎硫????꾨뱶/?앹꽦???깅줉 ?쇱씤 ?쒓굅
    public SecurityConfig(JwtAuthFilter jwtFilter) { this.jwtFilter = jwtFilter; }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // ?몄뀡 ?곹깭 ?놁쓬(JWT)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // CSRF: ?쇱씠 ?꾨땶 fetch JSON ?대?濡??몄쬆 API???쒖쇅
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        new AntPathRequestMatcher("/ws-stomp/**"),
                        new AntPathRequestMatcher("/api/auth/**")
                ))

                // CORS
                .cors(Customizer.withDefaults())

                // 寃쎈줈蹂?沅뚰븳
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // ???뺤쟻 由ъ냼??URL ?덉슜
                        .requestMatchers("/js/**", "/css/**", "/images/**", "/webjars/**", "/favicon.ico").permitAll()

                        // ??酉??섏씠吏 ?덉슜
                        .requestMatchers("/", "/rooms", "/chat/**", "/login", "/register", "/ws-stomp/**").permitAll()

                        // ???몄쬆 API ?덉슜
                        .requestMatchers("/api/auth/**").permitAll()

                        // ??蹂댄샇??API
                        .requestMatchers("/api/rooms/**", "/api/messages/**").authenticated()

                        .anyRequest().authenticated()
                )

                // ??湲곕낯 ?몄쬆 鍮꾪솢?깊솕
                .httpBasic(b -> b.disable())
                .formLogin(b -> b.disable());

        // JWT ?꾪꽣媛 ?덈떎硫?泥댁씤???깅줉 (?놁쑝硫???以??쒓굅)
        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // CORS ?ㅼ젙
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

    // 鍮꾨?踰덊샇 ?몄퐫??
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // (?꾩슂 ?? AuthenticationManager 二쇱엯???꾩슂??寃쎌슦
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }
}


