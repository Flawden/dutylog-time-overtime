package ru.daniil.shifts.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ru.daniil.shifts.dto.Dtos.MobileAuthTokenDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.MobileAuthService;
import ru.daniil.shifts.service.RememberMeTokenService;
import ru.daniil.shifts.service.UserTimeService;
import ru.daniil.shifts.service.WorkTimezoneChangeService;
import ru.daniil.shifts.service.exception.ApiException;

import java.security.Principal;
import java.time.LocalDate;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Профиль пользователя: отображаемое имя, день рождения, смена пароля.
 * Принцип: каждое поле профиля читает какая-то фича. Имя — шапка,
 * ДР — поздравление в календаре. Полей «про запас» здесь нет сознательно.
 */
@RestController
@RequestMapping({"/api/profile", "/api/v1/profile"})
public class ProfileController {

    private final UserRepository users;
    private final CurrentUserService currentUserService;
    private final MobileAuthService mobileAuthService;
    private final RememberMeTokenService rememberMeTokenService;
    private final UserTimeService userTimeService;
    private final WorkTimezoneChangeService workTimezoneChangeService;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final PasswordEncoder encoder;

    public ProfileController(UserRepository users,
                             CurrentUserService currentUserService,
                             MobileAuthService mobileAuthService,
                             RememberMeTokenService rememberMeTokenService,
                             UserTimeService userTimeService,
                             WorkTimezoneChangeService workTimezoneChangeService,
                             PasswordEncoder encoder) {
        this.users = users;
        this.currentUserService = currentUserService;
        this.mobileAuthService = mobileAuthService;
        this.rememberMeTokenService = rememberMeTokenService;
        this.userTimeService = userTimeService;
        this.workTimezoneChangeService = workTimezoneChangeService;
        this.encoder = encoder;
    }

    public record ProfileUpdateRequest(String displayName, String birthday, String themePreference,
                                       String accentColor, String themePreset, Map<String, Object> themeConfig,
                                       String languagePreference, String workTimezone, String displayTimezone,
                                       Boolean onboardingCompleted) {}
    public record PasswordChangeRequest(String currentPassword, String newPassword) {}

    @GetMapping
    public Map<String, Object> get(Principal principal) {
        AppUser user = currentUserService.requireUser(principal);
        Map<String, Object> out = new HashMap<>();
        out.put("username", user.getUsername());
        out.put("displayName", user.getDisplayName());
        out.put("birthday", user.getBirthday() != null ? user.getBirthday().toString() : null);
        out.put("admin", user.isAdmin());
        out.put("role", user.getRole());
        out.put("accountTier", user.getAccountTier());
        out.put("themePreference", user.getThemePreference());
        out.put("accentColor", user.getAccentColor());
        out.put("themePreset", user.getThemePreset());
        out.put("themeConfig", readThemeConfig(user.getThemeConfig()));
        out.put("languagePreference", user.getLanguagePreference());
        out.put("workTimezone", user.getWorkTimezone());
        out.put("displayTimezone", user.getDisplayTimezone());
        out.put("onboardingCompleted", user.isOnboardingCompleted());
        return out;
    }

