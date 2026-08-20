package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.dto.Dtos.ProductionCalendarMonthDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.CompensationTerm;
import ru.daniil.shifts.repo.CompensationTermRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HistoricalCompensationRateServiceTest {

    private final CompensationTermRepository terms =
            mock(
                    CompensationTermRepository.class
            );

    private final ProductionCalendarService productionCalendar =
            mock(
                    ProductionCalendarService.class
            );

    private final CompensationCalculationService calculation =
            new CompensationCalculationService();

    private final HistoricalCompensationRateService service =
            new HistoricalCompensationRateService(
                    terms,
                    productionCalendar,
                    calculation
            );

    private final AppUser user =
            new AppUser(
                    "historical-rate-user",
                    "{noop}unused"
            );

    @Test
    void hourlyUsesCompensationEffectiveForSourceMonthNotSettlementTime() {
        LocalDate sourceDate =
                LocalDate.of(
                        2026,
                        8,
                        20
                );

        CompensationTerm term =
                term(
                        LocalDate.of(
                                2026,
                                7,
                                1
                        ),
                        "HOURLY",
                        "RUB",
                        100_000L,
                        null
                );

        when(
                terms
                        .findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                                user,
                                LocalDate.of(
                                        2026,
                                        8,
                                        1
                                )
                        )
        ).thenReturn(
                Optional.of(term)
        );

        var resolved =
                service.resolve(
                        user,
                        sourceDate
                );

        assertEquals(
                sourceDate,
                resolved.sourceDate()
        );

        assertEquals(
                "2026-08",
                resolved.sourceMonth()
                        .toString()
        );

        assertEquals(
                LocalDate.of(
                        2026,
                        7,
                        1
                ),
                resolved.compensationEffectiveFrom()
        );

        assertEquals(
                "HOURLY",
                resolved.payMode()
        );

        assertEquals(
                "RUB",
                resolved.currencyCode()
        );

        assertEquals(
                100_000L,
                resolved.baseHourlyRateMinor()
        );

        assertNull(
                resolved.productionNormMinutes()
        );

        verifyNoInteractions(
                productionCalendar
        );
    }

    @Test
    void salaryDerivesHistoricalHourlyValueFromProductionNormOfSourceMonth() {
        LocalDate sourceDate =
                LocalDate.of(
                        2026,
                        8,
                        20
                );

        CompensationTerm term =
                term(
                        LocalDate.of(
                                2026,
                                8,
                                1
                        ),
                        "SALARY",
                        "RUB",
                        null,
                        8_000_000L
                );

        when(
                terms
                        .findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                                user,
                                LocalDate.of(
                                        2026,
                                        8,
                                        1
                                )
                        )
        ).thenReturn(
                Optional.of(term)
        );

        when(
                productionCalendar.month(
                        user,
                        "2026-08"
                )
        ).thenReturn(
                production(
                        "2026-08",
                        10_020,
                        true
                )
        );

        var resolved =
                service.resolve(
                        user,
                        sourceDate
                );

        /*
         * Canonical existing formula:
         * 8_000_000 * 60 / 10_020 = 47_904.19...
         * HALF_UP -> 47_904 minor/hour.
         */
        assertEquals(
                47_904L,
                resolved.baseHourlyRateMinor()
        );

        assertEquals(
                10_020,
                resolved.productionNormMinutes()
        );

        assertEquals(
                "SALARY",
                resolved.payMode()
        );
    }

    @Test
    void salaryFailsClosedWhenHistoricalSourceMonthCoverageIsIncomplete() {
        LocalDate sourceDate =
                LocalDate.of(
                        2026,
                        8,
                        20
                );

        CompensationTerm term =
                term(
                        LocalDate.of(
                                2026,
                                8,
                                1
                        ),
                        "SALARY",
                        "RUB",
                        null,
                        8_000_000L
                );

        when(
                terms
                        .findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                                user,
                                LocalDate.of(
                                        2026,
                                        8,
                                        1
                                )
                        )
        ).thenReturn(
                Optional.of(term)
        );

        when(
                productionCalendar.month(
                        user,
                        "2026-08"
                )
        ).thenReturn(
                production(
                        "2026-08",
                        4_800,
                        false
                )
        );

        ApiException error =
                assertThrows(
                        ApiException.class,
                        () ->
                                service.resolve(
                                        user,
                                        sourceDate
                                )
                );

        assertTrue(
                error.getMessage()
                        .contains(
                                "весь расчётный месяц"
                        )
        );
    }

    @Test
    void missingHistoricalCompensationTermFailsClosed() {
        LocalDate sourceDate =
                LocalDate.of(
                        2026,
                        5,
                        31
                );

        when(
                terms
                        .findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                                user,
                                LocalDate.of(
                                        2026,
                                        5,
                                        1
                                )
                        )
        ).thenReturn(
                Optional.empty()
        );

        ApiException error =
                assertThrows(
                        ApiException.class,
                        () ->
                                service.resolve(
                                        user,
                                        sourceDate
                                )
                );

        assertTrue(
                error.getMessage()
                        .contains(
                                "не настроен способ оплаты"
                        )
        );

        verifyNoInteractions(
                productionCalendar
        );
    }

    @Test
    void canonicalCalculationApiPreservesExistingHourlyAndSalarySemantics() {
        CompensationTerm hourly =
                term(
                        LocalDate.of(
                                2026,
                                8,
                                1
                        ),
                        "HOURLY",
                        "RUB",
                        123_456L,
                        null
                );

        assertEquals(
                123_456L,
                calculation
                        .effectiveHourlyRateMinor(
                                hourly,
                                0
                        )
        );

        CompensationTerm salary =
                term(
                        LocalDate.of(
                                2026,
                                8,
                                1
                        ),
                        "SALARY",
                        "RUB",
                        null,
                        8_000_000L
                );

        assertEquals(
                47_904L,
                calculation
                        .effectiveHourlyRateMinor(
                                salary,
                                10_020
                        )
        );
    }

    private CompensationTerm term(
            LocalDate effectiveFrom,
            String mode,
            String currency,
            Long hourly,
            Long salary
    ) {
        CompensationTerm term =
                new CompensationTerm(
                        user,
                        effectiveFrom
                );

        term.update(
                mode,
                currency,
                hourly,
                salary
        );

        return term;
    }

    private ProductionCalendarMonthDto production(
            String month,
            int productionNorm,
            boolean complete
    ) {
        int days =
                complete
                        ? 31
                        : 10;

        return new ProductionCalendarMonthDto(
                month,
                productionNorm,
                productionNorm,
                0,
                0,
                0,
                0,
                0,
                days,
                complete,
                List.of()
        );
    }
}
