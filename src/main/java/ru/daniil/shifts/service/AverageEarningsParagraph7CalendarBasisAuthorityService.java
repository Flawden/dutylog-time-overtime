package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Paragraph-7 paragraph-10 calendar-basis authority for vacation average pay.
 *
 * <p>The paragraph-7 wage authority proves money actually accrued before the
 * event. It does not prove the calendar-day quantity required by paragraph 10
 * for an incomplete event month. This service resolves only that missing
 * calendar basis.</p>
 *
 * <p>The authority deliberately reuses the same factual boundaries as the
 * primary vacation reference calendar: explicit employment coverage, posted
 * absence facts, effective average-earnings legal classification and annual
 * vacation holiday retention. Worked shifts are never substituted for
 * calendar days.</p>
 *
 * <p>This layer does not decide whether paragraph 7 applies, does not calculate
 * accrued wage money, average-daily money, payable vacation days or final
 * vacation-pay money.</p>
 */
@Service
public class AverageEarningsParagraph7CalendarBasisAuthorityService {

    public static final String RULE_ID = "PP_540_P7_P10_CALENDAR_BASIS";
    public static final String NO_PRE_EVENT_RANGE =
            "PP_540_P7_P10_NO_PRE_EVENT_RANGE";
    public static final String EMPLOYMENT_HISTORY_UNCONFIGURED =
            "PP_540_P7_P10_EMPLOYMENT_HISTORY_UNCONFIGURED";
    public static final String NO_COUNTABLE_CALENDAR_DAYS =
            "PP_540_P7_P10_NO_COUNTABLE_CALENDAR_DAYS";

    private static final String FULL_DAY = "FULL_DAY";
    private static final String VACATION = "VACATION";

    private final EmploymentHistoryService employment;
    private final AverageEarningsReferenceFactsService referenceFacts;
    private final ProductionCalendarService productionCalendar;

    public AverageEarningsParagraph7CalendarBasisAuthorityService(
            EmploymentHistoryService employment,
            AverageEarningsReferenceFactsService referenceFacts,
            ProductionCalendarService productionCalendar
    ) {
        this.employment = Objects.requireNonNull(
                employment,
                "Paragraph-7 calendar basis requires employment history"
        );
        this.referenceFacts = Objects.requireNonNull(
                referenceFacts,
                "Paragraph-7 calendar basis requires absence facts"
        );
        this.productionCalendar = Objects.requireNonNull(
                productionCalendar,
                "Paragraph-7 calendar basis requires production calendar"
        );
    }

