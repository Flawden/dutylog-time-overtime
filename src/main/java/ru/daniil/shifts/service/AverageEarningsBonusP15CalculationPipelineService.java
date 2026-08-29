package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

import static ru.daniil.shifts.service.AverageEarningsBonusP15Policy.ReferenceTimeAdjustment;

/**
 * 8A4F3G — end-to-end paragraph-15 calculation orchestrator.
 *
 * <p>The order is intentionally explicit and preserves the project boundary:
 * FACT discovery -> reference completeness POLICY -> P15 POLICY -> FORMULA ->
 * MONEY. The orchestrator itself performs no premium ratio arithmetic.</p>
 *
 * <p>Whole reference months proven to require no Payroll snapshot are accepted
 * by discovery and completeness as explicit historical zero-month authority.
 * They are not silently assigned a hypothetical schedule denominator. If an
 * included premium actually needs P15 proportional-time money, the pipeline
 * blocks until that denominator authority is supplied by a future explicit
 * rule.</p>
 */
@Service
public class AverageEarningsBonusP15CalculationPipelineService {

    public static final String NO_PAYROLL_NORM_AUTHORITY_UNRESOLVED =
            "PP_540_P15_REFERENCE_NO_PAYROLL_NORM_AUTHORITY_UNRESOLVED";
    public static final String AUTHORITY_WINDOW_MISMATCH =
            "PP_540_P15_PIPELINE_AUTHORITY_WINDOW_MISMATCH";
    public static final String CURRENCY_MISSING =
            "PP_540_P15_PIPELINE_CURRENCY_MISSING";

    private final AverageEarningsBonusP15HistoricalFactDiscoveryService discovery;
    private final AverageEarningsBonusP15ReferenceCompletenessService completeness;

    public AverageEarningsBonusP15CalculationPipelineService(
            AverageEarningsBonusP15HistoricalFactDiscoveryService discovery,
            AverageEarningsBonusP15ReferenceCompletenessService completeness
    ) {
        this.discovery = Objects.requireNonNull(
                discovery,
                "P15 historical fact discovery is required"
        );
        this.completeness = Objects.requireNonNull(
                completeness,
                "P15 reference completeness authority is required"
        );
    }

    @Transactional(readOnly = true)
    public Resolution calculate(
            AppUser user,
            LocalDate eventDate,
            YearMonth discoveryThroughMonth,
            List<YearMonth> provenNoPayrollMonths
    ) {
        return calculate(
                user,
                eventDate,
                AverageEarningsReferenceWindow.primary(eventDate),
                discoveryThroughMonth,
                provenNoPayrollMonths
        );
    }

