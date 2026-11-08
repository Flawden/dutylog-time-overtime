package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Immutable event attached to an incoming receipt.
 *
 * VOID is an accounting cancellation and has no cash amount/date.
 * REFUND is an actual outgoing cash transfer and therefore has an amount/date.
 * A VOID may link a replacement receipt when it is part of a correction.
 */
@Entity
@Table(name = "vacation_pay_receipt_adjustments")
public class VacationPayReceiptAdjustment {

    public static final String TYPE_VOID = "VOID";
    public static final String TYPE_REFUND = "REFUND";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private AppUser owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id", nullable = false, updatable = false)
    private VacationPayReceipt receipt;

    @Column(name = "adjustment_type", nullable = false, length = 12, updatable = false)
    private String adjustmentType;

    @Column(name = "amount_minor", updatable = false)
    private Long amountMinor;

    @Column(name = "currency_code", length = 3, updatable = false)
    private String currencyCode;

    @Column(name = "event_date", updatable = false)
    private LocalDate eventDate;

    @Column(name = "reason", nullable = false, length = 500, updatable = false)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replacement_receipt_id", updatable = false)
    private VacationPayReceipt replacementReceipt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected VacationPayReceiptAdjustment() {
    }

    private VacationPayReceiptAdjustment(
            AppUser owner,
            VacationPayReceipt receipt,
            String adjustmentType,
            Long amountMinor,
            String currencyCode,
            LocalDate eventDate,
            String reason,
            VacationPayReceipt replacementReceipt
    ) {
        this.owner = Objects.requireNonNull(owner, "Adjustment owner is required");
        this.receipt = Objects.requireNonNull(receipt, "Adjustment receipt is required");
        this.adjustmentType = requireText(adjustmentType, "Adjustment type is required");
        this.reason = requireText(reason, "Adjustment reason is required");
        if (!Objects.equals(owner.getId(), receipt.getOwner().getId())) {
            throw new IllegalArgumentException("Adjustment owner must own the original receipt");
        }
        if (this.reason.length() > 500) {
            throw new IllegalArgumentException("Adjustment reason must not exceed 500 characters");
        }

        if (TYPE_VOID.equals(adjustmentType)) {
            if (amountMinor != null || currencyCode != null || eventDate != null) {
                throw new IllegalArgumentException("A VOID event must not contain cash movement fields");
            }
            if (replacementReceipt != null
                    && (!Objects.equals(replacementReceipt.getOwner().getId(), owner.getId())
                    || !Objects.equals(
                            replacementReceipt.getAbsencePeriod().getId(),
                            receipt.getAbsencePeriod().getId()
                    )
                    || receipt.getId() == null
                    || replacementReceipt.getId() == null
                    || replacementReceipt.getId() <= receipt.getId())) {
                throw new IllegalArgumentException(
                        "Replacement receipt must be a later transfer for the same owner and vacation"
                );
            }
        } else if (TYPE_REFUND.equals(adjustmentType)) {
            if (amountMinor == null || amountMinor <= 0L
                    || currencyCode == null || !currencyCode.matches("[A-Z]{3}")
                    || eventDate == null || replacementReceipt != null) {
                throw new IllegalArgumentException("A REFUND event requires amount, currency and date only");
            }
            if (!currencyCode.equals(receipt.getCurrencyCode())) {
                throw new IllegalArgumentException("Refund currency must match the original receipt currency");
            }
        } else {
            throw new IllegalArgumentException("Unsupported receipt adjustment type");
        }

        this.amountMinor = amountMinor;
        this.currencyCode = currencyCode;
        this.eventDate = eventDate;
        this.replacementReceipt = replacementReceipt;
        this.createdAt = Instant.now();
    }

    public static VacationPayReceiptAdjustment voidReceipt(
            AppUser owner,
            VacationPayReceipt receipt,
            String reason,
            VacationPayReceipt replacementReceipt
    ) {
        return new VacationPayReceiptAdjustment(
                owner, receipt, TYPE_VOID, null, null, null, reason, replacementReceipt
        );
    }

    public static VacationPayReceiptAdjustment refund(
            AppUser owner,
            VacationPayReceipt receipt,
            long amountMinor,
            LocalDate eventDate,
            String reason
    ) {
        return new VacationPayReceiptAdjustment(
                owner, receipt, TYPE_REFUND, amountMinor,
                receipt.getCurrencyCode(), eventDate, reason, null
        );
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public VacationPayReceipt getReceipt() { return receipt; }
    public String getAdjustmentType() { return adjustmentType; }
    public Long getAmountMinor() { return amountMinor; }
    public String getCurrencyCode() { return currencyCode; }
    public LocalDate getEventDate() { return eventDate; }
    public String getReason() { return reason; }
    public VacationPayReceipt getReplacementReceipt() { return replacementReceipt; }
    public Instant getCreatedAt() { return createdAt; }
}
