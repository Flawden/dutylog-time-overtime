package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

/**
 * Primary twelve-month vacation average-daily calculation under paragraph 10.
 *
 * <pre>
 * final numerator MONEY (8A4F3H)
 * + vacation reference-calendar FACT / paragraph-10 denominator
 * -> exact average-daily earnings
 * </pre>
 *
 * <p>This layer deliberately remains primary-reference only. Paragraphs 6-8
 * fallback selection, final vacation-pay days, final money rounding and
 * compensation-for-unused-vacation scenarios remain separate boundaries.</p>
 *
 * <p>Paragraph 13 average-hourly earnings is intentionally absent: paragraph
 * 13 expressly excludes vacation and unused-vacation compensation.</p>
 */
@Service
public class VacationAveragePrimaryCalculationService {

    public static final String AUTHORITY_WINDOW_MISMATCH =
            "VACATION_AVERAGE_PRIMARY_AUTHORITY_WINDOW_MISMATCH";

    private final AverageEarningsNumeratorCalculationService numerator;
    private final VacationAverageReferenceCalendarService calendar;

    public VacationAveragePrimaryCalculationService(
            AverageEarningsNumeratorCalculationService numerator,
            VacationAverageReferenceCalendarService calendar
    ) {
        this.numerator = Objects.requireNonNull(
                numerator,
                "Vacation average numerator calculation service is required"
        );
        this.calendar = Objects.requireNonNull(
                calendar,
                "Vacation average reference calendar service is required"
        );
    }

    @Transactional(readOnly = true)
    public Resolution calculate(
            AppUser user,
            LocalDate eventDate,
            YearMonth discoveryThroughMonth,
            List<YearMonth> provenNoPayrollMonths
    ) {
        Objects.requireNonNull(user, "Vacation average calculation requires user");
        Objects.requireNonNull(eventDate, "Vacation average calculation requires event date");
        Objects.requireNonNull(
                discoveryThroughMonth,
                "Vacation average calculation requires discovery-through month"
        );
        provenNoPayrollMonths = List.copyOf(Objects.requireNonNull(
                provenNoPayrollMonths,
                "Vacation average calculation requires no-Payroll proofs"
        ));

        AverageEarningsLegalPolicy.requireRegime(eventDate);

        YearMonth eventMonth = YearMonth.from(eventDate);
        YearMonth referenceFrom = eventMonth.minusMonths(12);
        YearMonth referenceTo = eventMonth.minusMonths(1);

        AverageEarningsNumeratorCalculationService.Resolution money =
                Objects.requireNonNull(
                        numerator.calculate(
                                user,
                                eventDate,
                                discoveryThroughMonth,
                                provenNoPayrollMonths
                        ),
                        "Vacation average numerator authority returned null"
                );

        if (!money.ready()) {
            return Resolution.blocked(
                    eventDate,
                    eventMonth,
                    referenceFrom,
                    referenceTo,
                    discoveryThroughMonth,
                    money.blockingReason(),
                    money.blockingPeriod()
            );
        }

        if (!money.eventDate().equals(eventDate)
                || !money.eventMonth().equals(eventMonth)
                || !money.referenceFrom().equals(referenceFrom)
                || !money.referenceTo().equals(referenceTo)
                || !money.discoveryThroughMonth().equals(discoveryThroughMonth)) {
            return Resolution.blocked(
                    eventDate,
                    eventMonth,
                    referenceFrom,
                    referenceTo,
                    discoveryThroughMonth,
                    AUTHORITY_WINDOW_MISMATCH,
                    null
            );
        }

        VacationAverageReferenceCalendarService.Result calendarAuthority =
                Objects.requireNonNull(
                        calendar.resolve(user, eventDate),
                        "Vacation average reference calendar authority returned null"
                );

        if (!calendarAuthority.eventDate().equals(eventDate)
                || !calendarAuthority.eventMonth().equals(eventMonth)
                || !calendarAuthority.referenceFrom().equals(referenceFrom.atDay(1))
                || !calendarAuthority.referenceTo().equals(referenceTo.atEndOfMonth())
                || !calendarAuthority.denominator().eventDate().equals(eventDate)
                || !calendarAuthority.denominator().eventMonth().equals(eventMonth)
                || !calendarAuthority.denominator().referenceFrom().equals(referenceFrom)
                || !calendarAuthority.denominator().referenceTo().equals(referenceTo)) {
            return Resolution.blocked(
                    eventDate,
                    eventMonth,
                    referenceFrom,
                    referenceTo,
                    discoveryThroughMonth,
                    AUTHORITY_WINDOW_MISMATCH,
                    null
            );
        }

        VacationAverageDailyEarningsFormula.ExactMoneyPerDay daily =
                VacationAverageDailyEarningsFormula.calculate(
                        money.numeratorAmountMinor(),
                        calendarAuthority.denominator().denominatorDays()
                );

        return Resolution.ready(
                eventDate,
                eventMonth,
                referenceFrom,
                referenceTo,
                discoveryThroughMonth,
                money.currencyCode(),
                money,
                calendarAuthority,
                daily
        );
    }

