package ru.daniil.shifts.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;

import java.io.IOException;

/**
 * Invalidates stale browser sessions after password or role changes.
 *
 * Bearer/mobile authentication is intentionally ignored: mobile tokens are
 * checked against the current user row on every request and are revoked on
 * password changes by {@code MobileAuthService}.
 */
@Component
public class WebAccountStateFilter extends OncePerRequestFilter {
    private final UserRepository users;

    public WebAccountStateFilter(UserRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof DutyLogUserPrincipal principal) {
            AppUser current = users.findByUsername(principal.getUsername()).orElse(null);
            if (current == null || current.getAuthVersion() != principal.getAuthVersion()) {
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
