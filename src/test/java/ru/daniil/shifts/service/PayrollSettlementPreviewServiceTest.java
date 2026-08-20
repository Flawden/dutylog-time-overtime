package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.PayrollSettlementPricingService.PayrollSettlementPricing;
import ru.daniil.shifts.service.PayrollSettlementPricingService.SettlementLine;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayrollSettlementPreviewServiceTest {

    private final PayrollSettlementPricingService pricing =
            mock(
                    PayrollSettlementPricingService.class
            );

    private final PayrollSettlementPreviewService service =
            new PayrollSettlementPreviewService(
                    pricing
            );

    private final AppUser user =
            new AppUser(
                    "payroll-preview-user",
                    "{noop}unused"
            );

    @Test
    void emptyMonthIsReadyAndContainsZeroSettlementMoney() {
        YearMonth month =
                YearMonth.of(
                        2026,
                        8
                );

        when(
                pricing.project(
                        user,
                        month
                )
        ).thenReturn(
                new PayrollSettlementPricing(
                        month,
                        null,
                        0,
                        0,
                        0L,
                        0L,
                        0L,
                        List.of()
                )
        );

        var result =
                service.preview(
                        user,
                        month,
                        "RUB"
                );

        assertTrue(
                result.ready()
        );

        assertNull(
                result.blockingReason()
        );

        assertEquals(
                0L,
                result.totalAmountMinor()
        );
    }

    @Test
    void pricedSettlementBecomesReadyPayrollPreviewMoney() {
        YearMonth month =
                YearMonth.of(
                        2026,
                        8
                );

        SettlementLine line =
                new SettlementLine(
                        10L,
                        LocalDate.of(
                                2026,
                                8,
                                20
                        ),
                        "RUB",
                        60,
                        100_000L,
                        50_000L,
                        150_000L
                );

        when(
                pricing.project(
                        user,
                        month
                )
        ).thenReturn(
                new PayrollSettlementPricing(
                        month,
                        "RUB",
                        1,
                        60,
                        100_000L,
                        50_000L,
                        150_000L,
                        List.of(line)
                )
        );

        var result =
                service.preview(
                        user,
                        month,
                        "RUB"
                );

        assertTrue(
                result.ready()
        );

        assertEquals(
                1,
                result.settlementCount()
        );

        assertEquals(
                60,
                result.minutes()
        );

        assertEquals(
                150_000L,
                result.totalAmountMinor()
        );
    }

    @Test
    void missingHistoricalProvenanceBecomesBlockedPreviewInsteadOfBreakingGet() {
        YearMonth month =
                YearMonth.of(
                        2026,
                        8
                );

        when(
                pricing.project(
                        user,
                        month
                )
        ).thenThrow(
                ApiException.conflict(
                        "PAY_PRICING_PROVENANCE_REQUIRED",
                        "Неизвестно происхождение минут"
                )
        );

        var result =
                service.preview(
                        user,
                        month,
                        "RUB"
                );

        assertFalse(
                result.ready()
        );

        assertEquals(
                "PAY_PRICING_PROVENANCE_REQUIRED",
                result.blockingReason()
        );

        assertEquals(
                0L,
                result.totalAmountMinor()
        );
    }

    @Test
    void settlementCurrencyMismatchBecomesExplicitBlockingState() {
        YearMonth month =
                YearMonth.of(
                        2026,
                        8
                );

        SettlementLine line =
                new SettlementLine(
                        20L,
                        LocalDate.of(
                                2026,
                                8,
                                20
                        ),
                        "EUR",
                        60,
                        2_000L,
                        0L,
                        2_000L
                );

        when(
                pricing.project(
                        user,
                        month
                )
        ).thenReturn(
                new PayrollSettlementPricing(
                        month,
                        "EUR",
                        1,
                        60,
                        2_000L,
                        0L,
                        2_000L,
                        List.of(line)
                )
        );

        var result =
                service.preview(
                        user,
                        month,
                        "RUB"
                );

        assertFalse(
                result.ready()
        );

        assertEquals(
                "PAYROLL_SETTLEMENT_CURRENCY_MISMATCH",
                result.blockingReason()
        );
    }

    @Test
    void unexpectedMoneyFailureStillEscapesInsteadOfBeingHiddenAsReadiness() {
        YearMonth month =
                YearMonth.of(
                        2026,
                        8
                );

        when(
                pricing.project(
                        user,
                        month
                )
        ).thenThrow(
                ApiException.badRequest(
                        "PAYROLL_AMOUNT_OVERFLOW",
                        "overflow"
                )
        );

        ApiException error =
                assertThrows(
                        ApiException.class,
                        () ->
                                service.preview(
                                        user,
                                        month,
                                        "RUB"
                                )
                );

        assertEquals(
                "PAYROLL_AMOUNT_OVERFLOW",
                error.getCode()
        );
    }
}
