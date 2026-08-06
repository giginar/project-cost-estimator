package com.project.costestimator.config;

import com.project.costestimator.application.port.in.AuthenticationUseCase;
import com.project.costestimator.domain.AppUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class BearerTokenFilter extends OncePerRequestFilter {
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthenticationUseCase authentication;

    public BearerTokenFilter(AuthenticationUseCase authentication) {
        this.authentication = authentication;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String authorization = request.getHeader(AUTHORIZATION_HEADER);
        if (isUnauthenticatedBearerRequest(authorization)) {
            authenticate(authorization.substring(BEARER_PREFIX.length()));
        }
        chain.doFilter(request, response);
    }

    private boolean isUnauthenticatedBearerRequest(String authorization) {
        return authorization != null
                && authorization.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null;
    }

    private void authenticate(String rawToken) {
        AppUser user = authentication.authenticateToken(rawToken);
        if (user == null) {
            return;
        }
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + user.getRole().name());
        UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken(
                user.getEmail(), null, List.of(authority));
        SecurityContextHolder.getContext().setAuthentication(principal);
    }
}