    @Transactional(readOnly = true)
    public Resolution calculate(
            AppUser user,
            LocalDate eventDate,
            AverageEarningsReferenceWindow referenceWindow,
            YearMonth discoveryThroughMonth,
            List<YearMonth> provenNoPayrollMonths
    ) {
        Objects.requireNonNull(user, "P15 pipeline requires user");
        Objects.requireNonNull(eventDate, "P15 pipeline requires event date");
        Objects.requireNonNull(
                referenceWindow,
                "P15 pipeline requires reference window"
        ).requireEventDate(eventDate);
        Objects.requireNonNull(
                discoveryThroughMonth,
                "P15 pipeline requires discovery-through month"
        );
        Objects.requireNonNull(
                provenNoPayrollMonths,
                "P15 pipeline requires explicit no-Payroll proofs"
        );

        AverageEarningsLegalPolicy.requireRegime(eventDate);

        YearMonth eventMonth = referenceWindow.eventMonth();
        YearMonth referenceFrom = referenceWindow.referenceFrom();
        YearMonth referenceTo = referenceWindow.referenceTo();

        AverageEarningsBonusP15HistoricalFactDiscoveryService.Resolution historical =
                referenceWindow.primary()
                        ? discovery.resolve(
                                user,
                                eventDate,
                                discoveryThroughMonth,
                                provenNoPayrollMonths
                        )
                        : discovery.resolve(
                                user,
                                eventDate,
                                referenceWindow,
                                discoveryThroughMonth,
                                provenNoPayrollMonths
                        );

        if (!historical.ready()) {
            return Resolution.blocked(
                    eventDate,
                    eventMonth,
                    referenceFrom,
                    referenceTo,
                    discoveryThroughMonth,
                    BlockingStage.HISTORICAL_FACT_DISCOVERY,
                    historical.blockingReason(),
                    historical.blockingPeriod()
            );
        }

        if (!historical.eventDate().equals(eventDate)
                || !historical.eventMonth().equals(eventMonth)
                || !historical.referenceFrom().equals(referenceFrom)
                || !historical.referenceTo().equals(referenceTo)
                || !historical.discoveryThroughMonth().equals(discoveryThroughMonth)) {
            return Resolution.blocked(
                    eventDate,
                    eventMonth,
                    referenceFrom,
                    referenceTo,
                    discoveryThroughMonth,
                    BlockingStage.HISTORICAL_FACT_DISCOVERY,
                    AUTHORITY_WINDOW_MISMATCH,
                    null
            );
        }

        List<YearMonth> referenceNoPayrollMonths =
                provenNoPayrollMonths.stream()
                        .filter(Objects::nonNull)
                        .filter(month -> !month.isBefore(referenceFrom) && !month.isAfter(referenceTo))
                        .toList();

        AverageEarningsBonusP15ReferenceCompletenessService.Resolution reference =
                referenceWindow.primary()
                        ? completeness.resolve(
                                user,
                                eventDate,
                                referenceNoPayrollMonths
                        )
                        : completeness.resolve(
                                user,
                                eventDate,
                                referenceWindow,
                                referenceNoPayrollMonths
                        );

        if (!reference.ready()) {
            return Resolution.blocked(
                    eventDate,
                    eventMonth,
                    referenceFrom,
                    referenceTo,
                    discoveryThroughMonth,
                    BlockingStage.REFERENCE_COMPLETENESS,
                    reference.blockingReason(),
                    reference.blockingPeriod()
            );
        }

        if (!reference.eventDate().equals(eventDate)
                || !reference.eventMonth().equals(eventMonth)
                || !reference.referenceFrom().equals(referenceFrom)
                || !reference.referenceTo().equals(referenceTo)) {
            return Resolution.blocked(
                    eventDate,
                    eventMonth,
                    referenceFrom,
                    referenceTo,
                    discoveryThroughMonth,
                    BlockingStage.REFERENCE_COMPLETENESS,
                    AUTHORITY_WINDOW_MISMATCH,
                    null
            );
        }

        AverageEarningsBonusP15Policy.Resolution policy =
                AverageEarningsBonusP15Policy.resolve(
                        eventDate,
                        referenceFrom,
                        referenceTo,
                        reference.referencePeriodFullyWorked(),
                        historical.policyFacts()
                );

        if (!policy.ready()) {
            return Resolution.blocked(
                    eventDate,
                    eventMonth,
                    referenceFrom,
                    referenceTo,
                    discoveryThroughMonth,
                    BlockingStage.P15_POLICY,
                    policy.blockingReason(),
                    null
            );
        }

        boolean proportionalMoneyRequired =
                policy.decisions().stream()
                        .anyMatch(decision ->
                                decision.included()
                                        && decision.referenceTimeAdjustment()
                                        == ReferenceTimeAdjustment.PROPORTIONAL_TO_REFERENCE_WORKED_TIME
                        );

        if (proportionalMoneyRequired
                && !reference.proportionalNormAuthorityComplete()) {
            YearMonth firstMissingAuthority =
                    reference.noPayrollMonths().isEmpty()
                            ? null
                            : reference.noPayrollMonths().get(0);

            return Resolution.blocked(
                    eventDate,
                    eventMonth,
                    referenceFrom,
                    referenceTo,
                    discoveryThroughMonth,
                    BlockingStage.REFERENCE_COMPLETENESS,
                    NO_PAYROLL_NORM_AUTHORITY_UNRESOLVED,
                    firstMissingAuthority
            );
        }

        AverageEarningsBonusP15Formula.Calculation money =
                AverageEarningsBonusP15Formula.calculate(
                        referenceFrom,
                        referenceTo,
                        policy,
                        reference.referenceWorkedTime()
                );

        if (!money.ready()) {
            return Resolution.blocked(
                    eventDate,
                    eventMonth,
                    referenceFrom,
                    referenceTo,
                    discoveryThroughMonth,
                    BlockingStage.P15_FORMULA,
                    money.blockingReason(),
                    null
            );
        }

        if (money.includedPremiumAmountMinor() > 0L
                && historical.currencyCode() == null) {
            return Resolution.blocked(
                    eventDate,
                    eventMonth,
                    referenceFrom,
                    referenceTo,
                    discoveryThroughMonth,
                    BlockingStage.HISTORICAL_FACT_DISCOVERY,
                    CURRENCY_MISSING,
                    null
            );
        }

        return Resolution.ready(
                eventDate,
                eventMonth,
                referenceFrom,
                referenceTo,
                discoveryThroughMonth,
                historical.currencyCode(),
                reference,
                policy,
                money
        );
    }