    public record Resolution(
            LocalDate eventDate,
            YearMonth eventMonth,
            YearMonth referenceFrom,
            YearMonth referenceTo,
            YearMonth discoveryThroughMonth,
            boolean ready,
            String blockingReason,
            YearMonth blockingPeriod,
            String currencyCode,
            AverageEarningsNumeratorCalculationService.Resolution numerator,
            VacationAverageReferenceCalendarService.Result calendar,
            VacationAverageDailyEarningsFormula.ExactMoneyPerDay averageDaily
    ) {
        public Resolution {
            Objects.requireNonNull(eventDate, "Vacation average event date is required");
            Objects.requireNonNull(eventMonth, "Vacation average event month is required");
            Objects.requireNonNull(referenceFrom, "Vacation average reference start is required");
            Objects.requireNonNull(referenceTo, "Vacation average reference end is required");
            Objects.requireNonNull(
                    discoveryThroughMonth,
                    "Vacation average discovery-through month is required"
            );

            if (!eventMonth.equals(YearMonth.from(eventDate))
                    || !referenceFrom.equals(eventMonth.minusMonths(12))
                    || !referenceTo.equals(eventMonth.minusMonths(1))
                    || discoveryThroughMonth.isBefore(referenceTo)) {
                throw new IllegalArgumentException(
                        "Vacation average primary window is not canonical"
                );
            }

            if (ready == (blockingReason != null)) {
                throw new IllegalArgumentException(
                        "Vacation average primary resolution state is invalid"
                );
            }

            if (ready) {
                if (blockingPeriod != null
                        || numerator == null
                        || !numerator.ready()
                        || calendar == null
                        || averageDaily == null
                        || (currencyCode != null && !currencyCode.matches("[A-Z]{3}"))) {
                    throw new IllegalArgumentException(
                            "Ready vacation average primary calculation is incomplete"
                    );
                }
            } else {
                if (currencyCode != null
                        || numerator != null
                        || calendar != null
                        || averageDaily != null) {
                    throw new IllegalArgumentException(
                            "Blocked vacation average calculation cannot expose partial authority"
                    );
                }
            }
        }

        public static Resolution ready(
                LocalDate eventDate,
                YearMonth eventMonth,
                YearMonth referenceFrom,
                YearMonth referenceTo,
                YearMonth discoveryThroughMonth,
                String currencyCode,
                AverageEarningsNumeratorCalculationService.Resolution numerator,
                VacationAverageReferenceCalendarService.Result calendar,
                VacationAverageDailyEarningsFormula.ExactMoneyPerDay averageDaily
        ) {
            return new Resolution(
                    eventDate,
                    eventMonth,
                    referenceFrom,
                    referenceTo,
                    discoveryThroughMonth,
                    true,
                    null,
                    null,
                    currencyCode,
                    numerator,
                    calendar,
                    averageDaily
            );
        }

        public static Resolution blocked(
                LocalDate eventDate,
                YearMonth eventMonth,
                YearMonth referenceFrom,
                YearMonth referenceTo,
                YearMonth discoveryThroughMonth,
                String reason,
                YearMonth period
        ) {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException(
                        "Vacation average primary blocker is required"
                );
            }
            return new Resolution(
                    eventDate,
                    eventMonth,
                    referenceFrom,
                    referenceTo,
                    discoveryThroughMonth,
                    false,
                    reason,
                    period,
                    null,
                    null,
                    null,
                    null
            );
        }
    }
}
