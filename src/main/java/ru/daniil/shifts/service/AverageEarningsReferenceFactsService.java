package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AbsencePeriod;
import ru.daniil.shifts.model.AbsenceType;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.AbsencePeriodRepository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Read-only factual absence side of the primary average-earnings
 * reference period.
 *
 * <p>This service deliberately stops before legal average-earnings policy:
 * it does not decide whether an absence is excluded, does not select eligible
 * earnings, does not calculate 29.3-based quantities and does not calculate
 * money.</p>
 *
 * <p>Historical earning money is owned separately by
 * {@link PayrollHistoricalSemanticEarningsService}. This source preserves
 * only machine-owned absence facts that future average-earnings policy may
 * classify.</p>
 */
@Service
public class AverageEarningsReferenceFactsService {

    private static final String FULL_DAY = "FULL_DAY";
    private static final String PARTIAL = "PARTIAL";
    private static final String HOURS_ONLY = "HOURS_ONLY";

    private final AbsencePeriodRepository absences;
    private final LedgerIntegrityService ledgerIntegrity;

    public AverageEarningsReferenceFactsService(
            AbsencePeriodRepository absences,
            LedgerIntegrityService ledgerIntegrity
    ) {
        this.absences = Objects.requireNonNull(
                absences,
                "Absence repository is required"
        );
        this.ledgerIntegrity = Objects.requireNonNull(
                ledgerIntegrity,
                "Ledger integrity service is required"
        );
    }

    /**
     * Primary reference window: the twelve calendar months immediately
     * preceding the event month.
     *
     * <p>Only posted factual absences are returned. Reserved absences are not
     * historical payroll facts and therefore never enter this source.</p>
     */
    @Transactional(readOnly = true)
    public ReferenceFacts resolve(
            AppUser user,
            YearMonth eventMonth
    ) {
        return resolve(
                user,
                AverageEarningsReferenceWindow.primary(eventMonth)
        );
    }

    @Transactional(readOnly = true)
    public ReferenceFacts resolve(
            AppUser user,
            AverageEarningsReferenceWindow referenceWindow
    ) {
        Objects.requireNonNull(
                user,
                "Average earnings reference facts require user"
        );
        Objects.requireNonNull(
                referenceWindow,
                "Average earnings reference facts require reference window"
        );

        return resolveRange(
                user,
                referenceWindow.eventMonth(),
                referenceWindow.referenceFromDate(),
                referenceWindow.referenceToDate()
        );
    }

    /**
     * Exact inclusive factual range used by non-reference-period authorities.
     *
     * <p>This method is still FACT-only. The caller owns legal classification
     * and any denominator/formula policy. It exists so paragraph-7 can inspect
     * only the event-month days strictly before the event without pretending
     * that a partial month is a twelve-month reference window.</p>
     */
    @Transactional(readOnly = true)
    public ReferenceFacts resolveRange(
            AppUser user,
            YearMonth eventMonth,
            LocalDate referenceFrom,
            LocalDate referenceTo
    ) {
        Objects.requireNonNull(
                user,
                "Average earnings reference facts require user"
        );
        Objects.requireNonNull(
                eventMonth,
                "Average earnings reference facts require event month"
        );
        Objects.requireNonNull(
                referenceFrom,
                "Average earnings reference facts require range start"
        );
        Objects.requireNonNull(
                referenceTo,
                "Average earnings reference facts require range end"
        );
        if (referenceTo.isBefore(referenceFrom)) {
            throw new IllegalArgumentException(
                    "Average earnings factual range is invalid"
            );
        }

        List<AbsencePeriod> rows =
                absences
                        .findByOwnerAndEndDateGreaterThanEqualAndStartDateLessThanEqualOrderByStartDateAscIdAsc(
                                user,
                                referenceFrom,
                                referenceTo
                        );

        if (rows == null) {
            throw new IllegalStateException(
                    "Average earnings absence repository returned null"
            );
        }

        List<AbsenceFact> facts =
                new ArrayList<>();

        for (AbsencePeriod period : rows) {
            if (period == null) {
                throw new IllegalStateException(
                        "Average earnings absence repository returned null row"
                );
            }

            if (!ledgerIntegrity.posts(
                    period.getStatus()
            )) {
                continue;
            }

            facts.add(
                    toFact(
                            period,
                            referenceFrom,
                            referenceTo
                    )
            );
        }

        return new ReferenceFacts(
                eventMonth,
                referenceFrom,
                referenceTo,
                facts
        );
    }

