package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AbsencePeriod;
import ru.daniil.shifts.model.AbsenceType;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.AbsencePeriodRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Article-120 FACT authority for the calendar-day quantity payable as annual
 * paid vacation.
 *
 * <p>This service proves the exact annual-vacation absence identity and
 * partitions its physical inclusive span into payable calendar dates and
 * excluded non-working holiday dates. Weekends and transferred days off remain
 * payable calendar vacation days. VacationPlanner countMode is deliberately
 * irrelevant.</p>
 *
 * <p>No average-daily rate, multiplication, rounding or vacation-pay money is
 * produced here.</p>
 */
@Service
public class VacationPayableDaysFactService {
    public static final String RULE_ID = "TK_RF_ARTICLE_120";
    public static final String ABSENCE_MISSING =
            "TK_RF_ARTICLE_120_VACATION_ABSENCE_MISSING";
    public static final String OWNERSHIP_MISMATCH =
            "TK_RF_ARTICLE_120_VACATION_OWNERSHIP_MISMATCH";
    public static final String EVENT_IDENTITY_MISMATCH =
            "TK_RF_ARTICLE_120_VACATION_EVENT_IDENTITY_MISMATCH";
    public static final String ANNUAL_PAID_VACATION_REQUIRED =
            "TK_RF_ARTICLE_120_ANNUAL_PAID_VACATION_REQUIRED";
    public static final String POSTED_STATUS_REQUIRED =
            "TK_RF_ARTICLE_120_POSTED_STATUS_REQUIRED";
    public static final String FULL_DAY_REQUIRED =
            "TK_RF_ARTICLE_120_FULL_DAY_REQUIRED";
    public static final String SPAN_INVALID =
            "TK_RF_ARTICLE_120_VACATION_SPAN_INVALID";
    public static final String HOLIDAY_AUTHORITY_UNAVAILABLE =
            "TK_RF_ARTICLE_120_HOLIDAY_AUTHORITY_UNAVAILABLE";

    private static final String VACATION = "VACATION";
    private static final String VACATION_DAYS = "VACATION_DAYS";
    private static final String VACATION_ALLOWANCE = "VACATION_ALLOWANCE";
    private static final String FULL_DAY = "FULL_DAY";

    private final AbsencePeriodRepository absences;
    private final LedgerIntegrityService ledgerIntegrity;
    private final ProductionCalendarService productionCalendar;

    public VacationPayableDaysFactService(
            AbsencePeriodRepository absences,
            LedgerIntegrityService ledgerIntegrity,
            ProductionCalendarService productionCalendar
    ) {
        this.absences = Objects.requireNonNull(
                absences,
                "Vacation payable-days facts require absence repository"
        );
        this.ledgerIntegrity = Objects.requireNonNull(
                ledgerIntegrity,
                "Vacation payable-days facts require ledger status authority"
        );
        this.productionCalendar = Objects.requireNonNull(
                productionCalendar,
                "Vacation payable-days facts require production calendar"
        );
    }

