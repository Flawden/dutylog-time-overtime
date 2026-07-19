package ru.daniil.shifts.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves the client address without trusting attacker-controlled forwarding
 * headers unless the deployment explicitly runs behind the configured edge proxy.
 */
@Component
public class ClientIpResolver {
    private static final int MAX_IP_LENGTH = 64;
    private final boolean trustProxyHeaders;

    @Autowired
    public ClientIpResolver(@Value("${dutylog.security.trust-proxy-headers:false}") boolean trustProxyHeaders) {
        this.trustProxyHeaders = trustProxyHeaders;
    }

    String resolve(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }

        if (trustProxyHeaders) {
            // The supplied nginx/Caddy configs overwrite X-Real-IP at the edge.
            String realIp = validIp(request.getHeader("X-Real-IP"));
            if (realIp != null) {
                return realIp;
            }

            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null) {
                int comma = forwarded.indexOf(',');
                String first = comma >= 0 ? forwarded.substring(0, comma) : forwarded;
                String forwardedIp = validIp(first);
                if (forwardedIp != null) {
                    return forwardedIp;
                }
            }
        }

        String remote = validIp(request.getRemoteAddr());
        return remote == null ? "unknown" : remote;
    }

    boolean trustsProxyHeaders() {
        return trustProxyHeaders;
    }

    private String validIp(String value) {
        if (value == null) return null;
        String candidate = value.trim();
        if (candidate.isEmpty() || candidate.length() > MAX_IP_LENGTH) return null;

        if (isIpv4(candidate) || isIpv6(candidate)) {
            return candidate;
        }
        return null;
    }

    private boolean isIpv4(String candidate) {
        String[] parts = candidate.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) return false;
            int value = 0;
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                if (c < '0' || c > '9') return false;
                value = value * 10 + (c - '0');
            }
            if (value > 255) return false;
        }
        return true;
    }

    private boolean isIpv6(String candidate) {
        if (!candidate.contains(":")) return false;
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            if (!(c == ':' || c == '.'
                    || (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F'))) {
                return false;
            }
        }
        return true;
    }
}
