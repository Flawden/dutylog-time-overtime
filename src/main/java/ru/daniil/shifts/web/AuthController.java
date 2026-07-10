package ru.daniil.shifts.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ru.daniil.shifts.config.SecurityEventLogger;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.AppSettingsService;
import ru.daniil.shifts.service.DefaultShiftSeedService;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final DefaultShiftSeedService defaultShiftSeedService;
    private final AppSettingsService appSettingsService;
    private final SecurityEventLogger securityEvents;

    public AuthController(UserRepository users,
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

    public record RegisterRequest(String username, String password, String languagePreference) {}

    /** Публичный статус регистрации для страницы входа. */
    @GetMapping("/registration-status")
    public Map<String, Object> registrationStatus() {
        return appSettingsService.registrationStatus();
    }

    /**
     * Регистрация. После создания пользователю выдаётся стартовый набор смен.
     * 409 — если имя занято, 400 — если данные кривые.
     */
    @PostMapping("/register")
    @Transactional
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        if (!appSettingsService.isRegistrationEnabled()) {
            securityEvents.warn("REGISTRATION_REJECTED", null, "rejected", "reason=registration_closed");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Регистрация закрыта администратором"));
        }
        if (req == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Некорректный JSON в запросе"));
        }

        String username = req.username() == null ? "" : req.username().trim();
        String password = req.password() == null ? "" : req.password();

        if (username.length() < 3 || username.length() > 40) {
            return ResponseEntity.badRequest().body(Map.of("error", "Имя: от 3 до 40 символов"));
        }
        if (!username.matches("[A-Za-zА-Яа-яЁё0-9_.-]+")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Имя: только буквы, цифры, точка, дефис и подчёркивание"));
        }
        if (password.length() < 8) {
            return ResponseEntity.badRequest().body(Map.of("error", "Пароль: минимум 8 символов"));
        }
        if (users.existsByUsername(username)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Имя уже занято"));
        }

        AppUser user = new AppUser(username, encoder.encode(password));
        user.setLanguagePreference(req.languagePreference());
        user = users.save(user);
        defaultShiftSeedService.seedDefaults(user);
        securityEvents.info("REGISTRATION_SUCCEEDED", username, "accepted", "role=USER");
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "username", username,
                "languagePreference", user.getLanguagePreference()
        ));
    }

    /** Кто я — фронтенд показывает имя в шапке. */
    @GetMapping("/me")
    public Map<String, String> me(Principal principal) {
        return Map.of("username", principal.getName());
    }

}