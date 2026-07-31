package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Owner-scoped private iCalendar subscription.
 *
 * <p>The raw bearer token is never persisted. Only its SHA-256 digest and a
 * short non-secret hint are stored, so a database read cannot recreate a
 * working feed URL.</p>
 */
@Entity
@Table(name = "calendar_feed_subscriptions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_calendar_feed_subscription_user", columnNames = "user_id"),
                @UniqueConstraint(name = "uk_calendar_feed_subscription_token_hash", columnNames = "token_hash")
        })
public class CalendarFeedSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "token_hint", nullable = false, length = 12)
    private String tokenHint;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "rotated_at", nullable = false)
    private Instant rotatedAt = Instant.now();

    protected CalendarFeedSubscription() {}

    public CalendarFeedSubscription(AppUser owner, String tokenHash, String tokenHint) {
        this.owner = owner;
        this.tokenHash = tokenHash;
        this.tokenHint = tokenHint;
        Instant now = Instant.now();
        this.createdAt = now;
        this.rotatedAt = now;
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public String getTokenHash() { return tokenHash; }
    public String getTokenHint() { return tokenHint; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getRotatedAt() { return rotatedAt; }

    public void rotate(String tokenHash, String tokenHint) {
        this.tokenHash = tokenHash;
        this.tokenHint = tokenHint;
        this.rotatedAt = Instant.now();
    }
}