    @Transactional(readOnly = true)
    public Resolution resolve(
            AppUser user,
            LocalDate eventDate,
            Long absencePeriodId
    ) {
        Objects.requireNonNull(user, "Vacation payable-days facts require user");
        Objects.requireNonNull(
                eventDate,
                "Vacation payable-days facts require event date"
        );
        if (absencePeriodId == null || absencePeriodId <= 0L) {
            return Resolution.blocked(
                    eventDate,
                    absencePeriodId,
                    ABSENCE_MISSING,
                    "Annual paid vacation absence id is missing"
            );
        }

        AbsencePeriod period = absences.findById(absencePeriodId).orElse(null);
        if (period == null) {
            return Resolution.blocked(
                    eventDate,
                    absencePeriodId,
                    ABSENCE_MISSING,
                    "Annual paid vacation absence does not exist"
            );
        }

        if (!samePersistedOwner(user, period.getOwner())) {
            return Resolution.blocked(
                    eventDate,
                    absencePeriodId,
                    OWNERSHIP_MISMATCH,
                    "Annual paid vacation absence does not belong to requested user"
            );
        }

        LocalDate from = period.getStartDate();
        LocalDate to = period.getEndDate();
        if (from == null || to == null || to.isBefore(from)) {
            return Resolution.blocked(
                    eventDate,
                    absencePeriodId,
                    SPAN_INVALID,
                    "Annual paid vacation physical span is invalid"
            );
        }
        if (!from.equals(eventDate)) {
            return Resolution.blocked(
                    eventDate,
                    absencePeriodId,
                    EVENT_IDENTITY_MISMATCH,
                    "Vacation event date must equal the exact annual-vacation start date"
            );
        }

        AbsenceType type = period.getType();
        if (type == null
                || !VACATION.equals(type.getSystemCode())
                || !VACATION_DAYS.equals(type.getBalancePolicy())
                || !VACATION_ALLOWANCE.equals(period.getCompensationPolicy())) {
            return Resolution.blocked(
                    eventDate,
                    absencePeriodId,
                    ANNUAL_PAID_VACATION_REQUIRED,
                    "Absence is not canonical annual paid vacation"
            );
        }

        if (!FULL_DAY.equals(period.getCoverage())) {
            return Resolution.blocked(
                    eventDate,
                    absencePeriodId,
                    FULL_DAY_REQUIRED,
                    "Annual paid vacation payable-day fact requires FULL_DAY coverage"
            );
        }

        if (!ledgerIntegrity.posts(period.getStatus())) {
            return Resolution.blocked(
                    eventDate,
                    absencePeriodId,
                    POSTED_STATUS_REQUIRED,
                    "Annual paid vacation must be APPROVED or COMPLETED before payable-day authority is ready"
            );
        }

        List<LocalDate> physicalSpanDates = inclusiveDates(from, to);
        List<LocalDate> payableDates = new ArrayList<>();
        List<LocalDate> excludedHolidayDates = new ArrayList<>();
        List<AnnualPaidVacationHolidayPolicy.HolidayFact> holidayFacts =
                new ArrayList<>();

        for (LocalDate date : physicalSpanDates) {
            AnnualPaidVacationHolidayPolicy.HolidayFact holidayFact;
            try {
                holidayFact = AnnualPaidVacationHolidayPolicy.classify(
                        productionCalendar,
                        user,
                        date
                );
            } catch (IllegalStateException ex) {
                return Resolution.blocked(
                        eventDate,
                        absencePeriodId,
                        HOLIDAY_AUTHORITY_UNAVAILABLE,
                        "Annual paid vacation holiday authority is unavailable: "
                                + ex.getMessage()
                );
            }
            holidayFacts.add(holidayFact);
            if (holidayFact.nonWorkingHoliday()) {
                excludedHolidayDates.add(date);
            } else {
                payableDates.add(date);
            }
        }

        return Resolution.ready(
                eventDate,
                absencePeriodId,
                from,
                to,
                period.getStatus(),
                physicalSpanDates,
                payableDates,
                excludedHolidayDates,
                holidayFacts
        );
    }

    private boolean samePersistedOwner(AppUser requested, AppUser actual) {
        return requested.getId() != null
                && actual != null
                && actual.getId() != null
                && requested.getId().equals(actual.getId());
    }

