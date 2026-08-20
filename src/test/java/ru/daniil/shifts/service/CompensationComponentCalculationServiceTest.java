package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.CompensationComponentVersion.CalculationBase;
import ru.daniil.shifts.model.CompensationComponentVersion.CalculationType;
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
        return new ComponentRule(
                componentId,
                versionId,
                LocalDate.of(
                        2026,
                        9,
                        1
                ),
                name,
                CalculationType.PERCENT_OF_BASE,
                base,
                rateBps,
                null,
                null,
                enabled
        );
    }
}
