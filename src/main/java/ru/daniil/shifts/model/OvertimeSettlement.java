package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Explicit decision to remove overtime-bank minutes for future monetary
 * settlement.
 *
 * This entity stores the business decision only. The canonical Time Bank debit
 * is projected into OvertimeUsage(sourceKind=SETTLEMENT), and money/pricing is
 * deliberately not stored here yet.
 */
@Entity
@Table(
        name = "overtime_settlements",
        indexes = @Index(
                name = "idx_overtime_settlements_owner_date",
                columnList = "user_id, settlement_date, id"
        )
)
public class OvertimeSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(name = "settlement_date", nullable = false)
    private LocalDate settlementDate;

    @Column(name = "requested_minutes", nullable = false)
    private int requestedMinutes;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected OvertimeSettlement() {}

    public OvertimeSettlement(
            AppUser owner,
            LocalDate settlementDate,
            int requestedMinutes,
            String reason
    ) {
        if (owner == null) {
            throw new IllegalArgumentException(
                    "Settlement requires owner"
            );
        }

        this.owner = owner;
        update(
                settlementDate,
                requestedMinutes,
                reason
        );
    }

    public void update(
            LocalDate settlementDate,
            int requestedMinutes,
            String reason
    ) {
        if (settlementDate == null) {
            throw new IllegalArgumentException(
                    "Settlement date is required"
            );
        }

        if (requestedMinutes <= 0
                || requestedMinutes > 6000) {
            throw new IllegalArgumentException(
                    "Settlement must contain 1..6000 minutes"
            );
        }

        this.settlementDate = settlementDate;
        this.requestedMinutes = requestedMinutes;
        this.reason =
                reason == null || reason.isBlank()
                        ? null
                        : reason.trim();
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public AppUser getOwner() {
        return owner;
    }

    public LocalDate getSettlementDate() {
        return settlementDate;
    }

    public int getRequestedMinutes() {
        return requestedMinutes;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
