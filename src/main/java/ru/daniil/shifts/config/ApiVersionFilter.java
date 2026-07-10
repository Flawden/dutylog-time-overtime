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

/** Advertises the stable API contract and marks legacy mobile routes as deprecated. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ApiVersionFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/")) {
            response.setHeader("X-DutyLog-Api-Version", "v1");
        } else if (path.startsWith("/api/mobile/")) {
            response.setHeader("Deprecation", "true");
            response.setHeader("Link", "</api/v1/mobile>; rel=\"successor-version\"");
        }
        filterChain.doFilter(request, response);
    }
}
