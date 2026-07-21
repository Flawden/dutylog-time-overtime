package ru.daniil.shifts.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Persistent remember-me variant that deliberately keeps the token stable for
 * the lifetime of the browser cookie.
 *
 * Spring Security's default persistent-token implementation rotates the token
 * on every automatic login. A PWA restores several API requests in parallel
 * after the browser or container has been closed. The first request rotates the
 * token, while the remaining requests still carry the previous cookie and are
 * interpreted as token theft. The result is an apparently random logout.
 *
 * DutyLog already uses a random 64-character token, HTTPS-only HttpOnly cookies,
 * SameSite=Lax and explicit revocation after password/role changes. Avoiding
 * per-request rotation removes the concurrency race without weakening those
 * boundaries. Expiration remains fixed at the original login time.
 */
public final class StablePersistentRememberMeServices extends PersistentTokenBasedRememberMeServices {
    private final PersistentTokenRepository tokenRepository;
    private final UserDetailsService userDetailsService;
    private final int validitySeconds;

    public StablePersistentRememberMeServices(String key,
                                              UserDetailsService userDetailsService,
                                              PersistentTokenRepository tokenRepository,
                                              int validitySeconds) {
        super(key, userDetailsService, tokenRepository);
        this.userDetailsService = userDetailsService;
        this.tokenRepository = tokenRepository;
        this.validitySeconds = validitySeconds;
    }

    @Override
    protected UserDetails processAutoLoginCookie(String[] cookieTokens,
                                                 HttpServletRequest request,
                                                 HttpServletResponse response) {
        if (cookieTokens.length != 2) {
            throw new RememberMeAuthenticationException("Invalid remember-me cookie");
        }

        String presentedSeries = cookieTokens[0];
        String presentedToken = cookieTokens[1];
        PersistentRememberMeToken stored = tokenRepository.getTokenForSeries(presentedSeries);
        if (stored == null) {
            throw new RememberMeAuthenticationException("Remember-me token not found");
        }

        if (!constantTimeEquals(presentedToken, stored.getTokenValue())) {
            throw new RememberMeAuthenticationException("Remember-me token mismatch");
        }

        long expiresAt = stored.getDate().getTime() + (validitySeconds * 1000L);
        if (expiresAt < System.currentTimeMillis()) {
            tokenRepository.removeUserTokens(stored.getUsername());
            throw new RememberMeAuthenticationException("Remember-me token expired");
        }

        return userDetailsService.loadUserByUsername(stored.getUsername());
    }

    private static boolean constantTimeEquals(String left, String right) {
        byte[] a = String.valueOf(left).getBytes(StandardCharsets.UTF_8);
        byte[] b = String.valueOf(right).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }
}
