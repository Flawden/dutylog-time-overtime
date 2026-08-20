package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Effective-dated work-timezone context.
 *
 * A term owns the half-open interval
 * [effectiveFrom, nextTerm.effectiveFrom).
 *
 * effectiveFrom is intentionally a local wall-clock boundary. The term tells
 * DutyLog which IANA zone must be used to interpret facts in that work context.
 */
@Entity
@Table(
        name = "work_timezone_terms",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_work_timezone_term_owner_effective",
                columnNames = {"user_id", "effective_from"}
        ),
        indexes = @Index(
                name = "idx_work_timezone_terms_owner_effective",
                columnList = "user_id,effective_from"
        )
)
public class WorkTimezoneTerm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(name = "effective_from", nullable = false)
    private LocalDateTime effectiveFrom;

    @Column(name = "timezone_id", nullable = false, length = 80)
    private String timezoneId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected WorkTimezoneTerm() {}

    public WorkTimezoneTerm(
            AppUser owner,
            LocalDateTime effectiveFrom,
            String timezoneId
    ) {
        this.owner = owner;
        this.effectiveFrom = effectiveFrom;
        this.timezoneId = timezoneId;
    }

    public Long getId() {
        return id;
    }

    public AppUser getOwner() {
        return owner;
    }

    public LocalDateTime getEffectiveFrom() {
        return effectiveFrom;
    }

    public String getTimezoneId() {
        return timezoneId;
    }

    public void setTimezoneId(String timezoneId) {
        this.timezoneId = timezoneId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
