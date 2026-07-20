package ru.daniil.shifts.service;

import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.stereotype.Service;
import ru.daniil.shifts.model.AppUser;

/** Central revocation point for persistent web-login cookies. */
@Service
public class RememberMeTokenService {
    private final PersistentTokenRepository tokens;

    public RememberMeTokenService(PersistentTokenRepository tokens) {
        this.tokens = tokens;
    }

    public void revokeAll(AppUser user) {
        if (user != null) revokeAll(user.getUsername());
    }

    public void revokeAll(String username) {
        if (username != null && !username.isBlank()) {
            tokens.removeUserTokens(username);
        }
    }
}
