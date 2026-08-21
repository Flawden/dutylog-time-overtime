package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.CompensationComponent;
import ru.daniil.shifts.model.CompensationComponentVersion;
import ru.daniil.shifts.model.CompensationComponentVersion.CalculationBase;
import ru.daniil.shifts.model.CompensationComponentVersion.CalculationType;
import ru.daniil.shifts.service.CompensationComponentCalculationService.Context;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayrollCompensationComponentPreviewServiceTest {

    private final CompensationComponentResolverService resolver =
            mock(
                    CompensationComponentResolverService.class
            );

    private final PayrollCompensationComponentPreviewService service =
            new PayrollCompensationComponentPreviewService(
                    resolver,
                    new CompensationComponentCalculationService()
            );

    private final AppUser owner =
            new AppUser(
                    "component-preview-owner",
                    "{noop}unused"
            );

    @Test
    void earnedBasePercentProducesAdditiveComponentMoney() {
        doReturn(
                List.of(
                        version(
                                10L,
                                20L,
                                "Премия за выживание после ночной смены",
                                CalculationType.PERCENT_OF_BASE,
                                CalculationBase.EARNED_BASE_PAY,
                                400,
                                null,
                                null,
                                true
                        )
                )        ).when(
                resolver
        ).resolve(
                owner,
                YearMonth.of(2026, 8)
        );

        var result =
                service.preview(
                        owner,
                        YearMonth.of(2026, 8),
                        new Context(
                                "RUB",
                                "HOURLY",
                                null,
                                800_000L
                        ),
                        null
                );

        assertTrue(result.ready());
        assertNull(result.blockingReason());
        assertEquals(
                32_000L,
                result.projection()
                        .totalAmountMinor()
        );
        assertEquals(
                1,
                result.projection()
                        .lines()
                        .size()
        );
        assertEquals(
                800_000L,
                result.projection()
                        .lines()
                        .get(0)
                        .referenceBaseMinor()
        );
        assertEquals(
                "Премия за выживание после ночной смены",
                result.projection()
                        .lines()
                        .get(0)
                        .displayName()
        );
        assertNotNull(
                result.projection()
                        .fingerprint()
        );
    }

    @Test
    void fixedComponentCurrencyMismatchBecomesExplicitReadinessBlocker() {
        doReturn(
                List.of(
                        version(
                                11L,
                                21L,
                                "Fixed USD",
                                CalculationType.FIXED_AMOUNT,
                                null,
                                null,
                                50_000L,
                                "USD",
                                true
                        )
                )        ).when(
                resolver
        ).resolve(
                owner,
                YearMonth.of(2026, 8)
        );

        var result =
                service.preview(
                        owner,
                        YearMonth.of(2026, 8),
                        new Context(
                                "RUB",
                                "HOURLY",
                                null,
                                800_000L
                        ),
                        null
                );

        assertFalse(result.ready());
        assertEquals(
                PayrollCompensationComponentPreviewService
                        .PAYROLL_CURRENCY_MISMATCH,
                result.blockingReason()
        );
        assertEquals(
                0L,
                result.projection()
                        .totalAmountMinor()
        );
    }

    @Test
    void nominalSalaryPercentCannotPretendHourlyPayrollHasSalaryBase() {
        doReturn(
                List.of(
                        version(
                                12L,
                                22L,
                                "Salary percent",
                                CalculationType.PERCENT_OF_BASE,
                                CalculationBase.NOMINAL_SALARY,
                                400,
                                null,
                                null,
                                true
                        )
                )        ).when(
                resolver
        ).resolve(
                owner,
                YearMonth.of(2026, 8)
        );

        var result =
                service.preview(
                        owner,
                        YearMonth.of(2026, 8),
                        new Context(
                                "RUB",
                                "HOURLY",
                                null,
                                800_000L
                        ),
                        null
                );

        assertFalse(result.ready());
        assertEquals(
                PayrollCompensationComponentPreviewService
                        .PAYROLL_BASE_UNAVAILABLE,
                result.blockingReason()
        );
    }

    @Test
    void disabledComponentsRemainConfigurationHistoryButProduceNoMoneyIdentity() {
        doReturn(
                List.of(
                        version(
                                13L,
                                23L,
                                "Disabled",
                                CalculationType.PERCENT_OF_BASE,
                                CalculationBase.EARNED_BASE_PAY,
                                400,
                                null,
                                null,
                                false
                        )
                )        ).when(
                resolver
        ).resolve(
                owner,
                YearMonth.of(2026, 8)
        );

        var result =
                service.preview(
                        owner,
                        YearMonth.of(2026, 8),
                        null,
                        "PAYROLL_COMPENSATION_REQUIRED"
                );

        assertTrue(result.ready());
        assertTrue(
                result.projection()
                        .lines()
                        .isEmpty()
        );
        assertEquals(
                0L,
                result.projection()
                        .totalAmountMinor()
        );
        assertNull(
                result.projection()
                        .fingerprint()
        );
    }

    private CompensationComponentVersion version(
            long componentId,
            long versionId,
            String name,
            CalculationType type,
            CalculationBase base,
            Integer rateBps,
            Long amountMinor,
            String currency,
            boolean enabled
    ) {
        CompensationComponent component =
                mock(
                        CompensationComponent.class
                );

        when(
                component.getId()
        ).thenReturn(
                componentId
        );

        CompensationComponentVersion version =
                mock(
                        CompensationComponentVersion.class
                );

        when(
                version.getId()
        ).thenReturn(
                versionId
        );

        when(
                version.getComponent()
        ).thenReturn(
                component
        );

        when(
                version.getEffectiveFrom()
        ).thenReturn(
                LocalDate.of(2026, 8, 1)
        );

        when(
                version.getDisplayName()
        ).thenReturn(
                name
        );

        when(
                version.getCalculationType()
        ).thenReturn(
                type
        );

        when(
                version.getCalculationBase()
        ).thenReturn(
                base
        );

        when(
                version.getRateBps()
        ).thenReturn(
                rateBps
        );

        when(
                version.getAmountMinor()
        ).thenReturn(
                amountMinor
        );

        when(
                version.getCurrencyCode()
        ).thenReturn(
                currency
        );

        when(
                version.isEnabled()
        ).thenReturn(
                enabled
        );

        return version;
    }
}
