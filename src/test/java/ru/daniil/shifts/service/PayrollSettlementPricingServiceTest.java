package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.OvertimeSettlement;
import ru.daniil.shifts.repo.OvertimeSettlementRepository;
import ru.daniil.shifts.service.OvertimeSettlementPricingService.SettlementMoneyProjection;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayrollSettlementPricingServiceTest {

    private final OvertimeSettlementRepository settlements =
            mock(
                    OvertimeSettlementRepository.class
            );

    private final OvertimeSettlementPricingService pricing =
            mock(
                    OvertimeSettlementPricingService.class
            );

    private final PayrollSettlementPricingService service =
            new PayrollSettlementPricingService(
                    settlements,
                    pricing
            );

    private final AppUser user =
            new AppUser(
                    "payroll-settlement-user",
                    "{noop}unused"
            );

    @Test
    void emptyPayrollMonthHasZeroSettlementMoneyWithoutPricingCalls() {
        YearMonth month =
                YearMonth.of(
                        2026,
                        8
                );

        when(
                settlements
                        .findByOwnerAndSettlementDateBetweenOrderBySettlementDateAscIdAsc(
                                user,
                                LocalDate.of(
                                        2026,
                                        8,
                                        1
                                ),
                                LocalDate.of(
                                        2026,
                                        8,
                                        31
                                )
                        )
        ).thenReturn(
                List.of()
        );

        var result =
                service.project(
                        user,
                        month
                );

        assertTrue(
                result.empty()
        );

        assertEquals(
                0,
                result.settlementCount()
        );

        assertEquals(
                0L,
                result.totalAmountMinor()
        );

        assertNull(
                result.currencyCode()
        );

        verifyNoInteractions(
                pricing
        );
    }

    @Test
    void settlementDateDefinesPayrollMonthAndAlreadyPricedTransactionsAreSummed() {
        YearMonth month =
                YearMonth.of(
                        2026,
                        8
                );

        OvertimeSettlement first =
                settlement(
                        10L,
                        LocalDate.of(
                                2026,
                                8,
                                1
                        ),
                        60
                );

        OvertimeSettlement second =
                settlement(
                        11L,
                        LocalDate.of(
                                2026,
                                8,
                                31
                        ),
                        120
                );

        when(
                settlements
                        .findByOwnerAndSettlementDateBetweenOrderBySettlementDateAscIdAsc(
                                user,
                                month.atDay(1),
                                month.atEndOfMonth()
                        )
        ).thenReturn(
                List.of(
                        first,
                        second
                )
        );

        when(
                pricing.price(
                        user,
                        10L
                )
        ).thenReturn(
                priced(
                        10L,
                        LocalDate.of(
                                2026,
                                8,
                                1
                        ),
                        "RUB",
                        60,
                        100_000L,
                        50_000L
                )
        );

        when(
                pricing.price(
                        user,
                        11L
                )
        ).thenReturn(
                priced(
                        11L,
                        LocalDate.of(
                                2026,
                                8,
                                31
                        ),
                        "RUB",
                        120,
                        200_000L,
                        100_000L
                )
        );

        var result =
                service.project(
                        user,
                        month
                );

        assertFalse(
                result.empty()
        );

        assertEquals(
                "RUB",
                result.currencyCode()
        );

        assertEquals(
                2,
                result.settlementCount()
        );

        assertEquals(
                180,
                result.minutes()
        );

        assertEquals(
                300_000L,
                result.baseAmountMinor()
        );

        assertEquals(
                150_000L,
                result.premiumAmountMinor()
        );

        assertEquals(
                450_000L,
                result.totalAmountMinor()
        );

        assertEquals(
                List.of(
                        10L,
                        11L
                ),
                result.settlements()
                        .stream()
                        .map(line ->
                                line.settlementId()
                        )
                        .toList()
        );
    }

    @Test
    void settlementPricingIdentityMustMatchRepositoryRow() {
        YearMonth month =
                YearMonth.of(
                        2026,
                        8
                );

        OvertimeSettlement settlement =
                settlement(
                        20L,
                        LocalDate.of(
                                2026,
                                8,
                                10
                        ),
                        60
                );

        when(
                settlements
                        .findByOwnerAndSettlementDateBetweenOrderBySettlementDateAscIdAsc(
                                user,
                                month.atDay(1),
                                month.atEndOfMonth()
                        )
        ).thenReturn(
                List.of(
                        settlement
                )
        );

        when(
                pricing.price(
                        user,
                        20L
                )
        ).thenReturn(
                priced(
                        999L,
                        LocalDate.of(
                                2026,
                                8,
                                10
                        ),
                        "RUB",
                        60,
                        100_000L,
                        0L
                )
        );

        assertThrows(
                IllegalStateException.class,
                () ->
                        service.project(
                                user,
                                month
                        )
        );
    }

    @Test
    void differentCurrenciesAcrossSettlementsFailClosed() {
        YearMonth month =
                YearMonth.of(
                        2026,
                        8
                );

        OvertimeSettlement first =
                settlement(
                        30L,
                        LocalDate.of(
                                2026,
                                8,
                                5
                        ),
                        60
                );

        OvertimeSettlement second =
                settlement(
                        31L,
                        LocalDate.of(
                                2026,
                                8,
                                6
                        ),
                        60
                );

        when(
                settlements
                        .findByOwnerAndSettlementDateBetweenOrderBySettlementDateAscIdAsc(
                                user,
                                month.atDay(1),
                                month.atEndOfMonth()
                        )
        ).thenReturn(
                List.of(
                        first,
                        second
                )
        );

        when(
                pricing.price(
                        user,
                        30L
                )
        ).thenReturn(
                priced(
                        30L,
                        LocalDate.of(
                                2026,
                                8,
                                5
                        ),
                        "RUB",
                        60,
                        100_000L,
                        0L
                )
        );

        when(
                pricing.price(
                        user,
                        31L
                )
        ).thenReturn(
                priced(
                        31L,
                        LocalDate.of(
                                2026,
                                8,
                                6
                        ),
                        "EUR",
                        60,
                        2_000L,
                        0L
                )
        );

        ApiException error =
                assertThrows(
                        ApiException.class,
                        () ->
                                service.project(
                                        user,
                                        month
                                )
                );

        assertEquals(
                "PAYROLL_SETTLEMENT_CURRENCY_MISMATCH",
                error.getCode()
        );
    }

    @Test
    void pricingDateAndMinuteIdentityCannotDriftFromExplicitSettlement() {
        YearMonth month =
                YearMonth.of(
                        2026,
                        8
                );

        OvertimeSettlement settlement =
                settlement(
                        40L,
                        LocalDate.of(
                                2026,
                                8,
                                15
                        ),
                        90
                );

        when(
                settlements
                        .findByOwnerAndSettlementDateBetweenOrderBySettlementDateAscIdAsc(
                                user,
                                month.atDay(1),
                                month.atEndOfMonth()
                        )
        ).thenReturn(
                List.of(
                        settlement
                )
        );

        when(
                pricing.price(
                        user,
                        40L
                )
        ).thenReturn(
                priced(
                        40L,
                        LocalDate.of(
                                2026,
                                8,
                                16
                        ),
                        "RUB",
                        90,
                        100_000L,
                        0L
                )
        );

        assertThrows(
                IllegalStateException.class,
                () ->
                        service.project(
                                user,
                                month
                        )
        );
    }

    private OvertimeSettlement settlement(
            long id,
            LocalDate date,
            int minutes
    ) {
        OvertimeSettlement settlement =
                mock(
                        OvertimeSettlement.class
                );

        when(
                settlement.getId()
        ).thenReturn(
                id
        );

        when(
                settlement.getSettlementDate()
        ).thenReturn(
                date
        );

        when(
                settlement.getRequestedMinutes()
        ).thenReturn(
                minutes
        );

        return settlement;
    }

    private SettlementMoneyProjection priced(
            long id,
            LocalDate date,
            String currency,
            int minutes,
            long base,
            long premium
    ) {
        return new SettlementMoneyProjection(
                id,
                date,
                currency,
                minutes,
                base,
                premium,
                Math.addExact(
                        base,
                        premium
                ),
                List.of(
                        new OvertimeSettlementPricingService.PricedRateBucket(
                                Math.max(
                                        1L,
                                        base
                                ),
                                minutes,
                                base,
                                premium,
                                Math.addExact(
                                        base,
                                        premium
                                ),
                                List.of()
                        )
                ),
                List.of(
                        new OvertimeSettlementPricingService.SourceValuation(
                                1000L + id,
                                2000L + id,
                                3000L + id,
                                date.minusDays(1),
                                minutes,
                                0,
                                480,
                                date.minusDays(2),
                                YearMonth.from(
                                        date.minusDays(1)
                                ),
                                YearMonth.from(
                                        date.minusDays(1)
                                ).atDay(1),
                                "HOURLY",
                                currency,
                                Math.max(
                                        1L,
                                        base
                                ),
                                null
                        )
                )
        );
    }
}