    private AbsenceFact toFact(
            AbsencePeriod period,
            LocalDate referenceFrom,
            LocalDate referenceTo
    ) {
        Long periodId =
                period.getId();

        if (periodId == null) {
            throw new IllegalStateException(
                    "Posted average earnings absence must have persistent identity"
            );
        }

        AbsenceType type =
                period.getType();

        if (type == null) {
            throw new IllegalStateException(
                    "Posted average earnings absence must have type"
            );
        }

        LocalDate sourceFrom =
                period.getStartDate();

        LocalDate sourceTo =
                period.getEndDate();

        if (sourceFrom == null
                || sourceTo == null
                || sourceTo.isBefore(sourceFrom)) {
            throw new IllegalStateException(
                    "Posted average earnings absence has invalid source period"
            );
        }

        LocalDate overlapFrom =
                sourceFrom.isAfter(referenceFrom)
                        ? sourceFrom
                        : referenceFrom;

        LocalDate overlapTo =
                sourceTo.isBefore(referenceTo)
                        ? sourceTo
                        : referenceTo;

        if (overlapTo.isBefore(overlapFrom)) {
            throw new IllegalStateException(
                    "Posted average earnings absence does not intersect reference period"
            );
        }

        String coverage =
                period.getCoverage();

        Integer knownMinutes =
                knownMinutes(
                        period,
                        coverage
                );

        return new AbsenceFact(
                periodId,
                type.getSystemCode(),
                type.getBalancePolicy(),
                period.getCompensationPolicy(),
                ledgerIntegrity.normalizeStatus(
                        period.getStatus()
                ),
                coverage,
                sourceFrom,
                sourceTo,
                overlapFrom,
                overlapTo,
                period.getStartTime(),
                period.getEndTime(),
                period.getChargedMinutes(),
                period.getCompensatedMinutes(),
                knownMinutes
        );
    }

    private Integer knownMinutes(
            AbsencePeriod period,
            String coverage
    ) {
        if (FULL_DAY.equals(coverage)) {
            /*
             * A full-day absence is a calendar fact.
             * Never invent payroll minutes from a schedule here.
             */
            return null;
        }

        if (PARTIAL.equals(coverage)) {
            LocalTime start =
                    period.getStartTime();

            LocalTime end =
                    period.getEndTime();

            if (start == null
                    || end == null
                    || !end.isAfter(start)) {
                throw new IllegalStateException(
                        "Posted PARTIAL absence lacks exact positive local interval"
                );
            }

            return Math.toIntExact(
                    Duration
                            .between(
                                    start,
                                    end
                            )
                            .toMinutes()
            );
        }

        if (HOURS_ONLY.equals(coverage)) {
            int charged =
                    period.getChargedMinutes();

            if (charged <= 0) {
                throw new IllegalStateException(
                        "Posted HOURS_ONLY absence lacks exact positive stored minutes"
                );
            }

            return charged;
        }

        throw new IllegalStateException(
                "Posted average earnings absence has unsupported coverage: "
                        + coverage
        );
    }

    public record ReferenceFacts(
            YearMonth eventMonth,
            LocalDate referenceFrom,
            LocalDate referenceTo,
            List<AbsenceFact> absences
    ) {
        public ReferenceFacts {
            Objects.requireNonNull(
                    eventMonth,
                    "Event month is required"
            );
            Objects.requireNonNull(
                    referenceFrom,
                    "Reference from is required"
            );
            Objects.requireNonNull(
                    referenceTo,
                    "Reference to is required"
            );

            if (referenceTo.isBefore(
                    referenceFrom
            )) {
                throw new IllegalArgumentException(
                        "Reference period is invalid"
                );
            }

            absences = List.copyOf(
                    Objects.requireNonNull(
                            absences,
                            "Reference absence facts are required"
                    )
            );
        }
    }

    public record AbsenceFact(
            Long periodId,
            String systemCode,
            String balancePolicy,
            String compensationPolicy,
            String status,
            String coverage,
            LocalDate sourceFrom,
            LocalDate sourceTo,
            LocalDate overlapFrom,
            LocalDate overlapTo,
            LocalTime startTime,
            LocalTime endTime,
            int chargedMinutes,
            int compensatedMinutes,
            Integer knownMinutes
    ) {
    }
}
