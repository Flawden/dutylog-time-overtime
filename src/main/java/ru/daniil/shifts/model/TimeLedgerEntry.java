package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/** Append-only audit entry for reservations, postings, reversals and corrections. */
@Entity
@Table(name = "time_ledger_entries", indexes = {
        @Index(name = "idx_time_ledger_entries_owner_effective", columnList = "user_id, effective_date, id"),
        @Index(name = "idx_time_ledger_entries_source", columnList = "user_id, source_kind, source_id, id")
})
public class TimeLedgerEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(name = "entry_kind", nullable = false, length = 40)
    private String entryKind;

    @Column(name = "source_kind", nullable = false, length = 30)
    private String sourceKind;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "signed_minutes", nullable = false)
    private int signedMinutes;

    @Column(name = "posting_state", nullable = false, length = 20)
    private String postingState;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversal_of_id")
    private TimeLedgerEntry reversalOf;

    @Column(length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected TimeLedgerEntry() {}

    public TimeLedgerEntry(AppUser owner, String entryKind, String sourceKind, Long sourceId,
                           LocalDate effectiveDate, int signedMinutes, String postingState,
                           TimeLedgerEntry reversalOf, String reason) {
        this.owner = owner;
        this.entryKind = entryKind;
        this.sourceKind = sourceKind;
        this.sourceId = sourceId;
        this.effectiveDate = effectiveDate;
        this.signedMinutes = signedMinutes;
        this.postingState = postingState;
        this.reversalOf = reversalOf;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public String getEntryKind() { return entryKind; }
    public String getSourceKind() { return sourceKind; }
    public Long getSourceId() { return sourceId; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public int getSignedMinutes() { return signedMinutes; }
    public String getPostingState() { return postingState; }
    public TimeLedgerEntry getReversalOf() { return reversalOf; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
