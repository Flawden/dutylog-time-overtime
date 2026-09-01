package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.ProductionCalendarMonthDto;
import ru.daniil.shifts.model.AppUser;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Paragraph-8 formula-basis authority for calendar-day vacation average pay.
 *
 * <p>The upstream paragraph-8 service proves the exact established compensation
 * identity for the event month. This service adds only the explicit formula
 * basis required by {@link VacationAverageUnifiedDailyResolver}.</p>
 *
 * <p>Monthly official salary requires no production-calendar inference and is
 * mapped directly to the already-defined salary/29.3 policy. Hourly tariff
 * requires a complete production-calendar norm for every month of the event
 * calendar year; the annual norm is the exact sum of those twelve monthly
 * production norms. Missing schedule coverage fails closed.</p>
 *
 * <p>This layer does not decide whether paragraph 8 applies, does not calculate
 * average-daily money and does not calculate final vacation-pay money.</p>
 */
@Service
public class AverageEarningsParagraph8VacationFormulaBasisAuthorityService {

    public static final String MONTHLY_SALARY_RULE_ID =
            "PP_540_P8_P10_MONTHLY_SALARY_BASIS";
    public static final String HOURLY_TARIFF_RULE_ID =
            "PP_540_P8_P10_HOURLY_ANNUAL_NORM_BASIS";

    public static final String UPSTREAM_AUTHORITY_REQUIRED =
            "PP_540_P8_FORMULA_UPSTREAM_AUTHORITY_REQUIRED";
    public static final String UPSTREAM_IDENTITY_MISMATCH =
            "PP_540_P8_FORMULA_UPSTREAM_IDENTITY_MISMATCH";
    public static final String ANNUAL_NORM_MONTH_IDENTITY_MISMATCH =
            "PP_540_P8_FORMULA_ANNUAL_NORM_MONTH_IDENTITY_MISMATCH";
    public static final String ANNUAL_NORM_SCHEDULE_COVERAGE_INCOMPLETE =
            "PP_540_P8_FORMULA_ANNUAL_NORM_SCHEDULE_COVERAGE_INCOMPLETE";
    public static final String ANNUAL_NORM_NON_POSITIVE =
            "PP_540_P8_FORMULA_ANNUAL_NORM_NON_POSITIVE";

    private final ProductionCalendarService productionCalendar;

    public AverageEarningsParagraph8VacationFormulaBasisAuthorityService(
            ProductionCalendarService productionCalendar
    ) {
        this.productionCalendar = Objects.requireNonNull(
                productionCalendar,
                "Paragraph-8 formula basis requires production calendar"
        );
    }

