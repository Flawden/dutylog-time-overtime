package ru.daniil.shifts.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.config.SecurityEventLogger;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.exception.ApiException;

/** Shared registration rules for web and versioned Android API. */
@Service
public class UserRegistrationService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final DefaultShiftSeedService defaultShiftSeedService;
    private final AppSettingsService appSettingsService;
    private final SecurityEventLogger securityEvents;

    public UserRegistrationService(UserRepository users,
                                   PasswordEncoder encoder,
                                   DefaultShiftSeedService defaultShiftSeedService,
                                   AppSettingsService appSettingsService,
                                   SecurityEventLogger securityEvents) {
        this.users = users;
        this.encoder = encoder;
        this.defaultShiftSeedService = defaultShiftSeedService;
        this.appSettingsService = appSettingsService;
        this.securityEvents = securityEvents;
    }

    @Transactional
    public AppUser register(String rawUsername,
                            String rawPassword,
                            String languagePreference,
                            String channel) {
        String safeChannel = channel == null || channel.isBlank() ? "unknown" : channel;
        if (!appSettingsService.isRegistrationEnabled()) {
            securityEvents.warn("REGISTRATION_REJECTED", null, "rejected",
                    "reason=registration_closed channel=" + safeChannel);
            throw ApiException.forbidden("REGISTRATION_CLOSED", "Регистрация закрыта администратором");
        }

        String username = rawUsername == null ? "" : rawUsername.trim();
        String password = rawPassword == null ? "" : rawPassword;
        if (username.length() < 3 || username.length() > 40) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Имя: от 3 до 40 символов");
        }
        if (!username.matches("[A-Za-zА-Яа-яЁё0-9_.-]+")) {
            throw ApiException.badRequest("VALIDATION_FAILED",
                    "Имя: только буквы, цифры, точка, дефис и подчёркивание");
        }
        if (password.length() < 8) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Пароль: минимум 8 символов");
        }
        if (users.existsByUsername(username)) {
            throw ApiException.conflict("USERNAME_TAKEN", "Имя уже занято");
        }

        AppUser user = new AppUser(username, encoder.encode(password));
        user.setLanguagePreference(languagePreference);
        try {
            user = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // The pre-check is for friendly UX; the unique constraint is the real
            // race-safe guard when two registrations arrive simultaneously.
            throw ApiException.conflict("USERNAME_TAKEN", "Имя уже занято");
        }
        defaultShiftSeedService.seedDefaults(user);
        securityEvents.info("REGISTRATION_SUCCEEDED", username, "accepted",
                "role=USER channel=" + safeChannel);
        return user;
    }
}
