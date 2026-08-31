package ru.daniil.shifts.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.daniil.shifts.model.AppUser;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Canonical annual-vacation pay orchestration boundary.
 *
 * <p>L first proves the exact posted annual-paid-vacation payable-day FACT.
 * Only when L is ready does N delegate the already-selected J5 branch and its
 * lazy suppliers to K. Only when K is ready does N invoke M for final MONEY.
 * N owns ordering and fail-closed provenance only; it does not reimplement any
 * calendar, fallback, exact-daily or monetary formula policy.</p>
 */
@Service
public class VacationPayOrchestrator {
    public static final String RULE_ID = "DUTYLOG_VACATION_PAY_ORCHESTRATOR";
    public static final String PAYABLE_DAYS_AUTHORITY_BLOCKED =
            "DUTYLOG_VACATION_PAY_ORCHESTRATOR_PAYABLE_DAYS_AUTHORITY_BLOCKED";
    public static final String DAILY_AUTHORITY_BLOCKED =
            "DUTYLOG_VACATION_PAY_ORCHESTRATOR_DAILY_AUTHORITY_BLOCKED";
    public static final String MONEY_FORMULA_BLOCKED =
            "DUTYLOG_VACATION_PAY_ORCHESTRATOR_MONEY_FORMULA_BLOCKED";

    private final VacationPayableDaysFactService payableDays;
    private final DailyResolver dailyResolver;
    private final MoneyCalculator moneyCalculator;

    @Autowired
    public VacationPayOrchestrator(VacationPayableDaysFactService payableDays) {
        this(
                payableDays,
                VacationAverageUnifiedDailyResolver::resolve,
                VacationPayMoneyFormula::calculate
        );
    }

    VacationPayOrchestrator(
            VacationPayableDaysFactService payableDays,
            DailyResolver dailyResolver,
            MoneyCalculator moneyCalculator
    ) {
        this.payableDays = Objects.requireNonNull(
                payableDays,
                "Vacation pay orchestration requires L payable-days authority"
        );
        this.dailyResolver = Objects.requireNonNull(
                dailyResolver,
                "Vacation pay orchestration requires K exact-daily resolver"
        );
        this.moneyCalculator = Objects.requireNonNull(
                moneyCalculator,
                "Vacation pay orchestration requires M money formula"
        );
    }

