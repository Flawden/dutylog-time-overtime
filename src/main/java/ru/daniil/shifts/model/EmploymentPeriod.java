package ru.daniil.shifts.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Explicit factual employment relationship interval.
 *
 * <p>This entity is intentionally independent from account registration,
 * compensation configuration, schedules, factual work and Payroll snapshots.
 * None of those signals proves an employment boundary.</p>
 *
 * <p>The interval is inclusive. A null end date means the employment
 * relationship remains open-ended.</p>
 */
@Entity
@Table(
        name = "employment_periods",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_employment_period_owner_start",
                columnNames = {
                        "user_id",
                        "start_date"
                }
        ),
        indexes = @Index(
                name = "idx_employment_periods_owner_dates",
                columnList = "user_id,start_date,end_date,id"
        )
)
public class EmploymentPeriod {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "user_id",
            nullable = false
    )
    private AppUser owner;

    @Column(
            name = "start_date",
            nullable = false
    )
    private LocalDate startDate;

    @Column(
            name = "end_date"
    )
    private LocalDate endDate;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt =
            Instant.now();

    @Column(
            name = "updated_at",
            nullable = false
    )
    private Instant updatedAt =
            Instant.now();

    protected EmploymentPeriod() {
    }

    public EmploymentPeriod(
            AppUser owner,
            LocalDate startDate,
            LocalDate endDate
    ) {
        this.owner =
                Objects.requireNonNull(
                        owner,
                        "Employment period requires owner"
                );

        applyDates(
                startDate,
                endDate
        );
    }

    public void update(
            LocalDate startDate,
            LocalDate endDate
    ) {
        applyDates(
                startDate,
                endDate
        );

        updatedAt =
                Instant.now();
    }

    private void applyDates(
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (startDate == null) {
            throw new IllegalArgumentException(
                    "Employment period start date is required"
            );
        }

        if (endDate != null
                && endDate.isBefore(
                startDate
        )) {
            throw new IllegalArgumentException(
                    "Employment period end date must not precede start date"
            );
        }

        this.startDate =
                startDate;

        this.endDate =
                endDate;
    }

    @PrePersist
    void create() {
        validate();

        Instant now =
                Instant.now();

        createdAt =
                createdAt == null
                        ? now
                        : createdAt;

        updatedAt =
                now;
    }

    @PreUpdate
    void updateTimestamp() {
        validate();

        updatedAt =
                Instant.now();
    }

    private void validate() {
        if (owner == null) {
            throw new IllegalStateException(
                    "Employment period owner is required"
            );
        }

        if (startDate == null) {
            throw new IllegalStateException(
                    "Employment period start date is required"
            );
        }

        if (endDate != null
                && endDate.isBefore(
                startDate
        )) {
            throw new IllegalStateException(
                    "Employment period dates are invalid"
            );
        }
    }

    public Long getId() {
        return id;
    }

    public AppUser getOwner() {
        return owner;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
