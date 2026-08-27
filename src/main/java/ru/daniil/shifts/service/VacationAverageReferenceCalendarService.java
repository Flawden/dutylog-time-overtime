package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.ProductionCalendarDayDto;
import ru.daniil.shifts.model.AppUser;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Assembles exact primary-reference calendar facts for vacation average
 * earnings.
 *
 * <p>The pipeline is deliberately explicit:</p>
 *
 * <pre>
 * employment coverage
 * + posted absence facts
 * + average-earnings legal classification
 * + effective production-calendar HOLIDAY facts for annual vacation
 * -> countable calendar days by reference month
 * -> statutory paragraph-10 denominator
 * </pre>
 *
 * <p>This layer does not calculate the earnings numerator, premium allocation,
 * average daily money or vacation-pay money.</p>
 *
 * <p>Vacation balance count mode is deliberately irrelevant here. A stored
 * vacation period is a factual absence span. Federal non-working holidays
 * listed by Article 112 of the Russian Labour Code are a legal baseline that
 * cannot be erased by a local production-calendar override. An effective
 * production-calendar HOLIDAY may additionally preserve an explicitly
 * configured non-working holiday. Weekends are not silently removed from
 * annual vacation.</p>
 */
@Service
public class VacationAverageReferenceCalendarService {

    private static final String FULL_DAY =
            "FULL_DAY";

    private static final String VACATION =
            "VACATION";

    private static final String HOLIDAY =
            "HOLIDAY";

    /*
     * Federal non-working holidays from Article 112 of the Russian Labour
     * Code. Government transfers move days off, not the legal holiday itself,
     * so transferred days are deliberately absent from this set.
     */
    private static final Set<MonthDay> FEDERAL_NON_WORKING_HOLIDAYS =
            Set.of(
                    MonthDay.of(1, 1),
                    MonthDay.of(1, 2),
                    MonthDay.of(1, 3),
                    MonthDay.of(1, 4),
                    MonthDay.of(1, 5),
                    MonthDay.of(1, 6),
                    MonthDay.of(1, 7),
                    MonthDay.of(1, 8),
                    MonthDay.of(2, 23),
                    MonthDay.of(3, 8),
                    MonthDay.of(5, 1),
                    MonthDay.of(5, 9),
                    MonthDay.of(6, 12),
                    MonthDay.of(11, 4)
            );

    private final EmploymentHistoryService employment;
    private final AverageEarningsReferenceFactsService referenceFacts;
    private final ProductionCalendarService productionCalendar;

    public VacationAverageReferenceCalendarService(
            EmploymentHistoryService employment,
            AverageEarningsReferenceFactsService referenceFacts,
            ProductionCalendarService productionCalendar
    ) {
        this.employment =
                Objects.requireNonNull(
                        employment,
                        "Employment history service is required"
                );

        this.referenceFacts =
                Objects.requireNonNull(
                        referenceFacts,
                        "Average earnings reference facts service is required"
                );

        this.productionCalendar =
                Objects.requireNonNull(
                        productionCalendar,
                        "Production calendar service is required"
                );
    }