    private List<LocalDate> inclusiveDates(LocalDate from, LocalDate to) {
        List<LocalDate> result = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            result.add(date);
        }
        return List.copyOf(result);
    }

    public record Resolution(
            LocalDate eventDate,
            YearMonth eventMonth,
            Long requestedAbsencePeriodId,
            boolean ready,
            String blockingReason,
            String blockingMessage,
            Long absencePeriodId,
            LocalDate vacationFrom,
            LocalDate vacationTo,
            String status,
            List<LocalDate> physicalSpanDates,
            List<LocalDate> payableDates,
            List<LocalDate> excludedHolidayDates,
            List<AnnualPaidVacationHolidayPolicy.HolidayFact> holidayFacts
    ) {
        public Resolution {
            Objects.requireNonNull(eventDate, "Vacation payable-days result requires event date");
            Objects.requireNonNull(eventMonth, "Vacation payable-days result requires event month");
            if (!eventMonth.equals(YearMonth.from(eventDate))) {
                throw new IllegalArgumentException(
                        "Vacation payable-days result event identity is invalid"
                );
            }
            if (ready == (blockingReason != null)) {
                throw new IllegalArgumentException(
                        "Vacation payable-days result state is invalid"
                );
            }
            if (ready) {
                if (blockingMessage != null
                        || absencePeriodId == null
                        || absencePeriodId <= 0L
                        || !Objects.equals(absencePeriodId, requestedAbsencePeriodId)
                        || vacationFrom == null
                        || vacationTo == null
                        || vacationTo.isBefore(vacationFrom)
                        || !eventDate.equals(vacationFrom)
                        || status == null
                        || status.isBlank()) {
                    throw new IllegalArgumentException(
                            "Ready vacation payable-days result has incomplete vacation identity"
                    );
                }
                physicalSpanDates = List.copyOf(Objects.requireNonNull(
                        physicalSpanDates,
                        "Ready vacation payable-days result requires physical span"
                ));
                payableDates = List.copyOf(Objects.requireNonNull(
                        payableDates,
                        "Ready vacation payable-days result requires payable dates"
                ));
                excludedHolidayDates = List.copyOf(Objects.requireNonNull(
                        excludedHolidayDates,
                        "Ready vacation payable-days result requires excluded holidays"
                ));
                holidayFacts = List.copyOf(Objects.requireNonNull(
                        holidayFacts,
                        "Ready vacation payable-days result requires holiday provenance"
                ));
                validateReadyPartition(
                        vacationFrom,
                        vacationTo,
                        physicalSpanDates,
                        payableDates,
                        excludedHolidayDates,
                        holidayFacts
                );
            } else {
                if (blockingReason == null
                        || blockingReason.isBlank()
                        || blockingMessage == null
                        || blockingMessage.isBlank()
                        || absencePeriodId != null
                        || vacationFrom != null
                        || vacationTo != null
                        || status != null
                        || physicalSpanDates != null
                        || payableDates != null
                        || excludedHolidayDates != null
                        || holidayFacts != null) {
                    throw new IllegalArgumentException(
                            "Blocked vacation payable-days result cannot expose partial vacation fact"
                    );
                }
            }
        }

        static Resolution ready(
                LocalDate eventDate,
                Long absencePeriodId,
                LocalDate vacationFrom,
                LocalDate vacationTo,
                String status,
                List<LocalDate> physicalSpanDates,
                List<LocalDate> payableDates,
                List<LocalDate> excludedHolidayDates,
                List<AnnualPaidVacationHolidayPolicy.HolidayFact> holidayFacts
        ) {
            return new Resolution(
                    eventDate,
                    YearMonth.from(eventDate),
                    absencePeriodId,
                    true,
                    null,
                    null,
                    absencePeriodId,
                    vacationFrom,
                    vacationTo,
                    status,
                    physicalSpanDates,
                    payableDates,
                    excludedHolidayDates,
                    holidayFacts
            );
        }

        static Resolution blocked(
                LocalDate eventDate,
                Long requestedAbsencePeriodId,
                String blockingReason,
                String blockingMessage
        ) {
            return new Resolution(
                    eventDate,
                    YearMonth.from(eventDate),
                    requestedAbsencePeriodId,
                    false,
                    blockingReason,
                    blockingMessage,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        public int physicalCalendarDays() {
            return ready ? physicalSpanDates.size() : 0;
        }

        public int payableCalendarDays() {
            return ready ? payableDates.size() : 0;
        }

        public int excludedHolidayCalendarDays() {
            return ready ? excludedHolidayDates.size() : 0;
        }

        private static void validateReadyPartition(
                LocalDate from,
                LocalDate to,
                List<LocalDate> physical,
                List<LocalDate> payable,
                List<LocalDate> excluded,
                List<AnnualPaidVacationHolidayPolicy.HolidayFact> facts
        ) {
            List<LocalDate> expected = new ArrayList<>();
            for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
                expected.add(date);
            }
            if (!physical.equals(expected)) {
                throw new IllegalArgumentException(
                        "Vacation payable-days physical span is not exact inclusive interval"
                );
            }
            Set<LocalDate> payableSet = new HashSet<>(payable);
            Set<LocalDate> excludedSet = new HashSet<>(excluded);
            if (payableSet.size() != payable.size()
                    || excludedSet.size() != excluded.size()
                    || !java.util.Collections.disjoint(payableSet, excludedSet)) {
                throw new IllegalArgumentException(
                        "Vacation payable-days partition contains duplicates or overlap"
                );
            }
            Set<LocalDate> union = new HashSet<>(payableSet);
            union.addAll(excludedSet);
            if (union.size() != physical.size()
                    || !union.equals(new HashSet<>(physical))) {
                throw new IllegalArgumentException(
                        "Vacation payable-days partition does not cover physical span"
                );
            }
            if (facts.size() != physical.size()) {
                throw new IllegalArgumentException(
                        "Vacation payable-days holiday provenance does not cover physical span"
                );
            }
            for (int index = 0; index < physical.size(); index++) {
                LocalDate date = physical.get(index);
                AnnualPaidVacationHolidayPolicy.HolidayFact fact = facts.get(index);
                if (!fact.date().equals(date)
                        || fact.nonWorkingHoliday() != excludedSet.contains(date)) {
                    throw new IllegalArgumentException(
                            "Vacation payable-days holiday provenance contradicts date partition"
                    );
                }
            }
        }
    }
}
