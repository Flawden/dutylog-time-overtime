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
}