    public Resolution resolve(
            AppUser user,
            LocalDate eventDate,
            Long absencePeriodId,
            AverageEarningsOrderedFallbackResolver.Resolution orderedFallback,
            Supplier<VacationAveragePrimaryCalculationService.Resolution>
                    referenceCalculationSupplier,
            Supplier<VacationAverageUnifiedDailyResolver.Paragraph7CalendarBasis>
                    paragraph7CalendarBasisSupplier,
            Supplier<VacationAverageUnifiedDailyResolver.Paragraph8FormulaBasis>
                    paragraph8FormulaBasisSupplier
    ) {
        Objects.requireNonNull(user, "Vacation pay orchestration requires user");
        Objects.requireNonNull(eventDate, "Vacation pay orchestration requires event date");

        VacationPayableDaysFactService.Resolution payableDaysAuthority =
                payableDays.resolve(user, eventDate, absencePeriodId);
        Objects.requireNonNull(
                payableDaysAuthority,
                "L payable-days authority returned null resolution"
        );
        if (!payableDaysAuthority.ready()) {
            return Resolution.blocked(
                    eventDate,
                    absencePeriodId,
                    BlockingStage.PAYABLE_DAYS_AUTHORITY,
                    PAYABLE_DAYS_AUTHORITY_BLOCKED,
                    normalizedUpstreamReason(payableDaysAuthority.blockingReason()),
                    "L payable-vacation-days authority is blocked",
                    payableDaysAuthority,
                    null,
                    null
            );
        }

        Objects.requireNonNull(
                orderedFallback,
                "Vacation pay orchestration requires already-selected J5 resolution"
        );
        Objects.requireNonNull(
                referenceCalculationSupplier,
                "Vacation pay orchestration requires K reference calculation supplier"
        );
        Objects.requireNonNull(
                paragraph7CalendarBasisSupplier,
                "Vacation pay orchestration requires K paragraph-7 calendar supplier"
        );
        Objects.requireNonNull(
                paragraph8FormulaBasisSupplier,
                "Vacation pay orchestration requires K paragraph-8 formula supplier"
        );

        VacationAverageUnifiedDailyResolver.Resolution dailyAuthority =
                dailyResolver.resolve(
                        orderedFallback,
                        referenceCalculationSupplier,
                        paragraph7CalendarBasisSupplier,
                        paragraph8FormulaBasisSupplier
                );
        Objects.requireNonNull(
                dailyAuthority,
                "K exact average-daily resolver returned null resolution"
        );
        if (!dailyAuthority.ready()) {
            return Resolution.blocked(
                    eventDate,
                    absencePeriodId,
                    BlockingStage.DAILY_AUTHORITY,
                    DAILY_AUTHORITY_BLOCKED,
                    normalizedUpstreamReason(dailyAuthority.blockingReason()),
                    "K exact average-daily authority is blocked",
                    payableDaysAuthority,
                    dailyAuthority,
                    null
            );
        }

        VacationPayMoneyFormula.Resolution moneyAuthority =
                moneyCalculator.calculate(dailyAuthority, payableDaysAuthority);
        Objects.requireNonNull(
                moneyAuthority,
                "M vacation-pay money formula returned null resolution"
        );
        if (!moneyAuthority.ready()) {
            return Resolution.blocked(
                    eventDate,
                    absencePeriodId,
                    BlockingStage.MONEY_FORMULA,
                    MONEY_FORMULA_BLOCKED,
                    normalizedUpstreamReason(moneyAuthority.blockingReason()),
                    "M final vacation-pay money formula is blocked",
                    payableDaysAuthority,
                    dailyAuthority,
                    moneyAuthority
            );
        }

        return Resolution.ready(
                eventDate,
                absencePeriodId,
                payableDaysAuthority,
                dailyAuthority,
                moneyAuthority
        );
    }

    private static String normalizedUpstreamReason(String reason) {
        return reason == null || reason.isBlank() ? null : reason;
    }

    @FunctionalInterface
    interface DailyResolver {
        VacationAverageUnifiedDailyResolver.Resolution resolve(
                AverageEarningsOrderedFallbackResolver.Resolution orderedFallback,
                Supplier<VacationAveragePrimaryCalculationService.Resolution>
                        referenceCalculationSupplier,
                Supplier<VacationAverageUnifiedDailyResolver.Paragraph7CalendarBasis>
                        paragraph7CalendarBasisSupplier,
                Supplier<VacationAverageUnifiedDailyResolver.Paragraph8FormulaBasis>
                        paragraph8FormulaBasisSupplier
        );
    }

    @FunctionalInterface
    interface MoneyCalculator {
        VacationPayMoneyFormula.Resolution calculate(
                VacationAverageUnifiedDailyResolver.Resolution dailyAuthority,
                VacationPayableDaysFactService.Resolution payableDaysAuthority
        );
    }

    public enum BlockingStage {
        PAYABLE_DAYS_AUTHORITY,
        DAILY_AUTHORITY,
        MONEY_FORMULA
    }

