package ru.daniil.shifts.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Минимальная диагностика запросов: request-id, метод, путь, статус и длительность.
 * Полезно на VPS, когда нужно понять, что реально происходит без дебага в браузере.
 */
@Component
public class RequestDiagnosticsFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestDiagnosticsFilter.class);
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) requestId = UUID.randomUUID().toString().substring(0, 8);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        long started = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            String uri = request.getRequestURI();
            if (shouldLog(uri)) {
                long ms = (System.nanoTime() - started) / 1_000_000;
                int status = response.getStatus();
                if (status >= 500) {
                    log.error("{} {} -> {} ({} ms, requestId={})", request.getMethod(), uri, status, ms, requestId);
                } else if (status >= 400) {
                    log.warn("{} {} -> {} ({} ms, requestId={})", request.getMethod(), uri, status, ms, requestId);
                } else {
                    log.info("{} {} -> {} ({} ms, requestId={})", request.getMethod(), uri, status, ms, requestId);
                }
            }
        }
    }

    private boolean shouldLog(String uri) {
        return uri.startsWith("/api/") || uri.startsWith("/actuator/") || uri.equals("/perform_login") || uri.equals("/logout");
    }
}
