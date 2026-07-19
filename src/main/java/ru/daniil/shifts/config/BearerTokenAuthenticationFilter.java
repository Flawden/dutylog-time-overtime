package ru.daniil.shifts.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.MobileAuthService;
import ru.daniil.shifts.web.ApiErrorWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;

/** Accepts Bearer access tokens for Android and shared API endpoints. */
@Component
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {
    private final MobileAuthService mobileAuthService;
    private final SecurityEventLogger securityEvents;
    private final ApiErrorWriter apiErrors;

    public BearerTokenAuthenticationFilter(MobileAuthService mobileAuthService,
                                           SecurityEventLogger securityEvents,
                                           ApiErrorWriter apiErrors) {
        this.mobileAuthService = mobileAuthService;
        this.securityEvents = securityEvents;
        this.apiErrors = apiErrors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "/api/mobile/auth/login".equals(path)
                || "/api/mobile/auth/refresh".equals(path)
                || "/api/mobile/auth/logout".equals(path)
                || "/api/v1/mobile/auth/login".equals(path)
                || "/api/v1/mobile/auth/register".equals(path)
                || "/api/v1/mobile/auth/registration-status".equals(path)
                || "/api/v1/mobile/auth/refresh".equals(path)
                || "/api/v1/mobile/auth/logout".equals(path)
                || "/api/auth/register".equals(path)
                || "/api/auth/registration-status".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        String token = bearerToken(header);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }
        Optional<AppUser> user = mobileAuthService.authenticateAccessToken(token);
        if (user.isEmpty()) {
            securityEvents.warn(request, "AUTH_TOKEN_REJECTED", null, "rejected",
                    "reason=invalid_or_expired_access_token");
            apiErrors.write(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                    "TOKEN_INVALID", "Access token недействителен или истёк");
            return;
        }

        AppUser appUser = user.get();
        ArrayList<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (appUser.isAdmin()) authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(appUser.getUsername(), null, authorities));
        mobileAuthService.touchAccessToken(token);
        filterChain.doFilter(request, response);
    }
    /** HTTP authentication schemes are case-insensitive (RFC 9110). */
    static boolean hasBearerScheme(String header) {
        if (header == null) return false;
        int start = 0;
        while (start < header.length() && Character.isWhitespace(header.charAt(start))) start++;
        return header.length() - start > 6
                && header.regionMatches(true, start, "Bearer", 0, 6)
                && Character.isWhitespace(header.charAt(start + 6));
    }

    static String bearerToken(String header) {
        if (!hasBearerScheme(header)) return null;
        int start = 0;
        while (Character.isWhitespace(header.charAt(start))) start++;
        return header.substring(start + 6).trim();
    }

}
