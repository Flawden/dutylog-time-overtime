package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Locale;

/** Per-user money rules used by Payroll Foundation. Monetary values use minor currency units. */
@Entity
@Table(name = "payroll_settings", uniqueConstraints =
        @UniqueConstraint(name = "uq_payroll_settings_user", columnNames = "user_id"))
public class PayrollSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "RUB";

    @Column(name = "hourly_rate_minor", nullable = false)
    private long hourlyRateMinor;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected PayrollSettings() {}

    public PayrollSettings(AppUser owner) {
        this.owner = owner;
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public String getCurrencyCode() { return currencyCode; }
    public long getHourlyRateMinor() { return hourlyRateMinor; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String currencyCode, long hourlyRateMinor) {
        this.currencyCode = normalizeCurrency(currencyCode);
        this.hourlyRateMinor = Math.max(0L, hourlyRateMinor);
        this.updatedAt = Instant.now();
    }

    @PrePersist
    @PreUpdate
    void normalize() {
        currencyCode = normalizeCurrency(currencyCode);
        hourlyRateMinor = Math.max(0L, hourlyRateMinor);
        if (updatedAt == null) updatedAt = Instant.now();
    }

    private static String normalizeCurrency(String value) {
        String currency = value == null || value.isBlank() ? "RUB" : value.trim().toUpperCase(Locale.ROOT);
        return currency.matches("[A-Z]{3}") ? currency : "RUB";
    }
}