    @Transactional(readOnly = true)
    public Result resolve(
            AppUser user,
            LocalDate eventDate
    ) {
        Objects.requireNonNull(
                user,
                "Vacation average reference calendar requires user"
        );

        Objects.requireNonNull(
                eventDate,
                "Vacation average reference calendar requires event date"
        );

        AverageEarningsLegalPolicy
                .requireRegime(
                        eventDate
                );

        YearMonth eventMonth =
                YearMonth.from(
                        eventDate
                );

        LocalDate referenceFrom =
                eventMonth
                        .minusMonths(
                                12
                        )
                        .atDay(
                                1
                        );

        LocalDate referenceTo =
                eventMonth
                        .minusMonths(
                                1
                        )
                        .atEndOfMonth();

        EmploymentHistoryService.Resolution employmentResolution =
                Objects.requireNonNull(
                        employment.resolve(
                                user,
                                referenceFrom,
                                referenceTo
                        ),
                        "Employment history resolution is required"
                );

        validateEmploymentResolution(
                employmentResolution,
                referenceFrom,
                referenceTo
        );

        if (!employmentResolution.ready()) {
            throw new IllegalStateException(
                    "Average earnings employment history is unconfigured"
            );
        }

        Set<LocalDate> employedDates =
                employmentDates(
                        employmentResolution,
                        referenceFrom,
                        referenceTo
                );

        AverageEarningsReferenceFactsService.ReferenceFacts facts =
                Objects.requireNonNull(
                        referenceFacts.resolve(
                                user,
                                eventMonth
                        ),
                        "Average earnings reference facts are required"
                );

        validateReferenceFacts(
                facts,
                eventMonth,
                referenceFrom,
                referenceTo
        );

        Set<LocalDate> excludedDates =
                new TreeSet<>();

        Set<LocalDate> retainedVacationHolidayDates =
                new TreeSet<>();

        for (AverageEarningsReferenceFactsService.AbsenceFact fact
                : facts.absences()) {

            if (fact == null) {
                throw new IllegalStateException(
                        "Average earnings reference facts contain null absence"
                );
            }

            validateAbsenceBoundary(
                    fact,
                    referenceFrom,
                    referenceTo
            );

            List<LocalDate> absenceDates =
                    inclusiveDates(
                            fact.overlapFrom(),
                            fact.overlapTo()
                    );

            /*
             * Explicit employment history is authoritative.
             * A posted absence outside that history is contradictory source
             * data and must not be silently clipped away.
             */
            for (LocalDate date
                    : absenceDates) {

                if (!employedDates.contains(
                        date
                )) {
                    throw new IllegalStateException(
                            "Posted average earnings absence lies outside configured employment coverage: periodId="
                                    + fact.periodId()
                                    + ", date="
                                    + date
                    );
                }
            }

            AverageEarningsLegalPolicy.AbsenceDecision decision =
                    AverageEarningsLegalPolicy
                            .classifyAbsence(
                                    eventDate,
                                    fact
                            );

            if (!decision.resolved()) {
                throw new IllegalStateException(
                        "Average earnings absence legal treatment is unresolved: periodId="
                                + fact.periodId()
                );
            }

            /*
             * We currently have exact calendar exclusion provenance only for
             * FULL_DAY facts. PARTIAL and legacy HOURS_ONLY facts preserve
             * minutes, not a legally proven fractional calendar-day mapping.
             */
            if (!FULL_DAY.equals(
                    fact.coverage()
            )) {
                throw new IllegalStateException(
                        "Average earnings calendar exclusion requires FULL_DAY absence: periodId="
                                + fact.periodId()
                                + ", coverage="
                                + fact.coverage()
                );
            }

            if (VACATION.equals(
                    fact.systemCode()
            )) {
                /*
                 * Article 120 boundary:
                 * an effective non-working HOLIDAY inside annual paid leave is
                 * not itself an annual-leave day. Therefore it remains in the
                 * countable calendar span instead of being removed with the
                 * surrounding vacation dates.
                 *
                 * VacationPlanner countMode is intentionally not consulted.
                 */
                for (LocalDate date
                        : absenceDates) {

                    /*
                     * Federal Article-112 holidays are legal truth independent
                     * of local production-calendar overrides.
                     */
                    if (isFederalNonWorkingHoliday(
                            date
                    )) {
                        retainedVacationHolidayDates.add(
                                date
                        );

                        continue;
                    }

                    /*
                     * The production calendar remains an explicit extension
                     * point for additional non-working holiday truth, e.g.
                     * an officially configured regional holiday.
                     */
                    ProductionCalendarDayDto production =
                            productionCalendar
                                    .resolvedDay(
                                            user,
                                            date
                                    );

                    if (production == null
                            || production.dayKind() == null) {
                        throw new IllegalStateException(
                                "Vacation holiday classification is unavailable for "
                                        + date
                        );
                    }

                    if (HOLIDAY.equals(
                            production.dayKind()
                    )) {
                        retainedVacationHolidayDates.add(
                                date
                        );
                    } else {
                        excludedDates.add(
                                date
                        );
                    }
                }

                continue;
            }

            /*
             * SICK / UNPAID / TIME_OFF are already legally classified by
             * AverageEarningsLegalPolicy. Their FULL_DAY factual spans are
             * excluded here without schedule-minute inference.
             *
             * Set semantics intentionally union defensive historical overlaps.
             */
            excludedDates.addAll(
                    absenceDates
            );
        }

        List<VacationAverageCalendarDenominator.MonthFact> monthFacts =
                monthFacts(
                        eventMonth,
                        employedDates,
                        excludedDates
                );

        VacationAverageCalendarDenominator.Result denominator =
                VacationAverageCalendarDenominator
                        .primary(
                                eventDate,
                                eventMonth,
                                monthFacts
                        );

        return new Result(
                eventDate,
                eventMonth,
                referenceFrom,
                referenceTo,
                monthFacts,
                new ArrayList<>(
                        excludedDates
                ),
                new ArrayList<>(
                        retainedVacationHolidayDates
                ),
                denominator
        );
    }

    private boolean isFederalNonWorkingHoliday(
            LocalDate date
    ) {
        Objects.requireNonNull(
                date,
                "Federal holiday classification requires date"
        );

        return FEDERAL_NON_WORKING_HOLIDAYS.contains(
                MonthDay.from(
                        date
                )
        );
    }

    private void validateEmploymentResolution(
            EmploymentHistoryService.Resolution resolution,
            LocalDate expectedFrom,
            LocalDate expectedTo
    ) {
        if (!expectedFrom.equals(
                resolution.from()
        ) || !expectedTo.equals(
                resolution.to()
        )) {
            throw new IllegalStateException(
                    "Employment history reference boundary mismatch"
            );
        }
    }

