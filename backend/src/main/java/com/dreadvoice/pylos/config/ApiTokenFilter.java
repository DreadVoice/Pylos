package com.dreadvoice.pylos.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class ApiTokenFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String PRINCIPAL = "demo-seller";

    private final ApiSecurityProperties properties;

    public ApiTokenFilter(ApiSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String presented = bearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));

        if (presented != null && matchesConfiguredToken(presented)) {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            PRINCIPAL, null, AuthorityUtils.createAuthorityList("ROLE_SELLER")));
        }

        filterChain.doFilter(request, response);
    }

    private String bearerToken(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader)
                || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length()).strip();
        return token.isEmpty() ? null : token;
    }

    private boolean matchesConfiguredToken(String presented) {
        String configured = properties.token();
        if (!StringUtils.hasText(configured)) {
            return false;
        }
        return MessageDigest.isEqual(
                configured.strip().getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
    }
}
