package ru.daniil.shifts.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Defense-in-depth security headers for direct Spring Boot runs and reverse-proxy deployments.
 *
 * Caddy/nginx examples set the same headers at the edge, but keeping them here prevents a
 * misconfigured proxy or local production run from silently losing the baseline browser policy.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
public class SecurityHeadersFilter extends OncePerRequestFilter {
    private static final String CSP = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data:",
            "connect-src 'self'",
            "manifest-src 'self'",
            "base-uri 'self'",
            "frame-ancestors 'self'",
            "form-action 'self'"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("X-Frame-Options", "SAMEORIGIN");
        response.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
        response.setHeader("Content-Security-Policy", CSP);

        if (isHttps(request)) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000");
        }

        filterChain.doFilter(request, response);
    }

    private boolean isHttps(HttpServletRequest request) {
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        return request.isSecure() || "https".equalsIgnoreCase(forwardedProto);
    }
}
