package ru.daniil.shifts.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;

import java.util.regex.Pattern;

/**
 * Production-safe bootstrap администратора.
 *
 * Важно: публичная регистрация больше никогда не выдаёт ADMIN сама по себе.
 * Первый администратор создаётся или обновляется только из переменных окружения:
 * DUTYLOG_ADMIN_USERNAME + DUTYLOG_ADMIN_PASSWORD.
 */
@Service
public class AdminBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapService.class);
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-zА-Яа-яЁё0-9_.-]+");

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final DefaultShiftSeedService defaultShiftSeedService;
    private final String adminUsername;
    private final String adminPassword;

    public AdminBootstrapService(UserRepository users,
                                 PasswordEncoder encoder,
                                 DefaultShiftSeedService defaultShiftSeedService,
                                 @Value("${dutylog.admin.username:}") String adminUsername,
                                 @Value("${dutylog.admin.password:}") String adminPassword) {
        this.users = users;
        this.encoder = encoder;
        this.defaultShiftSeedService = defaultShiftSeedService;
        this.adminUsername = adminUsername == null ? "" : adminUsername.trim();
        this.adminPassword = adminPassword == null ? "" : adminPassword;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void bootstrapAdmin() {
        boolean hasUsername = !adminUsername.isBlank();
        boolean hasPassword = !adminPassword.isBlank();

        if (!hasUsername && !hasPassword) {
            log.info("DutyLog admin bootstrap is not configured; public registration creates USER accounts only.");
            return;
        }
        if (!hasUsername || !hasPassword) {
            throw new IllegalStateException("DUTYLOG_ADMIN_USERNAME and DUTYLOG_ADMIN_PASSWORD must be set together");
        }

        validateAdminCredentials(adminUsername, adminPassword);

        AppUser user = users.findByUsername(adminUsername).orElse(null);
        if (user == null) {
            user = new AppUser(adminUsername, encoder.encode(adminPassword));
            user.setRole("ADMIN");
            user = users.save(user);
            defaultShiftSeedService.seedDefaults(user);
            int demoted = demoteUnexpectedAdmins(adminUsername);
            log.info("DutyLog bootstrap administrator '{}' created from environment. Demoted unexpected admins: {}.", adminUsername, demoted);
            return;
        }

        boolean promoted = !user.isAdmin();
        user.setRole("ADMIN");
        user.setPasswordHash(encoder.encode(adminPassword));
        users.save(user);
        int demoted = demoteUnexpectedAdmins(adminUsername);

        if (promoted) {
            log.info("DutyLog bootstrap user '{}' promoted to ADMIN from environment. Demoted unexpected admins: {}.", adminUsername, demoted);
        } else {
            log.info("DutyLog bootstrap administrator '{}' ensured from environment. Demoted unexpected admins: {}.", adminUsername, demoted);
        }
    }

    private int demoteUnexpectedAdmins(String expectedUsername) {
        int demoted = 0;
        for (AppUser user : users.findAll()) {
            if (user.isAdmin() && !user.getUsername().equalsIgnoreCase(expectedUsername)) {
                user.setRole("USER");
                users.save(user);
                demoted++;
            }
        }
        return demoted;
    }

    private void validateAdminCredentials(String username, String password) {
        if (username.length() < 3 || username.length() > 40 || !USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalStateException("DUTYLOG_ADMIN_USERNAME must be 3-40 chars and contain only letters, digits, dot, dash or underscore");
        }
        if (password.length() < 20) {
            throw new IllegalStateException("DUTYLOG_ADMIN_PASSWORD must be at least 20 characters long");
        }
    }
}