    public record Resolution(
            LocalDate eventDate,
            YearMonth eventMonth,
            Long requestedAbsencePeriodId,
            boolean ready,
            BlockingStage blockingStage,
            String blockingReason,
            String upstreamBlockingReason,
            String blockingMessage,
            VacationPayableDaysFactService.Resolution payableDaysAuthority,
            VacationAverageUnifiedDailyResolver.Resolution dailyAuthority,
            VacationPayMoneyFormula.Resolution moneyAuthority
    ) {
        public Resolution {
            Objects.requireNonNull(eventDate, "Vacation pay result requires event date");
            Objects.requireNonNull(eventMonth, "Vacation pay result requires event month");
            Objects.requireNonNull(
                    payableDaysAuthority,
                    "Vacation pay result requires L provenance"
            );
            if (!eventMonth.equals(YearMonth.from(eventDate))) {
                throw new IllegalArgumentException(
                        "Vacation pay result event identity is invalid"
                );
            }
            if (ready == (blockingReason != null)) {
                throw new IllegalArgumentException("Vacation pay result state is invalid");
            }
            if (ready) {
                if (blockingStage != null
                        || upstreamBlockingReason != null
                        || blockingMessage != null
                        || dailyAuthority == null
                        || moneyAuthority == null
                        || !payableDaysAuthority.ready()
                        || !dailyAuthority.ready()
                        || !moneyAuthority.ready()) {
                    throw new IllegalArgumentException(
                            "Ready vacation pay result has incomplete authority chain"
                    );
                }
            } else {
                if (blockingStage == null
                        || blockingMessage == null
                        || blockingMessage.isBlank()) {
                    throw new IllegalArgumentException(
                            "Blocked vacation pay result requires orchestration blocker"
                    );
                }
                if (blockingStage == BlockingStage.PAYABLE_DAYS_AUTHORITY) {
                    if (dailyAuthority != null || moneyAuthority != null) {
                        throw new IllegalArgumentException(
                                "L-blocked vacation pay result cannot expose downstream authority"
                        );
                    }
                } else if (blockingStage == BlockingStage.DAILY_AUTHORITY) {
                    if (dailyAuthority == null || moneyAuthority != null) {
                        throw new IllegalArgumentException(
                                "K-blocked vacation pay result has invalid provenance"
                        );
                    }
                } else if (dailyAuthority == null || moneyAuthority == null) {
                    throw new IllegalArgumentException(
                            "M-blocked vacation pay result has invalid provenance"
                    );
                }
            }
        }

        static Resolution ready(
                LocalDate eventDate,
                Long requestedAbsencePeriodId,
                VacationPayableDaysFactService.Resolution payableDaysAuthority,
                VacationAverageUnifiedDailyResolver.Resolution dailyAuthority,
                VacationPayMoneyFormula.Resolution moneyAuthority
        ) {
            return new Resolution(
                    eventDate,
                    YearMonth.from(eventDate),
                    requestedAbsencePeriodId,
                    true,
                    null,
                    null,
                    null,
                    null,
                    payableDaysAuthority,
                    dailyAuthority,
                    moneyAuthority
            );
        }

        static Resolution blocked(
                LocalDate eventDate,
                Long requestedAbsencePeriodId,
                BlockingStage blockingStage,
                String blockingReason,
                String upstreamBlockingReason,
                String blockingMessage,
                VacationPayableDaysFactService.Resolution payableDaysAuthority,
                VacationAverageUnifiedDailyResolver.Resolution dailyAuthority,
                VacationPayMoneyFormula.Resolution moneyAuthority
        ) {
            Objects.requireNonNull(blockingStage, "Vacation pay blocker stage is required");
            if (blockingReason == null || blockingReason.isBlank()) {
                throw new IllegalArgumentException("Vacation pay blocker reason is required");
            }
            return new Resolution(
                    eventDate,
                    YearMonth.from(eventDate),
                    requestedAbsencePeriodId,
                    false,
                    blockingStage,
                    blockingReason,
                    upstreamBlockingReason,
                    blockingMessage,
                    payableDaysAuthority,
                    dailyAuthority,
                    moneyAuthority
            );
        }

        public String currencyCode() {
            return ready ? moneyAuthority.currencyCode() : null;
        }

        public Long vacationPayMinor() {
            return ready ? moneyAuthority.vacationPayMinor() : null;
        }

        public int payableCalendarDays() {
            return ready ? payableDaysAuthority.payableCalendarDays() : 0;
        }
    }
}
