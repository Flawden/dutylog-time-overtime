package ru.daniil.shifts.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

@Service
public class AverageEarningsParagraph7PreEventAccruedWageAuthorityService {

    private final BaseAuthorityResolver baseAuthorityResolver;
    private final BaseFormulaCalculator baseFormulaCalculator;
    private final SemanticResolver semanticResolver;
    private final OrdinaryResolver ordinaryResolver;
    private final HarmfulResolver harmfulResolver;
    private final BonusFactResolver bonusFactResolver;
    private final BonusAccrualResolver bonusAccrualResolver;
    private final BonusPolicyResolver bonusPolicyResolver;
    private final BonusWorkTimeResolver bonusWorkTimeResolver;
    private final BonusFormulaCalculator bonusFormulaCalculator;
    private final FinalResolver finalResolver;

    @Autowired
    public AverageEarningsParagraph7PreEventAccruedWageAuthorityService(
            AverageEarningsParagraph7PreEventBasePayAuthorityService baseAuthority,
            AverageEarningsParagraph7PreEventSemanticWageFactService semanticFacts,
            AverageEarningsParagraph7PreEventOrdinaryPremiumService ordinaryPremium,
            AverageEarningsParagraph7PreEventHarmfulCompensationService harmfulCompensation,
            AverageEarningsParagraph7PreEventBonusP15FactService bonusFacts,
            AverageEarningsParagraph7PreEventBonusAccrualAuthorityService bonusAccrual,
            AverageEarningsParagraph7PreEventBonusWorkTimeFactService bonusWorkTime
    ) {
        this(
                baseAuthority::resolve,
                AverageEarningsParagraph7PreEventBasePayFormula::calculate,
                semanticFacts::resolve,
                ordinaryPremium::resolve,
                harmfulCompensation::resolve,
                bonusFacts::resolve,
                bonusAccrual::resolve,
                AverageEarningsParagraph7PreEventBonusP15Policy::resolve,
                bonusWorkTime::resolve,
                AverageEarningsParagraph7PreEventBonusP15Formula::calculate,
                AverageEarningsParagraph7PreEventAccruedWageAuthority::resolve
        );
    }

    AverageEarningsParagraph7PreEventAccruedWageAuthorityService(
            BaseAuthorityResolver baseAuthorityResolver,
            BaseFormulaCalculator baseFormulaCalculator,
            SemanticResolver semanticResolver,
            OrdinaryResolver ordinaryResolver,
            HarmfulResolver harmfulResolver,
            BonusFactResolver bonusFactResolver,
            BonusAccrualResolver bonusAccrualResolver,
            BonusPolicyResolver bonusPolicyResolver,
            BonusWorkTimeResolver bonusWorkTimeResolver,
            BonusFormulaCalculator bonusFormulaCalculator,
            FinalResolver finalResolver
    ) {
        this.baseAuthorityResolver = Objects.requireNonNull(baseAuthorityResolver);
        this.baseFormulaCalculator = Objects.requireNonNull(baseFormulaCalculator);
        this.semanticResolver = Objects.requireNonNull(semanticResolver);
        this.ordinaryResolver = Objects.requireNonNull(ordinaryResolver);
        this.harmfulResolver = Objects.requireNonNull(harmfulResolver);
        this.bonusFactResolver = Objects.requireNonNull(bonusFactResolver);
        this.bonusAccrualResolver = Objects.requireNonNull(bonusAccrualResolver);
        this.bonusPolicyResolver = Objects.requireNonNull(bonusPolicyResolver);
        this.bonusWorkTimeResolver = Objects.requireNonNull(bonusWorkTimeResolver);
        this.bonusFormulaCalculator = Objects.requireNonNull(bonusFormulaCalculator);
        this.finalResolver = Objects.requireNonNull(finalResolver);
    }

