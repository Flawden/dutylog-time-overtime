package ru.daniil.shifts.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.repo.ShiftTypeRepository;
import ru.daniil.shifts.repo.UserRepository;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository users;
    private final ShiftTypeRepository shiftTypes;
    private final PasswordEncoder encoder;

    public AuthController(UserRepository users, ShiftTypeRepository shiftTypes, PasswordEncoder encoder) {
        this.users = users;
        this.shiftTypes = shiftTypes;
        this.encoder = encoder;
    }

    public record RegisterRequest(String username, String password) {}

    /**
     * Регистрация. После создания пользователю выдаётся стартовый набор смен.
     * 409 — если имя занято, 400 — если данные кривые.
     */
    @PostMapping("/register")
    @Transactional
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        String username = req.username() == null ? "" : req.username().trim();
        String password = req.password() == null ? "" : req.password();

        if (username.length() < 3 || username.length() > 40) {
            return ResponseEntity.badRequest().body(Map.of("error", "Имя: от 3 до 40 символов"));
        }
        if (password.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "Пароль: минимум 6 символов"));
        }
        if (users.existsByUsername(username)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Имя уже занято"));
        }

        AppUser user = users.save(new AppUser(username, encoder.encode(password)));
        seedDefaults(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("username", username));
    }

    /** Кто я — фронтенд показывает имя в шапке. */
    @GetMapping("/me")
    public Map<String, String> me(Principal principal) {
        return Map.of("username", principal.getName());
    }

    /** Стартовый набор для нового пользователя — минимум, остальное он создаст сам. */
    private void seedDefaults(AppUser user) {
        shiftTypes.saveAll(List.of(
                new ShiftType(user, "Дневная", 8, "#F5B841", false),
                new ShiftType(user, "Ночная",  8, "#7B8CE0", false)
        ));
    }
}
