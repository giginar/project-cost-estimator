package com.project.costestimator.config;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {
    private final BearerTokenFilter bearerTokenFilter;
    @Value("${app.security.enabled:true}") private boolean securityEnabled;

    @Bean
    static PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    UserDetailsService userDetailsService() { return username -> { throw new UsernameNotFoundException(username); }; }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable()).sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        if (!securityEnabled) return http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/verify", "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password", "/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/health").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/projects/**", "/api/v1/resources/**").hasAnyRole("ENGINEER", "MANAGER")
                .requestMatchers(HttpMethod.PUT, "/api/v1/projects/*").hasAnyRole("ENGINEER", "MANAGER")
                .requestMatchers("/api/v1/projects/**", "/api/v1/resources/**").hasRole("ENGINEER")
                .requestMatchers("/api/v1/auth/me", "/api/v1/auth/logout").authenticated()
                .anyRequest().denyAll());
        http.exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) -> writeError(response, 401, "Authentication required"))
                .accessDeniedHandler((request, response, exception) -> writeError(response, 403, "You do not have permission for this operation")));
        http.addFilterBefore(bearerTokenFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void writeError(HttpServletResponse response, int status, String detail) throws java.io.IOException {
        response.setStatus(status); response.setContentType("application/problem+json");
        response.getWriter().write("{\"status\":" + status + ",\"detail\":\"" + detail + "\"}");
    }
}
