package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.ProductionCalendarMonthDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.CompensationTerm;
import ru.daniil.shifts.repo.CompensationTermRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

/**
 * Paragraph-7 pre-event BASE_PAY pricing FACT authority.
 *
 * <p>This service intentionally stops before money. J3A proves which days and
 * minutes were actually worked before the legal event. J3B1 attaches the
 * exact effective compensation-term identity and derives only the base-pay
 * quantity that Native Payroll itself would allow to reach BASE_PAY.</p>
 *
 * <p>HOURLY uses canonical hourlyBaseWorkedMinutes, so banked overtime does
 * not silently become ordinary base pay. SALARY uses min(planned, worked) per
 * factual day, so work above the schedule cannot inflate monthly salary
 * coverage. No monthly gross, posting-month total, premium, paragraph-8
 * fallback or average-earnings money is inferred here.</p>
 */
@Service
public class AverageEarningsParagraph7PreEventBasePayAuthorityService {
    public static final String COMPENSATION_TERM_MISSING =
            "PP_540_P7_PRE_EVENT_COMPENSATION_TERM_MISSING";
    public static final String COMPENSATION_TERM_INVALID =
            "PP_540_P7_PRE_EVENT_COMPENSATION_TERM_INVALID";
    public static final String CURRENCY_INVALID =
            "PP_540_P7_PRE_EVENT_CURRENCY_INVALID";
    public static final String PRODUCTION_NORM_INCOMPLETE =
            "PP_540_P7_PRE_EVENT_PRODUCTION_NORM_INCOMPLETE";
    public static final String PRODUCTION_NORM_REQUIRED =
            "PP_540_P7_PRE_EVENT_PRODUCTION_NORM_REQUIRED";
    public static final String WORK_AUTHORITY_WINDOW_MISMATCH =
            "PP_540_P7_PRE_EVENT_WORK_AUTHORITY_WINDOW_MISMATCH";

    private final AverageEarningsParagraph7PreEventWorkFactService workFacts;
    private final CompensationTermRepository compensationTerms;
    private final ProductionCalendarService productionCalendar;

    public AverageEarningsParagraph7PreEventBasePayAuthorityService(
            AverageEarningsParagraph7PreEventWorkFactService workFacts,
            CompensationTermRepository compensationTerms,
            ProductionCalendarService productionCalendar
    ) {
        this.workFacts = Objects.requireNonNull(
                workFacts,
                "Paragraph-7 base-pay authority requires pre-event work facts"
        );
        this.compensationTerms = Objects.requireNonNull(
                compensationTerms,
                "Paragraph-7 base-pay authority requires compensation-term history"
        );
        this.productionCalendar = Objects.requireNonNull(
                productionCalendar,
                "Paragraph-7 base-pay authority requires production-calendar authority"
        );
    }

    @Transactional(readOnly = true)
    public Resolution resolve(
            AppUser user,
            LocalDate eventDate
    ) {
        Objects.requireNonNull(
                user,
                "Paragraph-7 base-pay authority requires user"
        );
        Objects.requireNonNull(
                eventDate,
                "Paragraph-7 base-pay authority requires event date"
        );
        AverageEarningsLegalPolicy.requireRegime(eventDate);

        AverageEarningsParagraph7PreEventWorkFactService.Resolution work =
                Objects.requireNonNull(
                        workFacts.resolve(user, eventDate),
                        "Paragraph-7 pre-event work authority returned null"
                );
        YearMonth eventMonth = YearMonth.from(eventDate);
        LocalDate periodFrom = eventMonth.atDay(1);
        if (!eventDate.equals(work.eventDate())
                || !periodFrom.equals(work.periodFrom())
                || !eventDate.equals(work.cutoffExclusive())) {
            return Resolution.blocked(
                    eventDate,
                    periodFrom,
                    work,
                    WORK_AUTHORITY_WINDOW_MISMATCH,
                    "Pre-event worked-time authority does not match the legal paragraph-7 window"
            );
        }

        if (!work.workedTimePresent()) {
            return Resolution.readyWithoutWorkedTime(
                    eventDate,
                    periodFrom,
                    work
            );
        }

        LocalDate compensationBoundary = eventMonth.atDay(1);
        CompensationTerm term = compensationTerms
                .findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        user,
                        compensationBoundary
                )
                .orElse(null);
        if (term == null) {
            return Resolution.blocked(
                    eventDate,
                    periodFrom,
                    work,
                    COMPENSATION_TERM_MISSING,
                    "No effective compensation term for paragraph-7 event month"
            );
        }

