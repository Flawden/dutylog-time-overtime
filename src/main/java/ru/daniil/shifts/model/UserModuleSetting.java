package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Per-user module switch. Disabling a module hides it from the UI and guards its API,
 * but never deletes user data.
 */
@Entity
@Table(name = "user_module_settings", uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "module_key"}))
public class UserModuleSetting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @Column(name = "module_key", nullable = false, length = 60)
    private String moduleKey;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected UserModuleSetting() {}

    public UserModuleSetting(AppUser owner, String moduleKey, boolean enabled) {
        this.owner = owner;
        this.moduleKey = moduleKey;
        this.enabled = enabled;
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public void setOwner(AppUser owner) { this.owner = owner; }
    public String getModuleKey() { return moduleKey; }
    public void setModuleKey(String moduleKey) { this.moduleKey = moduleKey == null ? null : moduleKey.trim().toLowerCase(); }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Instant getUpdatedAt() { return updatedAt; }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
        setModuleKey(moduleKey);
    }
}