    public enum BlockingStage {
        HISTORICAL_FACT_DISCOVERY,
        REFERENCE_COMPLETENESS,
        P15_POLICY,
        P15_FORMULA
    }

    public record Resolution(
            LocalDate eventDate,
            YearMonth eventMonth,
            YearMonth referenceFrom,
            YearMonth referenceTo,
            YearMonth discoveryThroughMonth,
            boolean ready,
            BlockingStage blockingStage,
            String blockingReason,
            YearMonth blockingPeriod,
            String currencyCode,
            AverageEarningsBonusP15ReferenceCompletenessService.Resolution referenceCompleteness,
            AverageEarningsBonusP15Policy.Resolution policy,
            AverageEarningsBonusP15Formula.Calculation calculation
    ) {
        public Resolution {
            Objects.requireNonNull(eventDate, "P15 pipeline event date is required");
            Objects.requireNonNull(eventMonth, "P15 pipeline event month is required");
            Objects.requireNonNull(referenceFrom, "P15 pipeline reference start is required");
            Objects.requireNonNull(referenceTo, "P15 pipeline reference end is required");
            Objects.requireNonNull(
                    discoveryThroughMonth,
                    "P15 pipeline discovery-through month is required"
            );

            if (!eventMonth.equals(YearMonth.from(eventDate))) {
                throw new IllegalArgumentException(
                        "P15 pipeline event month does not match legal event date"
                );
            }
            new AverageEarningsReferenceWindow(eventMonth, referenceFrom, referenceTo);
            if (discoveryThroughMonth.isBefore(referenceTo)) {
                throw new IllegalArgumentException(
                        "P15 pipeline discovery cannot end before selected reference period"
                );
            }
            if (ready == (blockingReason != null)) {
                throw new IllegalArgumentException(
                        "P15 pipeline resolution state is invalid"
                );
            }

            if (ready) {
                if (blockingStage != null
                        || blockingPeriod != null
                        || referenceCompleteness == null
                        || !referenceCompleteness.ready()
                        || policy == null
                        || !policy.ready()
                        || calculation == null
                        || !calculation.ready()
                        || (currencyCode != null && !currencyCode.matches("[A-Z]{3}"))) {
                    throw new IllegalArgumentException(
                            "Ready P15 pipeline resolution is incomplete"
                    );
                }
            } else {
                if (blockingStage == null
                        || currencyCode != null
                        || referenceCompleteness != null
                        || policy != null
                        || calculation != null) {
                    throw new IllegalArgumentException(
                            "Blocked P15 pipeline cannot expose partial policy or money"
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
                AverageEarningsBonusP15ReferenceCompletenessService.Resolution referenceCompleteness,
                AverageEarningsBonusP15Policy.Resolution policy,
                AverageEarningsBonusP15Formula.Calculation calculation
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
                    null,
                    currencyCode,
                    referenceCompleteness,
                    policy,
                    calculation
            );
        }

        public static Resolution blocked(
                LocalDate eventDate,
                YearMonth eventMonth,
                YearMonth referenceFrom,
                YearMonth referenceTo,
                YearMonth discoveryThroughMonth,
                BlockingStage blockingStage,
                String reason,
                YearMonth blockingPeriod
        ) {
            Objects.requireNonNull(blockingStage, "P15 pipeline blocking stage is required");
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException(
                        "P15 pipeline blocking reason is required"
                );
            }
            return new Resolution(
                    eventDate,
                    eventMonth,
                    referenceFrom,
                    referenceTo,
                    discoveryThroughMonth,
                    false,
                    blockingStage,
                    reason,
                    blockingPeriod,
                    null,
                    null,
                    null,
                    null
            );
        }
    }
}
