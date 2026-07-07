package ru.daniil.shifts.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.telegram.TelegramLinkService;
import ru.daniil.shifts.telegram.TelegramLinkService.TelegramStatusDto;

import java.security.Principal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Служебная диагностика для администратора.
 * Ничего секретного не отдаёт: токены, пароли и URL БД здесь не светятся.
 */
@RestController
@RequestMapping("/api/admin")
public class SystemController {
    private final Environment environment;
    private final JdbcTemplate jdbcTemplate;
    private final CurrentUserService currentUserService;
    private final TelegramLinkService telegramLinkService;

    @Value("${dutylog.telegram.enabled:false}")
    private boolean telegramEnabled;

    @Value("${dutylog.telegram.bot-token:}")
    private String telegramBotToken;

    @Value("${dutylog.telegram.polling-enabled:false}")
    private boolean telegramPollingEnabled;

    @Value("${dutylog.telegram.notifications-enabled:true}")
    private boolean telegramNotificationsEnabled;

    public SystemController(Environment environment,
                            JdbcTemplate jdbcTemplate,
                            CurrentUserService currentUserService,
                            TelegramLinkService telegramLinkService) {
        this.environment = environment;
        this.jdbcTemplate = jdbcTemplate;
        this.currentUserService = currentUserService;
        this.telegramLinkService = telegramLinkService;
    }

    @GetMapping("/status")
    public Map<String, Object> status(Principal principal) {
        AppUser user = requireAdmin(principal);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("app", "DutyLog: Time & Overtime");
        result.put("version", "22.0");
        result.put("admin", user.getUsername());
        result.put("serverTime", Instant.now().toString());
        result.put("serverTimezone", ZoneId.systemDefault().toString());
        result.put("profiles", Arrays.asList(environment.getActiveProfiles()));
        result.put("database", databaseStatus());
        result.put("telegram", telegramStatus(principal));
        return result;
    }

    private AppUser requireAdmin(Principal principal) {
        AppUser user = currentUserService.requireUser(principal);
        if (!user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Диагностика доступна только администратору");
        }
        return user;
    }

    private Map<String, Object> databaseStatus() {
        Map<String, Object> db = new LinkedHashMap<>();
        try {
            Integer one = jdbcTemplate.queryForObject("select 1", Integer.class);
            db.put("ok", one != null && one == 1);
        } catch (Exception e) {
            db.put("ok", false);
            db.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return db;
    }

    private Map<String, Object> telegramStatus(Principal principal) {
        Map<String, Object> tg = new LinkedHashMap<>();
        tg.put("enabled", telegramEnabled);
        tg.put("tokenConfigured", telegramBotToken != null && !telegramBotToken.isBlank());
        tg.put("pollingEnabled", telegramPollingEnabled);
        tg.put("notificationsEnabled", telegramNotificationsEnabled);
        try {
            AppUser user = currentUserService.requireUser(principal);
            TelegramStatusDto status = telegramLinkService.status(user);
            tg.put("configured", status.configured());
            tg.put("linked", status.linked());
            tg.put("accountNotificationsEnabled", status.notificationsEnabled());
            tg.put("botUsername", status.botUsername());
        } catch (Exception ignored) {
            tg.put("linked", false);
        }
        return tg;
    }
}
