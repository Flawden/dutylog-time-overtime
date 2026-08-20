package ru.daniil.shifts.model;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Homogeneous factual provenance below one SYSTEM_ACTUAL_WORK credit.
 *
 * A slice stores classification truth only:
 * source fact, source clock/instant identity and NIGHT/HOLIDAY dimensions.
 *
 * It deliberately stores no multiplier, pricing tier or money.
 */
@Entity
@Table(
        name = "overtime_credit_slices",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_overtime_credit_slice_offset",
                columnNames = {"credit_id", "offset_start_minutes"}
        ),
        indexes = {
                @Index(
                        name = "idx_overtime_credit_slices_credit_offset",
                        columnList = "credit_id, offset_start_minutes, id"
                ),
                @Index(
                        name = "idx_overtime_credit_slices_source_actual",
                        columnList = "source_actual_work_interval_id, id"
                )
        }
)
public class OvertimeCreditSlice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credit_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private OvertimeCredit credit;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "source_actual_work_interval_id",
            nullable = false
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ActualWorkInterval sourceActualWorkInterval;

    /**
     * Offset inside the fungible credit itself.
     *
     * Example:
     * credit = 120 minutes
     * slice A = offset 0,   minutes 60
     * slice B = offset 60,  minutes 60
     */
    @Column(name = "offset_start_minutes", nullable = false)
    private int offsetStartMinutes;

    @Column(nullable = false)
    private int minutes;

    @Column(name = "source_date", nullable = false)
    private LocalDate sourceDate;

    /**
     * Historical source-local clock identity.
     *
     * Local boundaries always exist. Absolute fields are either all present
     * together or all absent for unreconstructed legacy facts.
     */
    @Column(name = "source_start_at", nullable = false)
    private LocalDateTime sourceStartAt;

    @Column(name = "source_end_at", nullable = false)
    private LocalDateTime sourceEndAt;

    @Column(name = "source_start_instant")
    private Instant sourceStartInstant;

    @Column(name = "source_end_instant")
    private Instant sourceEndInstant;

    @Column(name = "source_timezone", length = 80)
    private String sourceTimezone;

    /** Independent classification dimensions. */
    @Column(nullable = false)
    private boolean night;

    @Column(nullable = false)
    private boolean holiday;

    /**
     * Worked-minute ordinal where this overtime slice starts.
     *
     * Example: an 8h ordinary threshold means the first overtime minute has
     * overtimeOrdinalStartMinutes = 480.
     */
    @Column(
            name = "overtime_ordinal_start_minutes",
            nullable = false
    )
    private int overtimeOrdinalStartMinutes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected OvertimeCreditSlice() {}

    public OvertimeCreditSlice(
            OvertimeCredit credit,
            int offsetStartMinutes,
            int minutes,
            ActualWorkInterval sourceActualWorkInterval,
            LocalDate sourceDate,
            LocalDateTime sourceStartAt,
            LocalDateTime sourceEndAt,
            Instant sourceStartInstant,
            Instant sourceEndInstant,
            String sourceTimezone,
            boolean night,
            boolean holiday,
            int overtimeOrdinalStartMinutes
    ) {
        if (credit == null) {
            throw new IllegalArgumentException(
                    "Provenance slice requires overtime credit"
            );
        }

        if (!credit.isSystemActualWorkDerived()) {
            throw new IllegalArgumentException(
                    "Provenance slices belong only to SYSTEM_ACTUAL_WORK credits"
            );
        }

        if (sourceActualWorkInterval == null) {
            throw new IllegalArgumentException(
                    "Provenance slice requires Actual Work source"
            );
        }

        if (sourceDate == null
                || sourceStartAt == null
                || sourceEndAt == null) {
            throw new IllegalArgumentException(
                    "Provenance slice requires source-local identity"
            );
        }

        if (offsetStartMinutes < 0) {
            throw new IllegalArgumentException(
                    "Credit slice offset cannot be negative"
            );
        }

        if (minutes <= 0) {
            throw new IllegalArgumentException(
                    "Credit slice must contain positive minutes"
            );
        }

        if (overtimeOrdinalStartMinutes < 0) {
            throw new IllegalArgumentException(
                    "Overtime ordinal cannot be negative"
            );
        }

        if (!sourceDate.equals(sourceStartAt.toLocalDate())) {
            throw new IllegalArgumentException(
                    "Slice source date must match source start"
            );
        }

        if (!sourceDate.equals(
                sourceEndAt.minusNanos(1).toLocalDate()
        )) {
            throw new IllegalArgumentException(
                    "Slice must stay inside one source calendar date"
            );
        }

        if (credit.getWorkDate() != null
                && !credit.getWorkDate().equals(sourceDate)) {
            throw new IllegalArgumentException(
                    "Slice source date must match credit work date"
            );
        }

        if (offsetStartMinutes + minutes
                > credit.getCreditedMinutes()) {
            throw new IllegalArgumentException(
                    "Slice cannot exceed credit minute range"
            );
        }

        boolean anyAbsolute =
                sourceStartInstant != null
                        || sourceEndInstant != null
                        || sourceTimezone != null;

        if (anyAbsolute) {
            if (sourceStartInstant == null
                    || sourceEndInstant == null
                    || sourceTimezone == null
                    || sourceTimezone.isBlank()
                    || !sourceEndInstant.isAfter(
                            sourceStartInstant
                    )) {
                throw new IllegalArgumentException(
                        "Exact provenance requires complete absolute identity"
                );
            }

            int elapsed = Math.toIntExact(
                    Duration.between(
                            sourceStartInstant,
                            sourceEndInstant
                    ).toMinutes()
            );

            if (elapsed != minutes) {
                throw new IllegalArgumentException(
                        "Exact provenance duration must match slice minutes"
                );
            }
        } else {
            if (!sourceEndAt.isAfter(sourceStartAt)) {
                throw new IllegalArgumentException(
                        "Legacy provenance requires positive local duration"
                );
            }

            int elapsed = Math.toIntExact(
                    Duration.between(
                            sourceStartAt,
                            sourceEndAt
                    ).toMinutes()
            );

            if (elapsed != minutes) {
                throw new IllegalArgumentException(
                        "Legacy provenance duration must match slice minutes"
                );
            }
        }

        this.credit = credit;
        this.offsetStartMinutes = offsetStartMinutes;
        this.minutes = minutes;
        this.sourceActualWorkInterval =
                sourceActualWorkInterval;
        this.sourceDate = sourceDate;
        this.sourceStartAt = sourceStartAt;
        this.sourceEndAt = sourceEndAt;
        this.sourceStartInstant = sourceStartInstant;
        this.sourceEndInstant = sourceEndInstant;
        this.sourceTimezone =
                sourceTimezone == null
                        ? null
                        : sourceTimezone.trim();
        this.night = night;
        this.holiday = holiday;
        this.overtimeOrdinalStartMinutes =
                overtimeOrdinalStartMinutes;
    }

    public Long getId() {
        return id;
    }

    public OvertimeCredit getCredit() {
        return credit;
    }

    public ActualWorkInterval getSourceActualWorkInterval() {
        return sourceActualWorkInterval;
    }

    public int getOffsetStartMinutes() {
        return offsetStartMinutes;
    }

    public int getMinutes() {
        return minutes;
    }

    public LocalDate getSourceDate() {
        return sourceDate;
    }

    public LocalDateTime getSourceStartAt() {
        return sourceStartAt;
    }

    public LocalDateTime getSourceEndAt() {
        return sourceEndAt;
    }

    public Instant getSourceStartInstant() {
        return sourceStartInstant;
    }

    public Instant getSourceEndInstant() {
        return sourceEndInstant;
    }

    public String getSourceTimezone() {
        return sourceTimezone;
    }

    public boolean isNight() {
        return night;
    }

    public boolean isHoliday() {
        return holiday;
    }

    public int getOvertimeOrdinalStartMinutes() {
        return overtimeOrdinalStartMinutes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean exact() {
        return sourceStartInstant != null
                && sourceEndInstant != null
                && sourceTimezone != null;
    }
}