    private Set<LocalDate> employmentDates(
            EmploymentHistoryService.Resolution resolution,
            LocalDate referenceFrom,
            LocalDate referenceTo
    ) {
        Set<LocalDate> result =
                new LinkedHashSet<>();

        for (EmploymentHistoryService.CoverageSlice slice
                : resolution.slices()) {

            if (slice == null
                    || slice.overlapFrom() == null
                    || slice.overlapTo() == null
                    || slice.overlapTo().isBefore(
                    slice.overlapFrom()
            )) {
                throw new IllegalStateException(
                        "Employment coverage slice is invalid"
                );
            }

            if (slice.overlapFrom().isBefore(
                    referenceFrom
            ) || slice.overlapTo().isAfter(
                    referenceTo
            )) {
                throw new IllegalStateException(
                        "Employment coverage slice exceeds reference boundary"
                );
            }

            for (LocalDate date
                    : inclusiveDates(
                    slice.overlapFrom(),
                    slice.overlapTo()
            )) {

                if (!result.add(
                        date
                )) {
                    throw new IllegalStateException(
                            "Employment coverage slices overlap at "
                                    + date
                    );
                }
            }
        }

        return result;
    }

    private void validateReferenceFacts(
            AverageEarningsReferenceFactsService.ReferenceFacts facts,
            YearMonth eventMonth,
            LocalDate referenceFrom,
            LocalDate referenceTo
    ) {
        if (!eventMonth.equals(
                facts.eventMonth()
        ) || !referenceFrom.equals(
                facts.referenceFrom()
        ) || !referenceTo.equals(
                facts.referenceTo()
        )) {
            throw new IllegalStateException(
                    "Average earnings reference facts boundary mismatch"
            );
        }
    }

    private void validateAbsenceBoundary(
            AverageEarningsReferenceFactsService.AbsenceFact fact,
            LocalDate referenceFrom,
            LocalDate referenceTo
    ) {
        if (fact.periodId() == null
                || fact.overlapFrom() == null
                || fact.overlapTo() == null
                || fact.overlapTo().isBefore(
                fact.overlapFrom()
        )) {
            throw new IllegalStateException(
                    "Average earnings absence boundary is invalid"
            );
        }

        if (fact.overlapFrom().isBefore(
                referenceFrom
        ) || fact.overlapTo().isAfter(
                referenceTo
        )) {
            throw new IllegalStateException(
                    "Average earnings absence exceeds reference boundary"
            );
        }
    }

    private List<VacationAverageCalendarDenominator.MonthFact> monthFacts(
            YearMonth eventMonth,
            Set<LocalDate> employedDates,
            Set<LocalDate> excludedDates
    ) {
        List<VacationAverageCalendarDenominator.MonthFact> result =
                new ArrayList<>(
                        12
                );

        YearMonth referenceMonth =
                eventMonth.minusMonths(
                        12
                );

        for (int offset = 0;
                offset < 12;
                offset++) {

            YearMonth month =
                    referenceMonth.plusMonths(
                            offset
                    );

            int countable =
                    0;

            for (LocalDate date =
                    month.atDay(
                            1
                    );
                    !date.isAfter(
                            month.atEndOfMonth()
                    );
                    date = date.plusDays(
                            1
                    )) {

                if (employedDates.contains(
                        date
                ) && !excludedDates.contains(
                        date
                )) {
                    countable++;
                }
            }

            result.add(
                    new VacationAverageCalendarDenominator.MonthFact(
                            month,
                            countable
                    )
            );
        }

        return List.copyOf(
                result
        );
    }

    private List<LocalDate> inclusiveDates(
            LocalDate from,
            LocalDate to
    ) {
        if (from == null
                || to == null
                || to.isBefore(
                from
        )) {
            throw new IllegalArgumentException(
                    "Inclusive calendar range is invalid"
            );
        }

        List<LocalDate> result =
                new ArrayList<>();

        for (LocalDate date =
                from;
                !date.isAfter(
                        to
                );
                date = date.plusDays(
                        1
                )) {

            result.add(
                    date
            );
        }

        return result;
    }

    public record Result(
            LocalDate eventDate,
            YearMonth eventMonth,
            LocalDate referenceFrom,
            LocalDate referenceTo,
            List<VacationAverageCalendarDenominator.MonthFact> months,
            List<LocalDate> excludedDates,
            List<LocalDate> retainedVacationHolidayDates,
            VacationAverageCalendarDenominator.Result denominator
    ) {
        public Result {
            Objects.requireNonNull(
                    eventDate,
                    "Event date is required"
            );

            Objects.requireNonNull(
                    eventMonth,
                    "Event month is required"
            );

            Objects.requireNonNull(
                    referenceFrom,
                    "Reference start is required"
            );

            Objects.requireNonNull(
                    referenceTo,
                    "Reference end is required"
            );

            months = List.copyOf(
                    Objects.requireNonNull(
                            months,
                            "Month facts are required"
                    )
            );

            excludedDates = List.copyOf(
                    Objects.requireNonNull(
                            excludedDates,
                            "Excluded calendar dates are required"
                    )
            );

            retainedVacationHolidayDates = List.copyOf(
                    Objects.requireNonNull(
                            retainedVacationHolidayDates,
                            "Retained vacation holiday dates are required"
                    )
            );

            Objects.requireNonNull(
                    denominator,
                    "Vacation average denominator is required"
            );

            if (months.size() != 12) {
                throw new IllegalArgumentException(
                        "Vacation average reference calendar requires 12 month facts"
                );
            }
        }
    }
}
