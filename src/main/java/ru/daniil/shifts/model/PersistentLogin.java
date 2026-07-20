package ru.daniil.shifts.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Schema ownership for Spring Security persistent remember-me tokens.
 *
 * Runtime reads/writes are performed by JdbcTokenRepositoryImpl. Keeping a
 * mapped entity lets Hibernate create the same table in H2 dev/test profiles,
 * while Flyway owns the PostgreSQL production schema.
 */
@Entity
@Table(name = "persistent_logins", indexes = {
        @Index(name = "idx_persistent_logins_username", columnList = "username")
})
public class PersistentLogin {

    @Id
    @Column(nullable = false, length = 64)
    private String series;

    @Column(nullable = false, length = 40)
    private String username;

    @Column(nullable = false, length = 64)
    private String token;

    @Column(name = "last_used", nullable = false)
    private LocalDateTime lastUsed;

    protected PersistentLogin() {
    }
}
