package ru.daniil.shifts.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.util.List;

/**
 * Админское управление пользователями.
 * Сейчас поддерживаем только роли доступа USER/ADMIN.
 * accountTier хранится отдельно как задел под будущие FREE/PAID/VIP и не влияет на права.
 */
@Service
public class UserAdminService {
    public static final List<String> ALLOWED_ROLES = List.of("USER", "ADMIN");

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final MobileAuthService mobileAuthService;
    private final String bootstrapAdminUsername;

    public UserAdminService(UserRepository users,
                            PasswordEncoder encoder,
                            MobileAuthService mobileAuthService,
                            @Value("${dutylog.admin.username:}") String bootstrapAdminUsername) {
        this.users = users;
        this.encoder = encoder;
        this.mobileAuthService = mobileAuthService;
        this.bootstrapAdminUsername = bootstrapAdminUsername == null ? "" : bootstrapAdminUsername.trim();
    }

    public record AdminUserDto(
            Long id,
            String username,
            String displayName,
            String role,
            String accountTier,
            boolean bootstrapAdmin,
            boolean currentUser,
            String createdAt,
            String updatedAt
    ) {}

    @Transactional(readOnly = true)
    public List<AdminUserDto> listUsers(AppUser currentUser) {
        return users.findAllByOrderByIdAsc().stream()
                .map(user -> toDto(user, currentUser))
                .toList();
    }

    @Transactional(readOnly = true)
    public long adminCount() {
        return users.countByRoleIgnoreCase("ADMIN");
    }

    @Transactional(readOnly = true)
    public long userCount() {
        return users.count();
    }

    @Transactional
    public AdminUserDto changeRole(Long userId, String requestedRole, AppUser currentUser) {
        AppUser target = findTarget(userId);
        String role = normalizeRole(requestedRole);

        if ("USER".equals(role) && target.isAdmin()) {
            if (isBootstrapAdmin(target)) {
                throw ApiException.badRequest("Стартового env-админа нельзя понизить до USER");
            }
            if (target.getId().equals(currentUser.getId())) {
                throw ApiException.badRequest("Нельзя понизить собственную роль из активной админской сессии");
            }
            if (adminCount() <= 1) {
                throw ApiException.badRequest("Нельзя убрать последнего администратора");
            }
        }

        target.setRole(role);
        AppUser saved = users.save(target);
        return toDto(saved, currentUser);
    }

    @Transactional
    public AdminUserDto resetPassword(Long userId, String newPassword, AppUser currentUser) {
        AppUser target = findTarget(userId);
        String password = newPassword == null ? "" : newPassword;
        if (password.length() < 12) {
            throw ApiException.badRequest("Новый пароль: минимум 12 символов для админского сброса");
        }
        target.setPasswordHash(encoder.encode(password));
        AppUser saved = users.save(target);
        mobileAuthService.revokeAllSessions(saved);
        return toDto(saved, currentUser);
    }

    private AppUser findTarget(Long userId) {
        if (userId == null) {
            throw ApiException.badRequest("Не указан id пользователя");
        }
        return users.findById(userId).orElseThrow(() -> ApiException.notFound("Пользователь не найден"));
    }

    private String normalizeRole(String requestedRole) {
        String role = requestedRole == null ? "" : requestedRole.trim().toUpperCase();
        if (!ALLOWED_ROLES.contains(role)) {
            throw ApiException.badRequest("Роль должна быть USER или ADMIN");
        }
        return role;
    }

    private AdminUserDto toDto(AppUser user, AppUser currentUser) {
        return new AdminUserDto(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole(),
                user.getAccountTier(),
                isBootstrapAdmin(user),
                currentUser != null && user.getId().equals(currentUser.getId()),
                user.getCreatedAt() != null ? user.getCreatedAt().toString() : null,
                user.getUpdatedAt() != null ? user.getUpdatedAt().toString() : null
        );
    }

    private boolean isBootstrapAdmin(AppUser user) {
        return !bootstrapAdminUsername.isBlank() && user.getUsername().equalsIgnoreCase(bootstrapAdminUsername);
    }
}
