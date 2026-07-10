package ru.daniil.shifts.config;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Structured security-event logging without secrets or user content.
 *
 * Log lines are intentionally key=value so they remain grep-friendly on a small VPS
 * and can later be shipped to Loki/ELK without changing the event model.
 */
@Component
public class SecurityEventLogger {
    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final int MAX_VALUE_LENGTH = 160;

    public void warn(String eventType, String username, String result, String detail) {
        write(true, currentRequest(), eventType, username, result, detail);
    }

    public void warn(HttpServletRequest request,
                     String eventType,
                     String username,
                     String result,
                     String detail) {
        write(true, request, eventType, username, result, detail);
    }

    public void info(String eventType, String username, String result, String detail) {
        write(false, currentRequest(), eventType, username, result, detail);
    }

    public void info(HttpServletRequest request,
                     String eventType,
                     String username,
                     String result,
                     String detail) {
        write(false, request, eventType, username, result, detail);
    }

    private void write(boolean warning,
                       HttpServletRequest request,
                       String eventType,
                       String username,
                       String result,
                       String detail) {
        String requestId = request == null ? "-" : safe(request.getAttribute(RequestDiagnosticsFilter.REQUEST_ID_ATTRIBUTE));
        String ip = request == null ? "-" : clientIp(request);
        String method = request == null ? "-" : safe(request.getMethod());
        String path = request == null ? "-" : safe(request.getRequestURI());

        String template = "securityEvent={} result={} username={} ip={} method={} path={} requestId={} detail={}";
        Object[] values = {
                safe(eventType), safe(result), safe(username), safe(ip), safe(method), safe(path), safe(requestId), safe(detail)
        };
        if (warning) {
            log.warn(template, values);
        } else {
            log.info(template, values);
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return safe(comma >= 0 ? forwarded.substring(0, comma) : forwarded);
        }
        String realIp = request.getHeader("X-Real-IP");
        return safe(realIp == null || realIp.isBlank() ? request.getRemoteAddr() : realIp);
    }

    private String safe(Object value) {
        if (value == null) {
            return "-";
        }
        String text = String.valueOf(value)
                .replace('\r', '_')
                .replace('\n', '_')
                .replace('\t', '_')
                .trim();
        if (text.isEmpty()) {
            return "-";
        }
        return text.length() <= MAX_VALUE_LENGTH ? text : text.substring(0, MAX_VALUE_LENGTH);
    }
}