    @Transactional(readOnly = true)
    public Resolution resolve(
            AppUser user,
            LocalDate eventDate
    ) {
        Objects.requireNonNull(
                user,
                "Paragraph-7 calendar basis requires user"
        );
        Objects.requireNonNull(
                eventDate,
                "Paragraph-7 calendar basis requires event date"
        );

        AverageEarningsLegalPolicy.requireRegime(eventDate);

        YearMonth eventMonth = YearMonth.from(eventDate);
        LocalDate periodFrom = eventMonth.atDay(1);
        LocalDate cutoffExclusive = eventDate;

        if (eventDate.equals(periodFrom)) {
            return Resolution.blocked(
                    eventDate,
                    periodFrom,
                    cutoffExclusive,
                    NO_PRE_EVENT_RANGE,
                    "Paragraph-7 calendar basis has no pre-event calendar range"
            );
        }

        LocalDate periodTo = eventDate.minusDays(1);

        EmploymentHistoryService.Resolution employmentResolution =
                Objects.requireNonNull(
                        employment.resolve(
                                user,
                                periodFrom,
                                periodTo
                        ),
                        "Paragraph-7 employment history returned null"
                );

        if (!periodFrom.equals(employmentResolution.from())
                || !periodTo.equals(employmentResolution.to())) {
            throw new IllegalStateException(
                    "Paragraph-7 employment history boundary mismatch"
            );
        }

        if (!employmentResolution.ready()) {
            return Resolution.blocked(
                    eventDate,
                    periodFrom,
                    cutoffExclusive,
                    EMPLOYMENT_HISTORY_UNCONFIGURED,
                    "Paragraph-7 calendar basis requires configured employment history"
            );
        }

        Set<LocalDate> employedDates = employmentDates(
                employmentResolution,
                periodFrom,
                periodTo
        );

        AverageEarningsReferenceFactsService.ReferenceFacts facts =
                Objects.requireNonNull(
                        referenceFacts.resolveRange(
                                user,
                                eventMonth,
                                periodFrom,
                                periodTo
                        ),
                        "Paragraph-7 calendar absence facts returned null"
                );

        validateFacts(
                facts,
                eventMonth,
                periodFrom,
                periodTo
        );

        Set<LocalDate> excludedDates = new TreeSet<>();
        Set<LocalDate> retainedVacationHolidayDates = new TreeSet<>();

        for (AverageEarningsReferenceFactsService.AbsenceFact fact
                : facts.absences()) {
            Objects.requireNonNull(
                    fact,
                    "Paragraph-7 calendar facts contain null absence"
            );

            validateAbsenceBoundary(
                    fact,
                    periodFrom,
                    periodTo
            );

            List<LocalDate> absenceDates = inclusiveDates(
                    fact.overlapFrom(),
                    fact.overlapTo()
            );

            for (LocalDate date : absenceDates) {
                if (!employedDates.contains(date)) {
                    throw new IllegalStateException(
                            "Posted paragraph-7 absence lies outside configured employment coverage: periodId="
                                    + fact.periodId()
                                    + ", date="
                                    + date
                    );
                }
            }

            AverageEarningsLegalPolicy.AbsenceDecision decision =
                    AverageEarningsLegalPolicy.classifyAbsence(
                            eventDate,
                            fact
                    );

            if (!decision.resolved()) {
                throw new IllegalStateException(
                        "Paragraph-7 calendar absence legal treatment is unresolved: periodId="
                                + fact.periodId()
                );
            }

            if (!FULL_DAY.equals(fact.coverage())) {
                throw new IllegalStateException(
                        "Paragraph-7 calendar exclusion requires FULL_DAY absence: periodId="
                                + fact.periodId()
                                + ", coverage="
                                + fact.coverage()
                );
            }

            if (VACATION.equals(fact.systemCode())) {
                for (LocalDate date : absenceDates) {
                    AnnualPaidVacationHolidayPolicy.HolidayFact holidayFact =
                            AnnualPaidVacationHolidayPolicy.classify(
                                    productionCalendar,
                                    user,
                                    date
                            );
                    if (holidayFact.nonWorkingHoliday()) {
                        retainedVacationHolidayDates.add(date);
                    } else {
                        excludedDates.add(date);
                    }
                }
            } else {
                excludedDates.addAll(absenceDates);
            }
        }

        int countableCalendarDays = 0;
        for (LocalDate date : employedDates) {
            if (!excludedDates.contains(date)) {
                countableCalendarDays++;
            }
        }

        if (countableCalendarDays <= 0) {
            return Resolution.blocked(
                    eventDate,
                    periodFrom,
                    cutoffExclusive,
                    NO_COUNTABLE_CALENDAR_DAYS,
                    "Paragraph-7 pre-event range has no countable calendar days"
            );
        }

        VacationAverageUnifiedDailyResolver.Paragraph7CalendarBasis basis =
                VacationAverageUnifiedDailyResolver.Paragraph7CalendarBasis.of(
                        eventDate,
                        countableCalendarDays,
                        RULE_ID
                );

        return Resolution.ready(
                eventDate,
                periodFrom,
                cutoffExclusive,
                countableCalendarDays,
                new ArrayList<>(excludedDates),
                new ArrayList<>(retainedVacationHolidayDates),
                basis
        );
    }

    private Set<LocalDate> employmentDates(
            EmploymentHistoryService.Resolution resolution,
            LocalDate periodFrom,
            LocalDate periodTo
    ) {
        Set<LocalDate> result = new LinkedHashSet<>();

        for (EmploymentHistoryService.CoverageSlice slice : resolution.slices()) {
            if (slice == null
                    || slice.overlapFrom() == null
                    || slice.overlapTo() == null
                    || slice.overlapTo().isBefore(slice.overlapFrom())) {
                throw new IllegalStateException(
                        "Paragraph-7 employment coverage slice is invalid"
                );
            }

            if (slice.overlapFrom().isBefore(periodFrom)
                    || slice.overlapTo().isAfter(periodTo)) {
                throw new IllegalStateException(
                        "Paragraph-7 employment coverage slice exceeds pre-event boundary"
                );
            }

            for (LocalDate date : inclusiveDates(
                    slice.overlapFrom(),
                    slice.overlapTo()
            )) {
                if (!result.add(date)) {
                    throw new IllegalStateException(
                            "Paragraph-7 employment coverage slices overlap at " + date
                    );
                }
            }
        }

        return result;
    }

    private void validateFacts(
            AverageEarningsReferenceFactsService.ReferenceFacts facts,
            YearMonth eventMonth,
            LocalDate periodFrom,
            LocalDate periodTo
    ) {
        if (!eventMonth.equals(facts.eventMonth())
                || !periodFrom.equals(facts.referenceFrom())
                || !periodTo.equals(facts.referenceTo())) {
            throw new IllegalStateException(
                    "Paragraph-7 calendar absence facts boundary mismatch"
            );
        }
    }

