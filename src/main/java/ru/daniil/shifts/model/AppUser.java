package ru.daniil.shifts.model;

import jakarta.persistence.*;

/**
 * Пользователь. Класс назван AppUser, а не User, чтобы не путаться
 * с org.springframework.security.core.userdetails.User.
 * Таблица — "users", потому что USER — зарезервированное слово в H2.
 */
@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = "username"))
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String username;

    /** BCrypt-хэш. Пароль в открытом виде нигде не хранится. */
    @Column(nullable = false, length = 100)
    private String passwordHash;

    /** Отображаемое имя (опционально). В шапке показывается оно, а не логин. */
    @Column(name = "display_name", length = 60)
    private String displayName;

    /** День рождения (опционально) — календарь поздравляет в этот день. */
    @Column(name = "birthday")
    private java.time.LocalDate birthday;

    /** Роль доступа. Пока поддерживаются USER и ADMIN. */
    @Column(length = 20)
    private String role = "USER";

    /**
     * Тариф/уровень аккаунта — задел под будущие FREE/PAID/VIP.
     * В v22.3 он только хранится и показывается, права от него не зависят.
     */
    @Column(name = "account_tier", length = 20)
    private String accountTier = "FREE";

    @Column(name = "created_at")
    private java.time.Instant createdAt;

    @Column(name = "updated_at")
    private java.time.Instant updatedAt;

    protected AppUser() {} // для JPA

    public AppUser(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public java.time.LocalDate getBirthday() { return birthday; }
    public void setBirthday(java.time.LocalDate birthday) { this.birthday = birthday; }
    public String getRole() { return role == null || role.isBlank() ? "USER" : role; }
    public void setRole(String role) { this.role = role == null || role.isBlank() ? "USER" : role.trim().toUpperCase(); }
    public boolean isAdmin() { return "ADMIN".equalsIgnoreCase(getRole()); }
    public String getAccountTier() { return accountTier == null || accountTier.isBlank() ? "FREE" : accountTier; }
    public void setAccountTier(String accountTier) { this.accountTier = accountTier == null || accountTier.isBlank() ? "FREE" : accountTier.trim().toUpperCase(); }
    public java.time.Instant getCreatedAt() { return createdAt; }
    public java.time.Instant getUpdatedAt() { return updatedAt; }

    @PrePersist
    void onCreate() {
        java.time.Instant now = java.time.Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        setRole(role);
        setAccountTier(accountTier);
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = java.time.Instant.now();
        setRole(role);
        setAccountTier(accountTier);
    }
}