        String currency = term.getCurrencyCode();
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            return Resolution.blocked(
                    eventDate,
                    periodFrom,
                    work,
                    CURRENCY_INVALID,
                    "Paragraph-7 compensation term has invalid currency"
            );
        }
        LocalDate effectiveFrom = term.getEffectiveFrom();
        if (effectiveFrom == null || effectiveFrom.isAfter(compensationBoundary)) {
            return Resolution.blocked(
                    eventDate,
                    periodFrom,
                    work,
                    COMPENSATION_TERM_INVALID,
                    "Paragraph-7 compensation-term effective date is invalid"
            );
        }

        String payMode = term.getPayMode();
        if ("HOURLY".equals(payMode)) {
            Long hourlyRate = term.getHourlyRateMinor();
            if (hourlyRate == null || hourlyRate <= 0L) {
                return Resolution.blocked(
                        eventDate,
                        periodFrom,
                        work,
                        COMPENSATION_TERM_INVALID,
                        "Paragraph-7 hourly compensation term requires positive configured rate"
                );
            }
            long eligibleMinutes = sumHourlyBaseMinutes(work.workedDays());
            return Resolution.ready(
                    eventDate,
                    periodFrom,
                    work,
                    effectiveFrom,
                    payMode,
                    currency,
                    hourlyRate,
                    null,
                    null,
                    eligibleMinutes
            );
        }

        if (!"SALARY".equals(payMode)) {
            return Resolution.blocked(
                    eventDate,
                    periodFrom,
                    work,
                    COMPENSATION_TERM_INVALID,
                    "Paragraph-7 compensation term has unsupported pay mode"
            );
        }
        Long monthlySalary = term.getMonthlySalaryMinor();
        if (monthlySalary == null || monthlySalary <= 0L) {
            return Resolution.blocked(
                    eventDate,
                    periodFrom,
                    work,
                    COMPENSATION_TERM_INVALID,
                    "Paragraph-7 salary compensation term requires positive monthly salary"
            );
        }

        ProductionCalendarMonthDto production = Objects.requireNonNull(
                productionCalendar.month(user, eventMonth.toString()),
                "Paragraph-7 production calendar returned null"
        );
        if (!production.scheduleCoverageComplete()) {
            return Resolution.blocked(
                    eventDate,
                    periodFrom,
                    work,
                    PRODUCTION_NORM_INCOMPLETE,
                    "Salary paragraph-7 authority requires complete event-month schedule norm"
            );
        }
        int normMinutes = production.productionNormMinutes();
        if (normMinutes <= 0) {
            return Resolution.blocked(
                    eventDate,
                    periodFrom,
                    work,
                    PRODUCTION_NORM_REQUIRED,
                    "Salary paragraph-7 authority requires positive event-month production norm"
            );
        }

        long eligibleMinutes = sumSalaryCoveredWorkedMinutes(work.workedDays());
        return Resolution.ready(
                eventDate,
                periodFrom,
                work,
                effectiveFrom,
                payMode,
                currency,
                null,
                monthlySalary,
                normMinutes,
                eligibleMinutes
        );
    }

    private static long sumHourlyBaseMinutes(
            List<AverageEarningsParagraph7PreEventWorkFactService.WorkedDayFact> days
    ) {
        long total = 0L;
        for (AverageEarningsParagraph7PreEventWorkFactService.WorkedDayFact day : days) {
            total = Math.addExact(
                    total,
                    day.hourlyBaseWorkedMinutes()
            );
        }
        return total;
    }

    private static long sumSalaryCoveredWorkedMinutes(
            List<AverageEarningsParagraph7PreEventWorkFactService.WorkedDayFact> days
    ) {
        long total = 0L;
        for (AverageEarningsParagraph7PreEventWorkFactService.WorkedDayFact day : days) {
            total = Math.addExact(
                    total,
                    Math.min(
                            day.plannedMinutes(),
                            day.workedMinutes()
                    )
            );
        }
        return total;
    }

    public record Resolution(
            LocalDate eventDate,
            LocalDate periodFrom,
            LocalDate cutoffExclusive,
            boolean ready,
            String blockingReason,
            String blockingMessage,
            AverageEarningsParagraph7PreEventWorkFactService.Resolution workFacts,
            LocalDate compensationEffectiveFrom,
            String payMode,
            String currencyCode,
            Long configuredHourlyRateMinor,
            Long monthlySalaryMinor,
            Integer productionNormMinutes,
            long eligibleBasePayMinutes
    ) {
        public Resolution {
            Objects.requireNonNull(eventDate, "Paragraph-7 event date is required");
            Objects.requireNonNull(periodFrom, "Paragraph-7 period start is required");
            Objects.requireNonNull(cutoffExclusive, "Paragraph-7 cutoff is required");
            Objects.requireNonNull(workFacts, "Paragraph-7 work facts are required");
            if (!periodFrom.equals(YearMonth.from(eventDate).atDay(1))
                    || !cutoffExclusive.equals(eventDate)
                    || eligibleBasePayMinutes < 0L) {
                throw new IllegalArgumentException(
                        "Paragraph-7 base-pay authority window or quantity is invalid"
                );
            }
            if (ready) {
                if (blockingReason != null || blockingMessage != null) {
                    throw new IllegalArgumentException(
                            "Ready paragraph-7 base-pay authority cannot contain blocker"
                    );
                }
                if (!workFacts.workedTimePresent()) {
                    if (compensationEffectiveFrom != null
                            || payMode != null
                            || currencyCode != null
                            || configuredHourlyRateMinor != null
                            || monthlySalaryMinor != null
                            || productionNormMinutes != null
                            || eligibleBasePayMinutes != 0L) {
                        throw new IllegalArgumentException(
                                "No-work paragraph-7 authority cannot invent pricing identity"
                        );
                    }
                } else if ("HOURLY".equals(payMode)) {
                    if (compensationEffectiveFrom == null
                            || currencyCode == null
                            || configuredHourlyRateMinor == null
                            || configuredHourlyRateMinor <= 0L
                            || monthlySalaryMinor != null
                            || productionNormMinutes != null) {
                        throw new IllegalArgumentException(
                                "Ready hourly paragraph-7 authority is incomplete"
                        );
                    }
                } else if ("SALARY".equals(payMode)) {
                    if (compensationEffectiveFrom == null
                            || currencyCode == null
                            || configuredHourlyRateMinor != null
                            || monthlySalaryMinor == null
                            || monthlySalaryMinor <= 0L
                            || productionNormMinutes == null
                            || productionNormMinutes <= 0) {
                        throw new IllegalArgumentException(
                                "Ready salary paragraph-7 authority is incomplete"
                        );
                    }
                } else {
                    throw new IllegalArgumentException(
                            "Ready paragraph-7 authority has unsupported pay mode"
                    );
                }
            } else {
                if (blockingReason == null
                        || blockingReason.isBlank()
                        || blockingMessage == null
                        || blockingMessage.isBlank()
                        || compensationEffectiveFrom != null
                        || payMode != null
                        || currencyCode != null
                        || configuredHourlyRateMinor != null
                        || monthlySalaryMinor != null
                        || productionNormMinutes != null
                        || eligibleBasePayMinutes != 0L) {
                    throw new IllegalArgumentException(
                            "Blocked paragraph-7 base-pay authority cannot expose partial pricing identity"
                    );
                }
            }
        }

        static Resolution readyWithoutWorkedTime(
                LocalDate eventDate,
                LocalDate periodFrom,
                AverageEarningsParagraph7PreEventWorkFactService.Resolution work
        ) {
            return new Resolution(
                    eventDate,
                    periodFrom,
                    eventDate,
                    true,
                    null,
                    null,
                    work,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    0L
            );
        }

        static Resolution ready(
                LocalDate eventDate,
                LocalDate periodFrom,
                AverageEarningsParagraph7PreEventWorkFactService.Resolution work,
                LocalDate compensationEffectiveFrom,
                String payMode,
                String currencyCode,
                Long configuredHourlyRateMinor,
                Long monthlySalaryMinor,
                Integer productionNormMinutes,
                long eligibleBasePayMinutes
        ) {
            return new Resolution(
                    eventDate,
                    periodFrom,
                    eventDate,
                    true,
                    null,
                    null,
                    work,
                    compensationEffectiveFrom,
                    payMode,
                    currencyCode,
                    configuredHourlyRateMinor,
                    monthlySalaryMinor,
                    productionNormMinutes,
                    eligibleBasePayMinutes
            );
        }

        static Resolution blocked(
                LocalDate eventDate,
                LocalDate periodFrom,
                AverageEarningsParagraph7PreEventWorkFactService.Resolution work,
                String reason,
                String message
        ) {
            return new Resolution(
                    eventDate,
                    periodFrom,
                    eventDate,
                    false,
                    reason,
                    message,
                    work,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    0L
            );
        }

        public boolean workedTimePresent() {
            return workFacts.workedTimePresent();
        }

        public boolean basePayQuantityPresent() {
            return ready && eligibleBasePayMinutes > 0L;
        }
    }
}