    @Transactional(readOnly = true)
    public AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution resolve(
            AppUser user,
            LocalDate eventDate,
            YearMonth discoveryThroughMonth,
            List<YearMonth> provenNoPayrollMonths
    ) {
        Objects.requireNonNull(user, "Paragraph-7 composed authority requires user");
        Objects.requireNonNull(eventDate, "Paragraph-7 composed authority requires event date");
        Objects.requireNonNull(
                discoveryThroughMonth,
                "Paragraph-7 composed authority requires discovery-through month"
        );
        List<YearMonth> zeroProofs = List.copyOf(Objects.requireNonNull(
                provenNoPayrollMonths,
                "Paragraph-7 composed authority requires explicit no-Payroll proofs"
        ));

        var baseAuthority = Objects.requireNonNull(
                baseAuthorityResolver.resolve(user, eventDate),
                "Paragraph-7 BASE_PAY authority returned null"
        );
        if (!baseAuthority.ready()) {
            return AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution.blockedComposition(
                    eventDate,
                    requireReason(baseAuthority.blockingReason()),
                    messageOrDefault(
                            baseAuthority.blockingMessage(),
                            "Paragraph-7 BASE_PAY authority is blocked"
                    )
            );
        }

        var basePay = Objects.requireNonNull(
                baseFormulaCalculator.calculate(baseAuthority),
                "Paragraph-7 BASE_PAY formula returned null"
        );

        var semantic = Objects.requireNonNull(
                semanticResolver.resolve(user, basePay),
                "Paragraph-7 semantic wage authority returned null"
        );
        if (!semantic.ready()) {
            return AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution.blockedComposition(
                    eventDate,
                    requireReason(semantic.blockingReason()),
                    messageOrDefault(
                            semantic.blockingMessage(),
                            "Paragraph-7 semantic wage authority is blocked"
                    )
            );
        }

        var ordinary = Objects.requireNonNull(
                ordinaryResolver.resolve(user, semantic),
                "Paragraph-7 ordinary-premium authority returned null"
        );
        if (!ordinary.ready()) {
            return AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution.blockedComposition(
                    eventDate,
                    requireReason(ordinary.blockingReason()),
                    messageOrDefault(
                            ordinary.blockingMessage(),
                            "Paragraph-7 ordinary-premium authority is blocked"
                    )
            );
        }

        var harmful = Objects.requireNonNull(
                harmfulResolver.resolve(user, ordinary),
                "Paragraph-7 harmful-compensation authority returned null"
        );
        if (!harmful.ready()) {
            return AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution.blockedHarmful(harmful);
        }

        var bonusFacts = Objects.requireNonNull(
                bonusFactResolver.resolve(user, semantic),
                "Paragraph-7 P15 bonus FACT authority returned null"
        );
        if (!bonusFacts.ready()) {
            return AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution.blockedBonus(
                    eventDate,
                    harmful,
                    requireReason(bonusFacts.blockingReason()),
                    messageOrDefault(
                            bonusFacts.blockingMessage(),
                            "Paragraph-7 P15 bonus FACT authority is blocked"
                    )
            );
        }

        var accrual = Objects.requireNonNull(
                bonusAccrualResolver.resolve(user, bonusFacts, discoveryThroughMonth, zeroProofs),
                "Paragraph-7 P15 bonus accrual authority returned null"
        );
        if (!accrual.ready()) {
            return AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution.blockedBonus(
                    eventDate,
                    harmful,
                    requireReason(accrual.blockingReason()),
                    messageOrDefault(
                            accrual.blockingMessage(),
                            "Paragraph-7 P15 bonus accrual authority is blocked"
                    )
            );
        }

        var policy = Objects.requireNonNull(
                bonusPolicyResolver.resolve(accrual),
                "Paragraph-7 P15 bonus policy returned null"
        );
        if (!policy.ready()) {
            return AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution.blockedBonus(
                    eventDate,
                    harmful,
                    requireReason(policy.blockingReason()),
                    "Paragraph-7 P15 bonus policy is blocked"
            );
        }

        var workTime = Objects.requireNonNull(
                bonusWorkTimeResolver.resolve(user, policy),
                "Paragraph-7 P15 bonus work-time authority returned null"
        );
        if (!workTime.ready()) {
            return AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution.blockedBonus(
                    eventDate,
                    harmful,
                    requireReason(workTime.blockingReason()),
                    "Paragraph-7 P15 bonus work-time authority is blocked"
            );
        }

        var bonus = Objects.requireNonNull(
                bonusFormulaCalculator.calculate(workTime),
                "Paragraph-7 P15 bonus formula returned null"
        );

        return Objects.requireNonNull(
                finalResolver.resolve(harmful, bonus),
                "Final paragraph-7 accrued-wage authority returned null"
        );
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return AverageEarningsParagraph7PreEventAccruedWageAuthority
                    .UPSTREAM_STATE_CONTRADICTION;
        }
        return reason;
    }

    private static String messageOrDefault(String message, String fallback) {
        return message == null || message.isBlank() ? fallback : message;
    }

    @FunctionalInterface
    interface BaseAuthorityResolver {
        AverageEarningsParagraph7PreEventBasePayAuthorityService.Resolution resolve(
                AppUser user,
                LocalDate eventDate
        );
    }

    @FunctionalInterface
    interface BaseFormulaCalculator {
        AverageEarningsParagraph7PreEventBasePayFormula.Calculation calculate(
                AverageEarningsParagraph7PreEventBasePayAuthorityService.Resolution authority
        );
    }

    @FunctionalInterface
    interface SemanticResolver {
        AverageEarningsParagraph7PreEventSemanticWageFactService.Resolution resolve(
                AppUser user,
                AverageEarningsParagraph7PreEventBasePayFormula.Calculation basePay
        );
    }

    @FunctionalInterface
    interface OrdinaryResolver {
        AverageEarningsParagraph7PreEventOrdinaryPremiumService.Resolution resolve(
                AppUser user,
                AverageEarningsParagraph7PreEventSemanticWageFactService.Resolution semantic
        );
    }

    @FunctionalInterface
    interface HarmfulResolver {
        AverageEarningsParagraph7PreEventHarmfulCompensationService.Resolution resolve(
                AppUser user,
                AverageEarningsParagraph7PreEventOrdinaryPremiumService.Resolution ordinary
        );
    }

    @FunctionalInterface
    interface BonusFactResolver {
        AverageEarningsParagraph7PreEventBonusP15FactService.Resolution resolve(
                AppUser user,
                AverageEarningsParagraph7PreEventSemanticWageFactService.Resolution semantic
        );
    }

    @FunctionalInterface
    interface BonusAccrualResolver {
        AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.Resolution resolve(
                AppUser user,
                AverageEarningsParagraph7PreEventBonusP15FactService.Resolution facts,
                YearMonth discoveryThroughMonth,
                List<YearMonth> provenNoPayrollMonths
        );
    }

    @FunctionalInterface
    interface BonusPolicyResolver {
        AverageEarningsParagraph7PreEventBonusP15Policy.Resolution resolve(
                AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.Resolution accrual
        );
    }

    @FunctionalInterface
    interface BonusWorkTimeResolver {
        AverageEarningsParagraph7PreEventBonusWorkTimeFactService.Resolution resolve(
                AppUser user,
                AverageEarningsParagraph7PreEventBonusP15Policy.Resolution policy
        );
    }

    @FunctionalInterface
    interface BonusFormulaCalculator {
        AverageEarningsParagraph7PreEventBonusP15Formula.Calculation calculate(
                AverageEarningsParagraph7PreEventBonusWorkTimeFactService.Resolution workTime
        );
    }

    @FunctionalInterface
    interface FinalResolver {
        AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution resolve(
                AverageEarningsParagraph7PreEventHarmfulCompensationService.Resolution harmful,
                AverageEarningsParagraph7PreEventBonusP15Formula.Calculation bonus
        );
    }
}
