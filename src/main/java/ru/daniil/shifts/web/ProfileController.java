package ru.daniil.shifts.web;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ru.daniil.shifts.dto.Dtos.MobileAuthTokenDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.MobileAuthService;
import ru.daniil.shifts.service.exception.ApiException;

import java.security.Principal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Профиль пользователя: отображаемое имя, день рождения, смена пароля.
 * Принцип: каждое поле профиля читает какая-то фича. Имя — шапка,
 * ДР — поздравление в календаре. Полей «про запас» здесь нет сознательно.
 */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final UserRepository users;
    private final CurrentUserService currentUserService;
    private final MobileAuthService mobileAuthService;
    private final PasswordEncoder encoder;

    public ProfileController(UserRepository users,
                             CurrentUserService currentUserService,
                             MobileAuthService mobileAuthService,
                             PasswordEncoder encoder) {
        this.users = users;
        this.currentUserService = currentUserService;
        this.mobileAuthService = mobileAuthService;
        this.encoder = encoder;
    }

    public record ProfileUpdateRequest(String displayName, String birthday) {}
    public record PasswordChangeRequest(String currentPassword, String newPassword) {}

    @GetMapping
    public Map<String, Object> get(Principal principal) {
        AppUser user = currentUserService.requireUser(principal);
        Map<String, Object> out = new HashMap<>();
        out.put("username", user.getUsername());
        out.put("displayName", user.getDisplayName());
        out.put("birthday", user.getBirthday() != null ? user.getBirthday().toString() : null);
        out.put("admin", user.isAdmin());
        return out;
    }

    @PutMapping
    @Transactional
    public Map<String, Object> update(@RequestBody ProfileUpdateRequest req, Principal principal) {
        AppUser user = currentUserService.requireUser(principal);

        String name = req.displayName() == null ? null : req.displayName().trim();
        if (name != null && name.length() > 60) {
            throw ApiException.badRequest("Имя: максимум 60 символов");
        }
        user.setDisplayName(name == null || name.isEmpty() ? null : name);

        if (req.birthday() == null || req.birthday().isBlank()) {
            user.setBirthday(null);
        } else {
            try {
                LocalDate bd = LocalDate.parse(req.birthday());
                if (bd.isAfter(LocalDate.now())) {
                    throw ApiException.badRequest("День рождения в будущем? Завидую, но нет");
                }
                user.setBirthday(bd);
            } catch (DateTimeParseException e) {
                throw ApiException.badRequest("Дата рождения должна быть в формате yyyy-MM-dd");
            }
        }

        users.save(user);
        return get(principal);
    }


    /**
     * Web-friendly список мобильных устройств.
     * Не используем /api/mobile/** из браузерного UI, чтобы mobile API
     * оставался stateless/Bearer и был исключён из CSRF только для Android.
     */
    @GetMapping("/sessions")
    public List<MobileAuthTokenDto> sessions(Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        return mobileAuthService.sessions(current);
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> revokeSession(@PathVariable Long id, Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        mobileAuthService.revokeSession(current, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Смена пароля. Требует текущий пароль — иначе любой, кто сел за
     * открытый браузер, угнал бы аккаунт. После смены все мобильные
     * сессии отзываются: украденный телефон не должен переживать смену пароля.
     */
    @PostMapping("/password")
    @Transactional
    public ResponseEntity<Void> changePassword(@RequestBody PasswordChangeRequest req, Principal principal) {
        AppUser user = currentUserService.requireUser(principal);

        String current = req.currentPassword() == null ? "" : req.currentPassword();
        String next = req.newPassword() == null ? "" : req.newPassword();

        if (!encoder.matches(current, user.getPasswordHash())) {
            throw ApiException.badRequest("Текущий пароль неверный");
        }
        if (next.length() < 6) {
            throw ApiException.badRequest("Новый пароль: минимум 6 символов");
        }
        if (next.equals(current)) {
            throw ApiException.badRequest("Новый пароль совпадает со старым");
        }

        user.setPasswordHash(encoder.encode(next));
        users.save(user);
        mobileAuthService.revokeAllSessions(user);
        return ResponseEntity.noContent().build();
    }
}
