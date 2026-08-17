package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;

/** Effective-month compensation input. One row becomes active from its month until replaced. */
@Entity
@Table(name = "compensation_terms",
        uniqueConstraints = @UniqueConstraint(name = "uq_compensation_term_owner_month", columnNames = {"user_id", "effective_from"}),
        indexes = @Index(name = "idx_compensation_terms_owner_effective", columnList = "user_id, effective_from"))
public class CompensationTerm {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "pay_mode", nullable = false, length = 16)
    private String payMode;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "hourly_rate_minor")
    private Long hourlyRateMinor;

    @Column(name = "monthly_salary_minor")
    private Long monthlySalaryMinor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected CompensationTerm() {}

    public CompensationTerm(AppUser owner, LocalDate effectiveFrom) {
        this.owner = owner;
        this.effectiveFrom = effectiveFrom.withDayOfMonth(1);
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public String getPayMode() { return payMode; }
    public String getCurrencyCode() { return currencyCode; }
    public Long getHourlyRateMinor() { return hourlyRateMinor; }
    public Long getMonthlySalaryMinor() { return monthlySalaryMinor; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String payMode, String currencyCode, Long hourlyRateMinor, Long monthlySalaryMinor) {
        this.payMode = payMode == null ? "" : payMode.trim().toUpperCase(Locale.ROOT);
        this.currencyCode = currencyCode == null ? "" : currencyCode.trim().toUpperCase(Locale.ROOT);
        this.hourlyRateMinor = hourlyRateMinor;
        this.monthlySalaryMinor = monthlySalaryMinor;
        this.updatedAt = Instant.now();
    }
}
