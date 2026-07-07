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

import java.io.IOException;
import java.util.Optional;

/**
 * Принимает Authorization: Bearer <accessToken> для Android/API-клиентов.
 * Веб-версия продолжает работать через обычную JSESSIONID-cookie.
 */
@Component
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {
    private final MobileAuthService mobileAuthService;

    public BearerTokenAuthenticationFilter(MobileAuthService mobileAuthService) {
        this.mobileAuthService = mobileAuthService;
    }



    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "/api/mobile/auth/login".equals(path)
                || "/api/mobile/auth/refresh".equals(path)
                || "/api/mobile/auth/logout".equals(path)
                || "/api/auth/register".equals(path)
                || "/api/auth/registration-status".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring("Bearer ".length()).trim();
        Optional<AppUser> user = mobileAuthService.authenticateAccessToken(token);
        if (user.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"Access token недействителен или истёк\"}");
            return;
        }

        AppUser appUser = user.get();
        java.util.ArrayList<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (appUser.isAdmin()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                appUser.getUsername(),
                null,
                authorities
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        mobileAuthService.touchAccessToken(token);
        filterChain.doFilter(request, response);
    }
}
