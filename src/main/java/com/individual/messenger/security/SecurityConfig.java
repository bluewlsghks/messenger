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
@EnableMethodSecurity // (Í∏∞Ï°¥ EnableGlobalMethodSecurity ?ÄÏ≤?
public class SecurityConfig {

    // JWT ?ÑÌÑ∞Í∞Ä ?àÎã§Î©?Ï£ºÏûÖ
    private final JwtAuthFilter jwtFilter; // ?ÜÎã§Î©????ÑÎìú/?ùÏÑ±???±Î°ù ?ºÏù∏ ?úÍ±∞
    public SecurityConfig(JwtAuthFilter jwtFilter) { this.jwtFilter = jwtFilter; }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // ?∏ÏÖò ?ÅÌÉú ?ÜÏùå(JWT)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // CSRF: ?ºÏù¥ ?ÑÎãå fetch JSON ?¥Î?Î°??∏Ï¶ù API???úÏô∏
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        new AntPathRequestMatcher("/ws-stomp/**"),
                        new AntPathRequestMatcher("/api/auth/**")
                ))

                // CORS
                .cors(Customizer.withDefaults())

                // Í≤ΩÎ°úÎ≥?Í∂åÌïú
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // ???ïÏ†Å Î¶¨ÏÜå??URL ?àÏö©
                        .requestMatchers("/js/**", "/css/**", "/images/**", "/webjars/**", "/favicon.ico").permitAll()

                        // ??Î∑??òÏù¥ÏßÄ ?àÏö©
                        .requestMatchers("/", "/rooms", "/chat/**", "/login", "/register", "/ws-stomp/**").permitAll()

                        // ???∏Ï¶ù API ?àÏö©
                        .requestMatchers("/api/auth/**").permitAll()

                        // ??Î≥¥Ìò∏??API
                        .requestMatchers("/api/rooms/**", "/api/messages/**").authenticated()

                        .anyRequest().authenticated()
                )

                // ??Í∏∞Î≥∏ ?∏Ï¶ù ÎπÑÌôú?±Ìôî
                .httpBasic(b -> b.disable())
                .formLogin(b -> b.disable());

        // JWT ?ÑÌÑ∞Í∞Ä ?àÎã§Î©?Ï≤¥Ïù∏???±Î°ù (?ÜÏúºÎ©???Ï§??úÍ±∞)
        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // CORS ?§Ï†ï
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

    // ÎπÑÎ?Î≤àÌò∏ ?∏ÏΩî??
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // (?ÑÏöî ?? AuthenticationManager Ï£ºÏûÖ???ÑÏöî??Í≤ΩÏö∞
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }
}

