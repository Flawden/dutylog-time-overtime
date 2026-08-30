package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.CompensationTerm;
import ru.daniil.shifts.repo.CompensationTermRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AverageEarningsParagraph8TariffSalaryAuthorityServiceTest {

    private final CompensationTermRepository terms =
            mock(CompensationTermRepository.class);

    private final AverageEarningsParagraph8TariffSalaryAuthorityService service =
            new AverageEarningsParagraph8TariffSalaryAuthorityService(terms);

    private final AppUser user =
            new AppUser(
                    "paragraph8-authority-user",
                    "{noop}unused"
            );

    @Test
    void hourlyResolvesExactConfiguredTariffFromHistoricalEventMonthTerm() {
        LocalDate eventDate = LocalDate.of(2026, 8, 20);
        CompensationTerm term = term(
                LocalDate.of(2026, 7, 1),
                "HOURLY",
                "RUB",
                123_456L,
                null
        );
        whenLookup(eventDate, term);

        var resolved = service.resolve(user, eventDate);

        assertTrue(resolved.ready());
        assertEquals(YearMonth.of(2026, 8), resolved.eventMonth());
        assertEquals(LocalDate.of(2026, 8, 1), resolved.compensationBoundary());
        assertEquals(LocalDate.of(2026, 7, 1), resolved.compensationEffectiveFrom());
        assertEquals(
                AverageEarningsParagraph8TariffSalaryAuthorityService.EstablishedBasis.HOURLY_TARIFF_RATE,
                resolved.establishedBasis()
        );
        assertEquals("HOURLY", resolved.payMode());
        assertEquals("RUB", resolved.currencyCode());
        assertEquals(123_456L, resolved.hourlyTariffRateMinor());
        assertNull(resolved.monthlyOfficialSalaryMinor());
        assertEquals(123_456L, resolved.establishedAmountMinor());
    }

    @Test
    void salaryResolvesExactConfiguredMonthlyOfficialSalaryWithoutHourlyDerivation() {
        LocalDate eventDate = LocalDate.of(2026, 8, 20);
        CompensationTerm term = term(
                LocalDate.of(2026, 8, 1),
                "SALARY",
                "RUB",
                null,
                8_000_000L
        );
        whenLookup(eventDate, term);

        var resolved = service.resolve(user, eventDate);

        assertTrue(resolved.ready());
        assertEquals(
                AverageEarningsParagraph8TariffSalaryAuthorityService.EstablishedBasis.MONTHLY_OFFICIAL_SALARY,
                resolved.establishedBasis()
        );
        assertEquals("SALARY", resolved.payMode());
        assertNull(resolved.hourlyTariffRateMinor());
        assertEquals(8_000_000L, resolved.monthlyOfficialSalaryMinor());
        assertEquals(8_000_000L, resolved.establishedAmountMinor());
    }

    @Test
    void eventDateOnFirstDayStillLooksUpEventMonthStart() {
        LocalDate eventDate = LocalDate.of(2026, 8, 1);
        CompensationTerm term = term(
                LocalDate.of(2026, 8, 1),
                "HOURLY",
                "RUB",
                100_000L,
                null
        );
        whenLookup(eventDate, term);

        var resolved = service.resolve(user, eventDate);

        assertTrue(resolved.ready());
        verify(terms).findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                user,
                LocalDate.of(2026, 8, 1)
        );
    }

    @Test
    void olderEffectiveTermRemainsValidForLaterEventMonth() {
        LocalDate eventDate = LocalDate.of(2026, 8, 31);
        CompensationTerm term = term(
                LocalDate.of(2026, 3, 1),
                "SALARY",
                "RUB",
                null,
                7_500_000L
        );
        whenLookup(eventDate, term);

        var resolved = service.resolve(user, eventDate);

        assertTrue(resolved.ready());
        assertEquals(LocalDate.of(2026, 3, 1), resolved.compensationEffectiveFrom());
        assertEquals(7_500_000L, resolved.establishedAmountMinor());
    }

    @Test
    void missingTermBlocksWithoutPartialAuthority() {
        LocalDate eventDate = LocalDate.of(2026, 8, 20);
        when(terms.findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                user,
                LocalDate.of(2026, 8, 1)
        )).thenReturn(Optional.empty());

        var resolved = service.resolve(user, eventDate);

        assertFalse(resolved.ready());
        assertEquals(
                AverageEarningsParagraph8TariffSalaryAuthorityService.COMPENSATION_TERM_MISSING,
                resolved.blockingReason()
        );
        assertNull(resolved.compensationEffectiveFrom());
        assertNull(resolved.establishedBasis());
        assertNull(resolved.currencyCode());
        assertNull(resolved.establishedAmountMinor());
    }

    @Test
    void invalidCurrencyBlocks() {
        LocalDate eventDate = LocalDate.of(2026, 8, 20);
        CompensationTerm term = mock(CompensationTerm.class);
        when(term.getEffectiveFrom()).thenReturn(LocalDate.of(2026, 8, 1));
        when(term.getCurrencyCode()).thenReturn("rub");
        when(term.getPayMode()).thenReturn("HOURLY");
        when(term.getHourlyRateMinor()).thenReturn(100_000L);
        whenLookup(eventDate, term);

        var resolved = service.resolve(user, eventDate);

        assertFalse(resolved.ready());
        assertEquals(
                AverageEarningsParagraph8TariffSalaryAuthorityService.CURRENCY_INVALID,
                resolved.blockingReason()
        );
    }

    @Test
    void futureEffectiveTermBlocks() {
        LocalDate eventDate = LocalDate.of(2026, 8, 20);
        CompensationTerm term = term(
                LocalDate.of(2026, 9, 1),
                "HOURLY",
                "RUB",
                100_000L,
                null
        );
        whenLookup(eventDate, term);

        var resolved = service.resolve(user, eventDate);

        assertFalse(resolved.ready());
        assertEquals(
                AverageEarningsParagraph8TariffSalaryAuthorityService.COMPENSATION_TERM_INVALID,
                resolved.blockingReason()
        );
    }

    @Test
    void nonMonthBoundaryEffectiveTermBlocks() {
        LocalDate eventDate = LocalDate.of(2026, 8, 20);
        CompensationTerm term = mock(CompensationTerm.class);
        when(term.getEffectiveFrom()).thenReturn(LocalDate.of(2026, 7, 15));
        when(term.getCurrencyCode()).thenReturn("RUB");
        when(term.getPayMode()).thenReturn("HOURLY");
        when(term.getHourlyRateMinor()).thenReturn(100_000L);
        whenLookup(eventDate, term);

        var resolved = service.resolve(user, eventDate);

        assertFalse(resolved.ready());
        assertEquals(
                AverageEarningsParagraph8TariffSalaryAuthorityService.COMPENSATION_TERM_INVALID,
                resolved.blockingReason()
        );
    }

    @Test
    void unsupportedPayModeBlocks() {
        LocalDate eventDate = LocalDate.of(2026, 8, 20);
        CompensationTerm term = term(
                LocalDate.of(2026, 8, 1),
                "PIECEWORK",
                "RUB",
                null,
                null
        );
        whenLookup(eventDate, term);

        var resolved = service.resolve(user, eventDate);

        assertFalse(resolved.ready());
        assertEquals(
                AverageEarningsParagraph8TariffSalaryAuthorityService.COMPENSATION_TERM_INVALID,
                resolved.blockingReason()
        );
    }

    @Test
    void hourlyMissingRateBlocks() {
        assertHourlyShapeBlocked(null, null);
    }

    @Test
    void hourlyNonPositiveRateBlocks() {
        assertHourlyShapeBlocked(0L, null);
    }

    @Test
    void hourlyContaminatedBySalaryBlocks() {
        assertHourlyShapeBlocked(100_000L, 8_000_000L);
    }

    @Test
    void salaryMissingAmountBlocks() {
        assertSalaryShapeBlocked(null, null);
    }

    @Test
    void salaryNonPositiveAmountBlocks() {
        assertSalaryShapeBlocked(null, 0L);
    }

    @Test
    void salaryContaminatedByHourlyRateBlocks() {
        assertSalaryShapeBlocked(100_000L, 8_000_000L);
    }

    @Test
    void nullUserIsRejectedBeforeRepositoryAccess() {
        assertThrows(
                NullPointerException.class,
                () -> service.resolve(null, LocalDate.of(2026, 8, 20))
        );
        verifyNoInteractions(terms);
    }

    @Test
    void nullEventDateIsRejectedBeforeRepositoryAccess() {
        assertThrows(
                NullPointerException.class,
                () -> service.resolve(user, null)
        );
        verifyNoInteractions(terms);
    }

    @Test
    void unsupportedLegalRegimeFailsBeforeRepositoryLookup() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> service.resolve(user, LocalDate.of(2025, 8, 31))
        );
        verifyNoInteractions(terms);
    }

    @Test
    void readyRecordRejectsDualMoneyIdentity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution.ready(
                        LocalDate.of(2026, 8, 20),
                        YearMonth.of(2026, 8),
                        LocalDate.of(2026, 8, 1),
                        AverageEarningsLegalPolicy.LegalRegime.RU_PP_540_2025,
                        LocalDate.of(2026, 8, 1),
                        AverageEarningsParagraph8TariffSalaryAuthorityService.EstablishedBasis.HOURLY_TARIFF_RATE,
                        "HOURLY",
                        "RUB",
                        100_000L,
                        8_000_000L
                )
        );
    }

    @Test
    void readyRecordRejectsDerivedHourlyShapeForSalaryBasis() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution.ready(
                        LocalDate.of(2026, 8, 20),
                        YearMonth.of(2026, 8),
                        LocalDate.of(2026, 8, 1),
                        AverageEarningsLegalPolicy.LegalRegime.RU_PP_540_2025,
                        LocalDate.of(2026, 8, 1),
                        AverageEarningsParagraph8TariffSalaryAuthorityService.EstablishedBasis.MONTHLY_OFFICIAL_SALARY,
                        "SALARY",
                        "RUB",
                        47_904L,
                        8_000_000L
                )
        );
    }

    @Test
    void blockedRecordRejectsPartialCompensationIdentity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution(
                        LocalDate.of(2026, 8, 20),
                        YearMonth.of(2026, 8),
                        LocalDate.of(2026, 8, 1),
                        AverageEarningsLegalPolicy.LegalRegime.RU_PP_540_2025,
                        false,
                        "BLOCKED",
                        "blocked",
                        LocalDate.of(2026, 8, 1),
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );
    }

    private void assertHourlyShapeBlocked(
            Long hourlyRateMinor,
            Long monthlySalaryMinor
    ) {
        reset(terms);
        LocalDate eventDate = LocalDate.of(2026, 8, 20);
        CompensationTerm term = term(
                LocalDate.of(2026, 8, 1),
                "HOURLY",
                "RUB",
                hourlyRateMinor,
                monthlySalaryMinor
        );
        whenLookup(eventDate, term);

        var resolved = service.resolve(user, eventDate);

        assertFalse(resolved.ready());
        assertEquals(
                AverageEarningsParagraph8TariffSalaryAuthorityService.COMPENSATION_TERM_INVALID,
                resolved.blockingReason()
        );
        assertNull(resolved.establishedAmountMinor());
    }

    private void assertSalaryShapeBlocked(
            Long hourlyRateMinor,
            Long monthlySalaryMinor
    ) {
        reset(terms);
        LocalDate eventDate = LocalDate.of(2026, 8, 20);
        CompensationTerm term = term(
                LocalDate.of(2026, 8, 1),
                "SALARY",
                "RUB",
                hourlyRateMinor,
                monthlySalaryMinor
        );
        whenLookup(eventDate, term);

        var resolved = service.resolve(user, eventDate);

        assertFalse(resolved.ready());
        assertEquals(
                AverageEarningsParagraph8TariffSalaryAuthorityService.COMPENSATION_TERM_INVALID,
                resolved.blockingReason()
        );
        assertNull(resolved.establishedAmountMinor());
    }

    private void whenLookup(
            LocalDate eventDate,
            CompensationTerm term
    ) {
        when(terms.findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                user,
                YearMonth.from(eventDate).atDay(1)
        )).thenReturn(Optional.ofNullable(term));
    }

    private static CompensationTerm term(
            LocalDate effectiveFrom,
            String payMode,
            String currency,
            Long hourlyRateMinor,
            Long monthlySalaryMinor
    ) {
        CompensationTerm term = new CompensationTerm(
                null,
                effectiveFrom
        );
        term.update(
                payMode,
                currency,
                hourlyRateMinor,
                monthlySalaryMinor
        );
        return term;
    }
}