    @Transactional(readOnly = true)
    public Resolution resolve(
            AppUser user,
            LocalDate eventDate,
            AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution
                    paragraph8Authority
    ) {
        Objects.requireNonNull(
                user,
                "Paragraph-8 formula basis requires user"
        );
        Objects.requireNonNull(
                eventDate,
                "Paragraph-8 formula basis requires event date"
        );

        AverageEarningsLegalPolicy.requireRegime(eventDate);
        YearMonth eventMonth = YearMonth.from(eventDate);

        if (paragraph8Authority == null || !paragraph8Authority.ready()) {
            return Resolution.blocked(
                    eventDate,
                    UPSTREAM_AUTHORITY_REQUIRED,
                    "Paragraph-8 formula basis requires ready established tariff/salary authority"
            );
        }

        if (!eventDate.equals(paragraph8Authority.eventDate())
                || !eventMonth.equals(paragraph8Authority.eventMonth())) {
            return Resolution.blocked(
                    eventDate,
                    UPSTREAM_IDENTITY_MISMATCH,
                    "Paragraph-8 established compensation identity does not match formula event"
            );
        }

        if (paragraph8Authority.establishedBasis()
                == AverageEarningsParagraph8TariffSalaryAuthorityService
                        .EstablishedBasis.MONTHLY_OFFICIAL_SALARY) {
            VacationAverageUnifiedDailyResolver.Paragraph8FormulaBasis basis =
                    VacationAverageUnifiedDailyResolver.Paragraph8FormulaBasis
                            .monthlySalary(
                                    eventDate,
                                    paragraph8Authority.currencyCode(),
                                    MONTHLY_SALARY_RULE_ID
                            );

            return Resolution.ready(
                    eventDate,
                    paragraph8Authority.establishedBasis(),
                    paragraph8Authority.currencyCode(),
                    null,
                    List.of(),
                    basis
            );
        }

        if (paragraph8Authority.establishedBasis()
                != AverageEarningsParagraph8TariffSalaryAuthorityService
                        .EstablishedBasis.HOURLY_TARIFF_RATE) {
            return Resolution.blocked(
                    eventDate,
                    UPSTREAM_IDENTITY_MISMATCH,
                    "Paragraph-8 formula basis received unsupported established basis"
            );
        }

        int eventYear = eventDate.getYear();
        long annualNormMinutes = 0L;
        List<MonthNormFact> months = new ArrayList<>(12);

        for (int monthNumber = 1; monthNumber <= 12; monthNumber++) {
            YearMonth month = YearMonth.of(eventYear, monthNumber);
            ProductionCalendarMonthDto calendar = Objects.requireNonNull(
                    productionCalendar.month(
                            user,
                            month.toString()
                    ),
                    "Paragraph-8 production calendar returned null month"
            );

            if (!month.toString().equals(calendar.month())) {
                return Resolution.blocked(
                        eventDate,
                        ANNUAL_NORM_MONTH_IDENTITY_MISMATCH,
                        "Paragraph-8 production-calendar month identity mismatch"
                );
            }

            if (!calendar.scheduleCoverageComplete()
                    || calendar.scheduleCoverageDays() != month.lengthOfMonth()) {
                return Resolution.blocked(
                        eventDate,
                        ANNUAL_NORM_SCHEDULE_COVERAGE_INCOMPLETE,
                        "Paragraph-8 hourly annual norm requires complete schedule coverage for "
                                + month
                );
            }

            int productionNormMinutes = calendar.productionNormMinutes();
            if (productionNormMinutes < 0) {
                throw new IllegalStateException(
                        "Paragraph-8 production-calendar norm cannot be negative: " + month
                );
            }

            annualNormMinutes = Math.addExact(
                    annualNormMinutes,
                    productionNormMinutes
            );

            months.add(
                    new MonthNormFact(
                            month,
                            productionNormMinutes,
                            calendar.scheduleCoverageDays()
                    )
            );
        }

        if (annualNormMinutes <= 0L) {
            return Resolution.blocked(
                    eventDate,
                    ANNUAL_NORM_NON_POSITIVE,
                    "Paragraph-8 hourly annual production norm must be positive"
            );
        }

        VacationAverageUnifiedDailyResolver.Paragraph8FormulaBasis basis =
                VacationAverageUnifiedDailyResolver.Paragraph8FormulaBasis
                        .hourlyTariff(
                                eventDate,
                                paragraph8Authority.currencyCode(),
                                HOURLY_TARIFF_RULE_ID,
                                annualNormMinutes
                        );

        return Resolution.ready(
                eventDate,
                paragraph8Authority.establishedBasis(),
                paragraph8Authority.currencyCode(),
                annualNormMinutes,
                months,
                basis
        );
    }

    public record MonthNormFact(
            YearMonth month,
            int productionNormMinutes,
            int scheduleCoverageDays
    ) {
        public MonthNormFact {
            Objects.requireNonNull(
                    month,
                    "Paragraph-8 month norm requires month"
            );
            if (productionNormMinutes < 0
                    || scheduleCoverageDays != month.lengthOfMonth()) {
                throw new IllegalArgumentException(
                        "Paragraph-8 month norm fact is incomplete"
                );
            }
        }
    }

