package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.ProductionCalendarMonthDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.CompensationTerm;
import ru.daniil.shifts.repo.CompensationTermRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Resolves the historical ordinary hourly value attached to one factual
 * source-work date.
 *
 * Valuation identity is SOURCE WORK MONTH, not settlement date.
 *
 * CompensationTerm remains effective-month:
 * the lookup boundary is the first day of sourceDate's month.
 *
 * HOURLY:
 *     configured hourly rate.
 *
 * SALARY:
 *     monthly salary * 60 / production norm of the source month.
 *
 * No overtime/night/holiday premium and no settlement money is calculated
 * here.
 */
@Service
public class HistoricalCompensationRateService {

    private final CompensationTermRepository terms;
    private final ProductionCalendarService productionCalendar;
    private final CompensationCalculationService calculation;

    public HistoricalCompensationRateService(
            CompensationTermRepository terms,
            ProductionCalendarService productionCalendar,
            CompensationCalculationService calculation
    ) {
        this.terms = terms;
        this.productionCalendar = productionCalendar;
        this.calculation = calculation;
    }

    @Transactional(readOnly = true)
    public HistoricalBaseRate resolve(
            AppUser user,
            LocalDate sourceDate
    ) {
        if (user == null) {
            throw new IllegalArgumentException(
                    "Historical compensation rate requires user"
            );
        }

        if (sourceDate == null) {
            throw new IllegalArgumentException(
                    "Historical compensation rate requires source date"
            );
        }

        YearMonth sourceMonth =
                YearMonth.from(
                        sourceDate
                );

        /*
         * Compensation history is month-based by contract.
         *
         * Even though repository storage uses LocalDate, valuation intentionally
         * resolves against the first day of the factual source month.
         */
        LocalDate compensationBoundary =
                sourceMonth.atDay(1);

        CompensationTerm term =
                terms
                        .findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                                user,
                                compensationBoundary
                        )
                        .orElseThrow(() ->
                                ApiException.conflict(
                                        "PAYROLL_COMPENSATION_REQUIRED",
                                        "Для месяца "
                                                + sourceMonth
                                                + " не настроен способ оплаты"
                                )
                        );

        String currency =
                term.getCurrencyCode();

        if (currency == null
                || !currency.matches(
                        "[A-Z]{3}"
                )) {
            throw new IllegalStateException(
                    "Historical compensation term has invalid currency"
            );
        }

        String mode =
                term.getPayMode();

        if ("HOURLY".equals(mode)) {
            long hourly =
                    calculation
                            .effectiveHourlyRateMinor(
                                    term,
                                    0
                            );

            return new HistoricalBaseRate(
                    sourceDate,
                    sourceMonth,
                    term.getEffectiveFrom(),
                    mode,
                    currency,
                    hourly,
                    null
            );
        }

        if (!"SALARY".equals(mode)) {
            throw ApiException.badRequest(
                    "PAYROLL_MODE_INVALID",
                    "Некорректный способ оплаты"
            );
        }

        ProductionCalendarMonthDto production =
                productionCalendar.month(
                        user,
                        sourceMonth.toString()
                );

        /*
         * Same fail-closed salary denominator policy as Native Payroll.
         *
         * A partial month schedule cannot define a stable salary-derived
         * hourly value.
         */
        if (!production
                .scheduleCoverageComplete()) {
            throw ApiException.conflict(
                    "PAYROLL_PRODUCTION_NORM_INCOMPLETE",
                    "Для оклада сначала заполни график на весь расчётный месяц "
                            + sourceMonth
            );
        }

        if (production
                .productionNormMinutes()
                <= 0) {
            throw ApiException.conflict(
                    "PAYROLL_PRODUCTION_NORM_REQUIRED",
                    "Для оклада нужна положительная расчётная норма месяца "
                            + sourceMonth
            );
        }

        long hourly =
                calculation
                        .effectiveHourlyRateMinor(
                                term,
                                production
                                        .productionNormMinutes()
                        );

        return new HistoricalBaseRate(
                sourceDate,
                sourceMonth,
                term.getEffectiveFrom(),
                mode,
                currency,
                hourly,
                production
                        .productionNormMinutes()
        );
    }

    public record HistoricalBaseRate(
            LocalDate sourceDate,
            YearMonth sourceMonth,
            LocalDate compensationEffectiveFrom,
            String payMode,
            String currencyCode,
            long baseHourlyRateMinor,
            Integer productionNormMinutes
    ) {
        public HistoricalBaseRate {
            if (sourceDate == null
                    || sourceMonth == null
                    || compensationEffectiveFrom == null
                    || payMode == null
                    || currencyCode == null
                    || baseHourlyRateMinor <= 0) {
                throw new IllegalArgumentException(
                        "Historical base-rate identity is incomplete"
                );
            }

            if (!sourceMonth.equals(
                    YearMonth.from(
                            sourceDate
                    )
            )) {
                throw new IllegalArgumentException(
                        "Historical base-rate month disagrees with source date"
                );
            }

            if (compensationEffectiveFrom.isAfter(
                    sourceMonth.atDay(1)
            )) {
                throw new IllegalArgumentException(
                        "Compensation term cannot become effective after source month boundary"
                );
            }

            if ("SALARY".equals(
                    payMode
            )) {
                if (productionNormMinutes == null
                        || productionNormMinutes <= 0) {
                    throw new IllegalArgumentException(
                            "Salary historical rate requires positive production norm"
                    );
                }
            } else if ("HOURLY".equals(
                    payMode
            )) {
                if (productionNormMinutes != null) {
                    throw new IllegalArgumentException(
                            "Hourly historical rate must not carry salary production norm"
                    );
                }
            } else {
                throw new IllegalArgumentException(
                        "Unsupported historical pay mode"
                );
            }
        }
    }
}