    private void validateAbsenceBoundary(
            AverageEarningsReferenceFactsService.AbsenceFact fact,
            LocalDate periodFrom,
            LocalDate periodTo
    ) {
        if (fact.periodId() == null
                || fact.overlapFrom() == null
                || fact.overlapTo() == null
                || fact.overlapTo().isBefore(fact.overlapFrom())) {
            throw new IllegalStateException(
                    "Paragraph-7 calendar absence boundary is invalid"
            );
        }

        if (fact.overlapFrom().isBefore(periodFrom)
                || fact.overlapTo().isAfter(periodTo)) {
            throw new IllegalStateException(
                    "Paragraph-7 calendar absence exceeds pre-event boundary"
            );
        }
    }

    private List<LocalDate> inclusiveDates(
            LocalDate from,
            LocalDate to
    ) {
        if (from == null
                || to == null
                || to.isBefore(from)) {
            throw new IllegalArgumentException(
                    "Paragraph-7 inclusive calendar range is invalid"
            );
        }

        List<LocalDate> result = new ArrayList<>();
        for (LocalDate date = from;
                !date.isAfter(to);
                date = date.plusDays(1)) {
            result.add(date);
        }
        return result;
    }

    public record Resolution(
            LocalDate eventDate,
            YearMonth eventMonth,
            LocalDate periodFrom,
            LocalDate cutoffExclusive,
            boolean ready,
            String blockingReason,
            String blockingMessage,
            int countableCalendarDays,
            List<LocalDate> excludedDates,
            List<LocalDate> retainedVacationHolidayDates,
            VacationAverageUnifiedDailyResolver.Paragraph7CalendarBasis basis
    ) {
        public Resolution {
            Objects.requireNonNull(
                    eventDate,
                    "Paragraph-7 calendar result requires event date"
            );
            Objects.requireNonNull(
                    eventMonth,
                    "Paragraph-7 calendar result requires event month"
            );
            Objects.requireNonNull(
                    periodFrom,
                    "Paragraph-7 calendar result requires period start"
            );
            Objects.requireNonNull(
                    cutoffExclusive,
                    "Paragraph-7 calendar result requires cutoff"
            );
            excludedDates = List.copyOf(Objects.requireNonNull(
                    excludedDates,
                    "Paragraph-7 calendar result requires excluded dates"
            ));
            retainedVacationHolidayDates = List.copyOf(Objects.requireNonNull(
                    retainedVacationHolidayDates,
                    "Paragraph-7 calendar result requires retained holiday dates"
            ));

            if (!eventMonth.equals(YearMonth.from(eventDate))
                    || !periodFrom.equals(eventMonth.atDay(1))
                    || !cutoffExclusive.equals(eventDate)) {
                throw new IllegalArgumentException(
                        "Paragraph-7 calendar result event identity is invalid"
                );
            }

            if (ready) {
                if (blockingReason != null
                        || blockingMessage != null
                        || countableCalendarDays <= 0
                        || basis == null
                        || basis.countableCalendarDays() != countableCalendarDays
                        || !basis.eventDate().equals(eventDate)
                        || !basis.authorityCode().equals(RULE_ID)) {
                    throw new IllegalArgumentException(
                            "Ready paragraph-7 calendar result is incomplete"
                    );
                }
            } else {
                if (blockingReason == null
                        || blockingReason.isBlank()
                        || blockingMessage == null
                        || blockingMessage.isBlank()
                        || countableCalendarDays != 0
                        || !excludedDates.isEmpty()
                        || !retainedVacationHolidayDates.isEmpty()
                        || basis != null) {
                    throw new IllegalArgumentException(
                            "Blocked paragraph-7 calendar result cannot expose partial basis"
                    );
                }
            }
        }

        static Resolution ready(
                LocalDate eventDate,
                LocalDate periodFrom,
                LocalDate cutoffExclusive,
                int countableCalendarDays,
                List<LocalDate> excludedDates,
                List<LocalDate> retainedVacationHolidayDates,
                VacationAverageUnifiedDailyResolver.Paragraph7CalendarBasis basis
        ) {
            return new Resolution(
                    eventDate,
                    YearMonth.from(eventDate),
                    periodFrom,
                    cutoffExclusive,
                    true,
                    null,
                    null,
                    countableCalendarDays,
                    excludedDates,
                    retainedVacationHolidayDates,
                    basis
            );
        }

        static Resolution blocked(
                LocalDate eventDate,
                LocalDate periodFrom,
                LocalDate cutoffExclusive,
                String blockingReason,
                String blockingMessage
        ) {
            return new Resolution(
                    eventDate,
                    YearMonth.from(eventDate),
                    periodFrom,
                    cutoffExclusive,
                    false,
                    blockingReason,
                    blockingMessage,
                    0,
                    List.of(),
                    List.of(),
                    null
            );
        }
    }
}
