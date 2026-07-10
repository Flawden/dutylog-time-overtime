package ru.daniil.shifts.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.AppSettingsService;
import ru.daniil.shifts.service.UserRegistrationService;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AppSettingsService appSettingsService;
    private final UserRegistrationService registrationService;

    public AuthController(AppSettingsService appSettingsService,
                          UserRegistrationService registrationService) {
        this.appSettingsService = appSettingsService;
        this.registrationService = registrationService;
    }

    public record RegisterRequest(String username, String password, String languagePreference) {}

    @GetMapping("/registration-status")
    public Map<String, Object> registrationStatus() {
        return appSettingsService.registrationStatus();
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody(required = false) RegisterRequest req) {
        if (req == null) {
            throw ru.daniil.shifts.service.exception.ApiException.badRequest(
                    "INVALID_JSON", "Некорректный JSON в запросе");
        }
        AppUser user = registrationService.register(
                req.username(), req.password(), req.languagePreference(), "web");
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "username", user.getUsername(),
                "languagePreference", user.getLanguagePreference()
        ));
    }

    @GetMapping("/me")
    public Map<String, String> me(Principal principal) {
        return Map.of("username", principal.getName());
    }
}
