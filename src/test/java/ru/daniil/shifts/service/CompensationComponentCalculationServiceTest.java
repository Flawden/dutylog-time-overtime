package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.CompensationComponentVersion.CalculationBase;
import ru.daniil.shifts.model.CompensationComponentVersion.CalculationType;
import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.service.CompensationComponentCalculationService.ComponentRule;
import ru.daniil.shifts.service.CompensationComponentCalculationService.Context;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompensationComponentCalculationServiceTest {

    private final CompensationComponentCalculationService service =
            new CompensationComponentCalculationService();

    @Test
    void fixedAmountUsesPayrollCurrencyAndPreservesUserName() {
        var result =
                service.calculate(
                        hourlyContext(800_000L),
                        List.of(
                                fixed(
                                        1,
                                        11,
                                        "Моя ежемесячная премия",
                                        50_000L,
                                        "RUB",
                                        true
                                )
                        )
                );

        assertEquals(50_000L, result.totalAmountMinor());
        assertEquals(1, result.lines().size());
        assertEquals(
                "Моя ежемесячная премия",
                result.lines().get(0).displayName()
        );
        assertEquals(
                50_000L,
                result.lines().get(0).amountMinor()
        );
        assertNotNull(result.fingerprint());
        assertEquals(64, result.fingerprint().length());
    }

    @Test
    void earnedBasePercentageSupportsCustomHarmfulnessFourPercent() {
        var result =
                service.calculate(
                        hourlyContext(800_000L),
                        List.of(
                                percent(
                                        2,
                                        12,
                                        "Вредность 4%",
                                        PayrollEarningKind.HARMFUL_CONDITIONS,
                                        CalculationBase.EARNED_BASE_PAY,
                                        400,
                                        true
                                )
                        )
                );

        assertEquals(
                32_000L,
                result.totalAmountMinor()
        );

        var line = result.lines().get(0);

        assertEquals(
                CalculationBase.EARNED_BASE_PAY,
                line.calculationBase()
        );
        assertEquals(
                800_000L,
                line.referenceBaseMinor()
        );
        assertEquals(
                32_000L,
                line.amountMinor()
        );

        assertEquals(
                PayrollEarningKind.HARMFUL_CONDITIONS,
                line.earningKind()
        );

        var unclassified =
                service.calculate(
                        hourlyContext(800_000L),
                        List.of(
                                percent(
                                        2,
                                        12,
                                        "Вредность 4%",
                                        CalculationBase.EARNED_BASE_PAY,
                                        400,
                                        true
                                )
                        )
                );

        assertEquals(
                result.totalAmountMinor(),
                unclassified.totalAmountMinor()
        );

        org.junit.jupiter.api.Assertions.assertNotEquals(
                result.fingerprint(),
                unclassified.fingerprint()
        );
    }

    @Test
    void nominalSalaryPercentageUsesConfiguredSalaryNotEarnedBase() {
        Context context =
                new Context(
                        "RUB",
                        "SALARY",
                        6_000_000L,
                        4_500_000L
                );

        var result =
                service.calculate(
                        context,
                        List.of(
                                percent(
                                        3,
                                        13,
                                        "Процент от оклада",
                                        CalculationBase.NOMINAL_SALARY,
                                        1_000,
                                        true
                                )
                        )
                );

        assertEquals(
                600_000L,
                result.totalAmountMinor()
        );

        assertEquals(
                6_000_000L,
                result.lines().get(0)
                        .referenceBaseMinor()
        );
    }

    @Test
    void percentageUsesHalfUpRoundingPerComponent() {
        var result =
                service.calculate(
                        hourlyContext(101L),
                        List.of(
                                percent(
                                        4,
                                        14,
                                        "Половина",
                                        CalculationBase.EARNED_BASE_PAY,
                                        5_000,
                                        true
                                )
                        )
                );

        assertEquals(
                51L,
                result.totalAmountMinor()
        );
    }

    @Test
    void disabledComponentCreatesNoMoneyOrFingerprint() {
        var result =
                service.calculate(
                        hourlyContext(800_000L),
                        List.of(
                                percent(
                                        5,
                                        15,
                                        "Отключённая надбавка",
                                        CalculationBase.EARNED_BASE_PAY,
                                        400,
                                        false
                                )
                        )
                );

        assertEquals(0L, result.totalAmountMinor());
        assertTrue(result.lines().isEmpty());
        assertNull(result.fingerprint());
    }

    @Test
    void severalComponentsAggregateAndFingerprintIgnoresInputOrder() {
        ComponentRule fixed =
                fixed(
                        6,
                        16,
                        "Фиксированная",
                        10_000L,
                        "RUB",
                        true
                );

        ComponentRule percent =
                percent(
                        7,
                        17,
                        "Процентная",
                        CalculationBase.EARNED_BASE_PAY,
                        1_000,
                        true
                );

        Context context =
                hourlyContext(
                        100_000L
                );

        var first =
                service.calculate(
                        context,
                        List.of(
                                fixed,
                                percent
                        )
                );

        var reversed =
                service.calculate(
                        context,
                        List.of(
                                percent,
                                fixed
                        )
                );

        assertEquals(
                20_000L,
                first.totalAmountMinor()
        );
        assertEquals(
                first.totalAmountMinor(),
                reversed.totalAmountMinor()
        );
        assertEquals(
                first.fingerprint(),
                reversed.fingerprint()
        );
        assertEquals(
                List.of(6L, 7L),
                first.lines()
                        .stream()
                        .map(item -> item.componentId())
                        .toList()
        );
    }

    @Test
    void fixedCurrencyMismatchFailsClosed() {
        IllegalArgumentException error =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                service.calculate(
                                        hourlyContext(
                                                100_000L
                                        ),
                                        List.of(
                                                fixed(
                                                        8,
                                                        18,
                                                        "Долларовая выплата",
                                                        1_000L,
                                                        "USD",
                                                        true
                                                )
                                        )
                                )
                );

        assertTrue(
                error.getMessage()
                        .contains(
                                "does not match Payroll currency"
                        )
        );
    }

    @Test
    void nominalSalaryBaseFailsClosedInHourlyMode() {
        IllegalArgumentException error =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                service.calculate(
                                        hourlyContext(
                                                100_000L
                                        ),
                                        List.of(
                                                percent(
                                                        9,
                                                        19,
                                                        "От оклада",
                                                        CalculationBase.NOMINAL_SALARY,
                                                        400,
                                                        true
                                                )
                                        )
                                )
                );

        assertTrue(
                error.getMessage()
                        .contains(
                                "requires SALARY"
                        )
        );
    }

    @Test
    void duplicateStableComponentIdentityFailsClosed() {
        ComponentRule first =
                percent(
                        10,
                        20,
                        "Версия A",
                        CalculationBase.EARNED_BASE_PAY,
                        400,
                        true
                );

        ComponentRule second =
                percent(
                        10,
                        21,
                        "Версия B",
                        CalculationBase.EARNED_BASE_PAY,
                        500,
                        true
                );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.calculate(
                                hourlyContext(
                                        100_000L
                                ),
                                List.of(
                                        first,
                                        second
                                )
                        )
        );
    }

    @Test
    void invalidPercentageShapeIsRejectedAtBoundary() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ComponentRule(
                                11,
                                22,
                                LocalDate.of(
                                        2026,
                                        9,
                                        1
                                ),
                                "Некорректная",
                                CalculationType.PERCENT_OF_BASE,
                                null,
                                400,
                                null,
                                null,
                                true
                        )
        );
    }


    @Test
    void monthlyAndRegionalLocalEligibleBasesRespectSemanticPhaseDependenciesWithoutChangingLineOrder() {
        ComponentRule regional =
                percent(
                        1,
                        31,
                        "Районный 15%",
                        PayrollEarningKind.REGIONAL_COEFFICIENT,
                        CalculationBase.LOCAL_ELIGIBLE_EARNINGS,
                        1_500,
                        true
                );

        ComponentRule harmful =
                percent(
                        2,
                        32,
                        "Вредность 4%",
                        PayrollEarningKind.HARMFUL_CONDITIONS,
                        CalculationBase.EARNED_BASE_PAY,
                        400,
                        true
                );

        ComponentRule monthly =
                percent(
                        3,
                        33,
                        "Ежемесячная премия 40%",
                        PayrollEarningKind.MONTHLY_BONUS,
                        CalculationBase.LOCAL_ELIGIBLE_EARNINGS,
                        4_000,
                        true
                );

        ComponentRule combination =
                fixed(
                        4,
                        34,
                        "Совмещение",
                        PayrollEarningKind.COMBINATION,
                        50_000L,
                        "RUB",
                        true
                );

        ComponentRule oneTime =
                fixed(
                        5,
                        35,
                        "Разовая премия",
                        PayrollEarningKind.ONE_TIME_BONUS,
                        20_000L,
                        "RUB",
                        true
                );

        var result =
                service.calculate(
                        hourlyContext(800_000L),
                        List.of(
                                oneTime,
                                regional,
                                combination,
                                harmful,
                                monthly
                        ),
                        List.of(
                                new PayrollEligibleEarningsBaseResolver.Earning(
                                        PayrollEarningKind.BASE_PAY,
                                        800_000L
                                ),
                                new PayrollEligibleEarningsBaseResolver.Earning(
                                        PayrollEarningKind.NIGHT_PREMIUM,
                                        10_000L
                                )
                        ),
                        true
                );

        assertEquals(
                List.of(1L, 2L, 3L, 4L, 5L),
                result.lines()
                        .stream()
                        .map(ComponentRuleLine -> ComponentRuleLine.componentId())
                        .toList()
        );

        var regionalLine = result.lines().get(0);
        var monthlyLine = result.lines().get(2);

        assertEquals(
                892_000L,
                monthlyLine.referenceBaseMinor()
        );
        assertEquals(
                356_800L,
                monthlyLine.amountMinor()
        );

        assertEquals(
                CalculationBase.LOCAL_ELIGIBLE_EARNINGS,
                regionalLine.calculationBase()
        );
        assertEquals(
                1_268_800L,
                regionalLine.referenceBaseMinor()
        );
        assertEquals(
                190_320L,
                regionalLine.amountMinor()
        );
        assertEquals(
                649_120L,
                result.totalAmountMinor()
        );
    }

    @Test
    void localEligibleBaseFailsClosedWhenUpstreamSemanticPoolIsIncomplete() {
        IllegalArgumentException error =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                service.calculate(
                                        hourlyContext(800_000L),
                                        List.of(
                                                percent(
                                                        1,
                                                        31,
                                                        "Районный 15%",
                                                        PayrollEarningKind.REGIONAL_COEFFICIENT,
                                                        CalculationBase.LOCAL_ELIGIBLE_EARNINGS,
                                                        1_500,
                                                        true
                                                )
                                        ),
                                        List.of(
                                                new PayrollEligibleEarningsBaseResolver.Earning(
                                                        PayrollEarningKind.BASE_PAY,
                                                        800_000L
                                                )
                                        ),
                                        false
                                )
                );

        assertTrue(
                error.getMessage().contains("complete upstream semantic earnings")
        );
    }

    @Test
    void localEligibleBaseFailsClosedWhenSiblingComponentIsUnclassified() {
        IllegalArgumentException error =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                service.calculate(
                                        hourlyContext(800_000L),
                                        List.of(
                                                fixed(
                                                        2,
                                                        32,
                                                        "Неизвестная выплата",
                                                        10_000L,
                                                        "RUB",
                                                        true
                                                ),
                                                percent(
                                                        1,
                                                        31,
                                                        "Районный 15%",
                                                        PayrollEarningKind.REGIONAL_COEFFICIENT,
                                                        CalculationBase.LOCAL_ELIGIBLE_EARNINGS,
                                                        1_500,
                                                        true
                                                )
                                        ),
                                        List.of(
                                                new PayrollEligibleEarningsBaseResolver.Earning(
                                                        PayrollEarningKind.BASE_PAY,
                                                        800_000L
                                                )
                                        ),
                                        true
                                )
                );

        assertTrue(
                error.getMessage().contains("UNCLASSIFIED")
        );
    }

    @Test
    void localEligibleBaseDoesNotGeneralizeBeyondProvenMonthlyAndRegionalTargets() {
        IllegalArgumentException error =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                service.calculate(
                                        hourlyContext(800_000L),
                                        List.of(
                                                percent(
                                                        1,
                                                        31,
                                                        "Разовая премия",
                                                        PayrollEarningKind.ONE_TIME_BONUS,
                                                        CalculationBase.LOCAL_ELIGIBLE_EARNINGS,
                                                        1_000,
                                                        true
                                                )
                                        ),
                                        List.of(
                                                new PayrollEligibleEarningsBaseResolver.Earning(
                                                        PayrollEarningKind.BASE_PAY,
                                                        800_000L
                                                )
                                        ),
                                        true
                                )
                );

        assertTrue(
                error.getMessage().contains("only proven for MONTHLY_BONUS and REGIONAL_COEFFICIENT")
        );
    }

    private Context hourlyContext(
            long earnedBase
    ) {
        return new Context(
                "RUB",
                "HOURLY",
                null,
                earnedBase
        );
    }

    private ComponentRule fixed(
            long componentId,
            long versionId,
            String name,
            PayrollEarningKind earningKind,
            long amount,
            String currency,
            boolean enabled
    ) {
        return new ComponentRule(
                componentId,
                versionId,
                LocalDate.of(
                        2026,
                        9,
                        1
                ),
                name,
                earningKind,
                CalculationType.FIXED_AMOUNT,
                null,
                null,
                amount,
                currency,
                enabled
        );
    }

    private ComponentRule fixed(
            long componentId,
            long versionId,
            String name,
            long amount,
            String currency,
            boolean enabled
    ) {
        return new ComponentRule(
                componentId,
                versionId,
                LocalDate.of(
                        2026,
                        9,
                        1
                ),
                name,
                CalculationType.FIXED_AMOUNT,
                null,
                null,
                amount,
                currency,
                enabled
        );
    }

    private ComponentRule percent(
            long componentId,
            long versionId,
            String name,
            CalculationBase base,
            int rateBps,
            boolean enabled
    ) {
        return percent(
                componentId,
                versionId,
                name,
                null,
                base,
                rateBps,
                enabled
        );
    }

    private ComponentRule percent(
            long componentId,
            long versionId,
            String name,
            PayrollEarningKind earningKind,
            CalculationBase base,
            int rateBps,
            boolean enabled
    ) {
        return new ComponentRule(
                componentId,
                versionId,
                LocalDate.of(
                        2026,
                        9,
                        1
                ),
                name,
                earningKind,
                CalculationType.PERCENT_OF_BASE,
                base,
                rateBps,
                null,
                null,
                enabled
        );
    }
}