    @PutMapping
    @Transactional
    public Map<String, Object> update(@RequestBody ProfileUpdateRequest req, Principal principal) {
        AppUser user = currentUserService.requireUser(principal);

        /*
         * Legacy wire compatibility:
         * Profile PUT still accepts workTimezone/displayTimezone, but a change
         * now becomes an effective-dated Work Context mutation from the start
         * of the user's current work-local day.
         *
         * Historical edits use the dedicated Work Context API.
         */
        String requestedTimezone =
                req.workTimezone() != null
                        ? req.workTimezone()
                        : req.displayTimezone();

        if (requestedTimezone != null) {
            String timezone =
                    validatedTimezone(
                            requestedTimezone,
                            "Часовой пояс"
                    );

            String currentTimezone =
                    userTimeService.workZone(user).getId();

            if (!currentTimezone.equals(timezone)) {
                workTimezoneChangeService.upsertAndReconcile(
                        user,
                        userTimeService.workToday(user).atStartOfDay(),
                        timezone
                );
            }
        }

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
                if (bd.isAfter(userTimeService.workToday(user))) {
                    throw ApiException.badRequest("День рождения в будущем? Завидую, но нет");
                }
                user.setBirthday(bd);
            } catch (DateTimeParseException e) {
                throw ApiException.badRequest("Дата рождения должна быть в формате yyyy-MM-dd");
            }
        }

        if (req.themePreference() != null) {
            String theme = req.themePreference().trim().toLowerCase();
            if (!theme.equals("system") && !theme.equals("light") && !theme.equals("dark")) {
                throw ApiException.badRequest("Тема должна быть system, light или dark");
            }
            user.setThemePreference(theme);
        }

        if (req.accentColor() != null) {
            String accent = req.accentColor().trim();
            if (!accent.matches("#[0-9a-fA-F]{6}")) {
                throw ApiException.badRequest("Акцентный цвет должен быть в формате #RRGGBB");
            }
            user.setAccentColor(accent.toUpperCase());
        }

        if (req.themePreset() != null) {
            String preset = req.themePreset().trim();
            if (!preset.matches("[A-Za-z0-9_-]{1,40}")) {
                throw ApiException.badRequest("Пресет темы должен быть коротким безопасным идентификатором");
            }
            user.setThemePreset(preset);
        }

        if (req.themeConfig() != null) {
            user.setThemeConfig(writeSafeThemeConfig(req.themeConfig()));
        }

        if (req.languagePreference() != null) {
            String lang = req.languagePreference().trim().toLowerCase();
            if (!lang.equals("ru") && !lang.equals("en")) {
                throw ApiException.badRequest("Язык интерфейса должен быть ru или en");
            }
            user.setLanguagePreference(lang);
        }

        if (req.onboardingCompleted() != null) {
            user.setOnboardingCompleted(req.onboardingCompleted());
        }

        users.save(user);
        return get(principal);
    }


    private String validatedTimezone(String raw, String fieldName) {
        String timezone = raw == null ? "" : raw.trim();
        if (timezone.isBlank() || timezone.length() > 80) {
            throw ApiException.badRequest(fieldName + " должен быть IANA-идентификатором, например Europe/Moscow");
        }
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException e) {
            throw ApiException.badRequest("Неизвестный часовой пояс: " + timezone);
        }
        return timezone;
    }

    private Map<String, Object> readThemeConfig(String raw) {
        if (raw == null || raw.isBlank()) return Map.of();
        try {
            Map<String, Object> parsed = JSON.readValue(raw, MAP_TYPE);
            return safeThemeConfig(parsed);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String writeSafeThemeConfig(Map<String, Object> input) {
        try {
            return JSON.writeValueAsString(safeThemeConfig(input));
        } catch (Exception e) {
            throw ApiException.badRequest("Не удалось сохранить настройки темы");
        }
    }

    private Map<String, Object> safeThemeConfig(Map<String, Object> input) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("appBg", safeColor(input.get("appBg"), ""));
        out.put("panelBg", safeColor(input.get("panelBg"), ""));
        out.put("panelAltBg", safeColor(input.get("panelAltBg"), ""));
        out.put("textColor", safeColor(input.get("textColor"), ""));
        out.put("mutedColor", safeColor(input.get("mutedColor"), ""));
        out.put("borderColor", safeColor(input.get("borderColor"), ""));
        out.put("buttonStyle", safeEnum(input.get("buttonStyle"), "solid", "solid", "soft", "outline", "ghost"));
        out.put("cardStyle", safeEnum(input.get("cardStyle"), "default", "default", "flat", "soft", "contrast", "warm"));
        out.put("shadowLevel", safeEnum(input.get("shadowLevel"), "medium", "none", "low", "soft", "medium", "strong"));
        out.put("density", safeEnum(input.get("density"), "comfortable", "compact", "comfortable", "spacious"));
        out.put("cardRadius", clampInt(input.get("cardRadius"), 14, 6, 28));
        out.put("uiContract", clampInt(input.get("uiContract"), 2, 2, 2));
        out.put("workspaceId", safeEnum(input.get("workspaceId"), "shift-worker", "shift-worker", "planner", "minimal", "custom"));
        out.put("layoutId", safeEnum(input.get("layoutId"), "dashboard", "dashboard", "compact", "focus", "sidebar", "mobile-flow"));
        out.put("themeId", safeEnum(input.get("themeId"), "default", "default", "custom", "midnight", "oled", "forest", "sunset", "industrial", "softPurple"));
        out.put("paletteId", safeEnum(input.get("paletteId"), "theme", "theme", "gold-teal", "teal-gold", "violet", "ember", "custom"));
        out.put("decorationId", safeEnum(input.get("decorationId"), "none", "none", "grid"));
        out.put("accentSecondary", safeColor(input.get("accentSecondary"), "#14CDB4"));
        out.put("todayWidgets", safeTodayWidgets(input.get("todayWidgets")));
        out.put("navigationOrder", safeNavigationOrder(input.get("navigationOrder")));
        out.put("navigationVisible", safeNavigationVisible(input.get("navigationVisible")));
        out.put("calendarDensity", safeEnum(input.get("calendarDensity"), "comfortable", "comfortable", "compact"));
        out.put("calendarLayerStyle", safeEnum(input.get("calendarLayerStyle"), "pills", "pills", "dots"));
        return out;
    }

    private String safeColor(Object value, String fallback) {
        String s = value == null ? "" : value.toString().trim();
        if (s.isEmpty()) return fallback;
        if (!s.matches("#[0-9a-fA-F]{6}")) {
            throw ApiException.badRequest("Цвет темы должен быть в формате #RRGGBB");
        }
        return s.toUpperCase();
    }

    private String safeEnum(Object value, String fallback, String... allowed) {
        String s = value == null ? fallback : value.toString().trim();
        for (String option : allowed) if (option.equals(s)) return s;
        throw ApiException.badRequest("Недопустимый параметр темы: " + s);
    }

    private int clampInt(Object value, int fallback, int min, int max) {
        int n;
        if (value instanceof Number number) n = number.intValue();
        else {
            try { n = Integer.parseInt(value == null ? String.valueOf(fallback) : value.toString()); }
            catch (NumberFormatException e) { n = fallback; }
        }
        return Math.max(min, Math.min(max, n));
    }

    private List<String> safeStringList(Object value, String... allowed) {
        if (!(value instanceof Collection<?> collection)) return List.of();
        Set<String> allowedValues = Set.of(allowed);
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Object item : collection) {
            String candidate = item == null ? "" : item.toString().trim();
            if (allowedValues.contains(candidate)) result.add(candidate);
        }
        return List.copyOf(result);
    }


    private List<String> safeTodayWidgets(Object value) {
        List<String> selected = safeStringList(value, "shift", "overtime", "tasks", "important");
        if (selected.isEmpty()) return List.of();
        LinkedHashSet<String> result = new LinkedHashSet<>(selected);
        if (!result.contains("shift")) {
            LinkedHashSet<String> withRequiredShift = new LinkedHashSet<>();
            withRequiredShift.add("shift");
            withRequiredShift.addAll(result);
            result = withRequiredShift;
        }
        return List.copyOf(result);
    }

    private List<String> safeNavigationOrder(Object value) {
        List<String> canonical = List.of("today", "calendar", "vacation", "overtime", "payroll", "tasks", "important", "settings");
        LinkedHashSet<String> result = new LinkedHashSet<>(safeStringList(value, canonical.toArray(String[]::new)));
        result.addAll(canonical);
        return List.copyOf(result);
    }

    private List<String> safeNavigationVisible(Object value) {
        List<String> fallback = List.of("today", "calendar", "vacation", "overtime", "settings");
        List<String> selected = safeStringList(value, "today", "calendar", "vacation", "overtime", "payroll", "tasks", "important", "settings");
        if (selected.isEmpty()) selected = fallback;
        LinkedHashSet<String> requested = new LinkedHashSet<>(selected);
        requested.add("today");
        requested.add("settings");
        List<String> order = safeNavigationOrder(value);
        List<String> result = new java.util.ArrayList<>();
        for (String id : order) {
            if (requested.contains(id) && result.size() < 5) result.add(id);
        }
        if (!result.contains("today")) result.add(0, "today");
        if (!result.contains("settings")) {
            if (result.size() >= 5) result.remove(result.size() - 1);
            result.add("settings");
        }
        return List.copyOf(result);
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
    public ResponseEntity<Void> revokeSession(@PathVariable("id") Long id, Principal principal) {
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
        int minLength = user.isAdmin() ? 12 : 8;
        if (next.length() < minLength) {
            throw ApiException.badRequest("Новый пароль: минимум " + minLength + " символов");
        }
        if (next.equals(current)) {
            throw ApiException.badRequest("Новый пароль совпадает со старым");
        }

        user.setPasswordHash(encoder.encode(next));
        user.bumpAuthVersion();
        users.save(user);
        mobileAuthService.revokeAllSessions(user);
        rememberMeTokenService.revokeAll(user);
        return ResponseEntity.noContent().build();
    }
}