    public record Resolution(
            LocalDate eventDate,
            YearMonth eventMonth,
            boolean ready,
            String blockingReason,
            String blockingMessage,
            AverageEarningsParagraph8TariffSalaryAuthorityService.EstablishedBasis
                    establishedBasis,
            String currencyCode,
            Long annualNormMinutes,
            List<MonthNormFact> annualNormMonths,
            VacationAverageUnifiedDailyResolver.Paragraph8FormulaBasis basis
    ) {
        public Resolution {
            Objects.requireNonNull(
                    eventDate,
                    "Paragraph-8 formula result requires event date"
            );
            Objects.requireNonNull(
                    eventMonth,
                    "Paragraph-8 formula result requires event month"
            );
            annualNormMonths = List.copyOf(Objects.requireNonNull(
                    annualNormMonths,
                    "Paragraph-8 formula result requires norm provenance"
            ));

            if (!eventMonth.equals(YearMonth.from(eventDate))) {
                throw new IllegalArgumentException(
                        "Paragraph-8 formula result event identity is invalid"
                );
            }

            if (ready) {
                if (blockingReason != null
                        || blockingMessage != null
                        || establishedBasis == null
                        || currencyCode == null
                        || !currencyCode.matches("[A-Z]{3}")
                        || basis == null
                        || basis.establishedBasis() != establishedBasis
                        || !basis.currencyCode().equals(currencyCode)
                        || !basis.eventDate().equals(eventDate)) {
                    throw new IllegalArgumentException(
                            "Ready paragraph-8 formula result is incomplete"
                    );
                }

                if (establishedBasis
                        == AverageEarningsParagraph8TariffSalaryAuthorityService
                                .EstablishedBasis.MONTHLY_OFFICIAL_SALARY) {
                    if (annualNormMinutes != null
                            || !annualNormMonths.isEmpty()
                            || !MONTHLY_SALARY_RULE_ID.equals(basis.authorityCode())) {
                        throw new IllegalArgumentException(
                                "Monthly salary formula result has invalid annual-norm provenance"
                        );
                    }
                } else if (establishedBasis
                        == AverageEarningsParagraph8TariffSalaryAuthorityService
                                .EstablishedBasis.HOURLY_TARIFF_RATE) {
                    if (annualNormMinutes == null
                            || annualNormMinutes <= 0L
                            || annualNormMonths.size() != 12
                            || !HOURLY_TARIFF_RULE_ID.equals(basis.authorityCode())
                            || !annualNormMinutes.equals(basis.annualNormMinutes())) {
                        throw new IllegalArgumentException(
                                "Hourly tariff formula result has invalid annual-norm provenance"
                        );
                    }
                } else {
                    throw new IllegalArgumentException(
                            "Ready paragraph-8 formula result has unsupported basis"
                    );
                }
            } else {
                if (blockingReason == null
                        || blockingReason.isBlank()
                        || blockingMessage == null
                        || blockingMessage.isBlank()
                        || establishedBasis != null
                        || currencyCode != null
                        || annualNormMinutes != null
                        || !annualNormMonths.isEmpty()
                        || basis != null) {
                    throw new IllegalArgumentException(
                            "Blocked paragraph-8 formula result cannot expose partial basis"
                    );
                }
            }
        }

        static Resolution ready(
                LocalDate eventDate,
                AverageEarningsParagraph8TariffSalaryAuthorityService.EstablishedBasis
                        establishedBasis,
                String currencyCode,
                Long annualNormMinutes,
                List<MonthNormFact> annualNormMonths,
                VacationAverageUnifiedDailyResolver.Paragraph8FormulaBasis basis
        ) {
            return new Resolution(
                    eventDate,
                    YearMonth.from(eventDate),
                    true,
                    null,
                    null,
                    establishedBasis,
                    currencyCode,
                    annualNormMinutes,
                    annualNormMonths,
                    basis
            );
        }

        static Resolution blocked(
                LocalDate eventDate,
                String blockingReason,
                String blockingMessage
        ) {
            return new Resolution(
                    eventDate,
                    YearMonth.from(eventDate),
                    false,
                    blockingReason,
                    blockingMessage,
                    null,
                    null,
                    null,
                    List.of(),
                    null
            );
        }
    }
}
