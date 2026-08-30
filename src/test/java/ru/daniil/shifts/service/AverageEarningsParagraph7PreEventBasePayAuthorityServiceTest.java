package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.daniil.shifts.dto.Dtos.ProductionCalendarMonthDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.CompensationTerm;
import ru.daniil.shifts.repo.CompensationTermRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AverageEarningsParagraph7PreEventBasePayAuthorityServiceTest {
    private static final LocalDate EVENT = LocalDate.of(2026, 8, 20);
    private static final LocalDate FROM = LocalDate.of(2026, 8, 1);

    @Mock AverageEarningsParagraph7PreEventWorkFactService workFacts;
    @Mock CompensationTermRepository terms;
    @Mock ProductionCalendarService productionCalendar;
    @Mock AppUser user;

    private AverageEarningsParagraph7PreEventBasePayAuthorityService service;

    @BeforeEach
    void setUp() {
        service = new AverageEarningsParagraph7PreEventBasePayAuthorityService(
                workFacts,
                terms,
                productionCalendar
        );
    }

    @Test
    void nullUserRejected() {
        assertThrows(NullPointerException.class, () -> service.resolve(null, EVENT));
    }

    @Test
    void nullEventRejected() {
        assertThrows(NullPointerException.class, () -> service.resolve(user, null));
    }

    @Test
    void noWorkedTimeIsReadyZeroWithoutPricingLookup() {
        var work = work(EVENT, List.of());
        when(workFacts.resolve(user, EVENT)).thenReturn(work);

        var result = service.resolve(user, EVENT);

        assertTrue(result.ready());
        assertFalse(result.workedTimePresent());
        assertFalse(result.basePayQuantityPresent());
        assertEquals(0L, result.eligibleBasePayMinutes());
        assertNull(result.payMode());
        verifyNoInteractions(terms, productionCalendar);
    }

    @Test
    void mismatchedWorkWindowBlocksBeforePricingLookup() {
        var work = AverageEarningsParagraph7PreEventWorkFactService.Resolution.ready(
                EVENT.minusDays(1),
                FROM,
                EVENT.minusDays(1),
                0,
                0L,
                List.of()
        );
        when(workFacts.resolve(user, EVENT)).thenReturn(work);

        var result = service.resolve(user, EVENT);

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventBasePayAuthorityService.WORK_AUTHORITY_WINDOW_MISMATCH,
                result.blockingReason()
        );
        verifyNoInteractions(terms, productionCalendar);
    }

    @Test
    void missingCompensationTermBlocksWithoutPartialPricingIdentity() {
        var work = work(EVENT, List.of(day(5, 480, 480, 480)));
        when(workFacts.resolve(user, EVENT)).thenReturn(work);
        when(terms.findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(user, FROM))
                .thenReturn(Optional.empty());

        var result = service.resolve(user, EVENT);

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventBasePayAuthorityService.COMPENSATION_TERM_MISSING,
                result.blockingReason()
        );
        assertNull(result.currencyCode());
        assertNull(result.payMode());
        assertEquals(0L, result.eligibleBasePayMinutes());
    }

    @Test
    void invalidCurrencyBlocksFailClosed() {
        var work = work(EVENT, List.of(day(5, 480, 480, 480)));
        var term = hourlyTerm("rub", 10000L);
        when(workFacts.resolve(user, EVENT)).thenReturn(work);
        when(terms.findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(user, FROM))
                .thenReturn(Optional.of(term));

        var result = service.resolve(user, EVENT);

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventBasePayAuthorityService.CURRENCY_INVALID,
                result.blockingReason()
        );
        assertNull(result.currencyCode());
    }

    @Test
    void effectiveDateAfterMonthBoundaryBlocks() {
        var work = work(EVENT, List.of(day(5, 480, 480, 480)));
        var term = hourlyTerm("RUB", 10000L);
        when(term.getEffectiveFrom()).thenReturn(FROM.plusDays(1));
        when(workFacts.resolve(user, EVENT)).thenReturn(work);
        when(terms.findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(user, FROM))
                .thenReturn(Optional.of(term));

        var result = service.resolve(user, EVENT);

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventBasePayAuthorityService.COMPENSATION_TERM_INVALID,
                result.blockingReason()
        );
    }

    @Test
    void unsupportedPayModeBlocks() {
        var work = work(EVENT, List.of(day(5, 480, 480, 480)));
        var term = mock(CompensationTerm.class);
        when(term.getCurrencyCode()).thenReturn("RUB");
        when(term.getEffectiveFrom()).thenReturn(FROM);
        when(term.getPayMode()).thenReturn("PIECEWORK");
        when(workFacts.resolve(user, EVENT)).thenReturn(work);
        when(terms.findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(user, FROM))
                .thenReturn(Optional.of(term));

        var result = service.resolve(user, EVENT);

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventBasePayAuthorityService.COMPENSATION_TERM_INVALID,
                result.blockingReason()
        );
    }

    @Test
    void hourlyRateMustBePositive() {
        var work = work(EVENT, List.of(day(5, 480, 480, 480)));
        var term = hourlyTerm("RUB", 0L);
        when(workFacts.resolve(user, EVENT)).thenReturn(work);
        when(terms.findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(user, FROM))
                .thenReturn(Optional.of(term));

        var result = service.resolve(user, EVENT);

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventBasePayAuthorityService.COMPENSATION_TERM_INVALID,
                result.blockingReason()
        );
    }

    @Test
    void hourlyAuthorityUsesOnlyHourlyBaseWorkedMinutes() {
        var work = work(EVENT, List.of(
                day(5, 600, 480, 480),
                day(6, 540, 480, 420)
        ));
        var term = hourlyTerm("RUB", 15000L);
        when(workFacts.resolve(user, EVENT)).thenReturn(work);
        when(terms.findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(user, FROM))
                .thenReturn(Optional.of(term));

        var result = service.resolve(user, EVENT);

        assertTrue(result.ready());
        assertEquals("HOURLY", result.payMode());
        assertEquals(900L, result.eligibleBasePayMinutes());
        assertEquals(15000L, result.configuredHourlyRateMinor());
        assertNull(result.monthlySalaryMinor());
        assertNull(result.productionNormMinutes());
        verifyNoInteractions(productionCalendar);
    }

    @Test
    void hourlyAllBankedOvertimeCanHaveWorkedTimeButZeroBaseQuantity() {
        var work = work(EVENT, List.of(day(5, 120, 0, 0)));
        var term = hourlyTerm("RUB", 15000L);
        when(workFacts.resolve(user, EVENT)).thenReturn(work);
        when(terms.findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(user, FROM))
                .thenReturn(Optional.of(term));

        var result = service.resolve(user, EVENT);

        assertTrue(result.ready());
        assertTrue(result.workedTimePresent());
        assertFalse(result.basePayQuantityPresent());
        assertEquals(0L, result.eligibleBasePayMinutes());
    }

    @Test
    void salaryRequiresCompleteProductionNormAuthority() {
        var work = work(EVENT, List.of(day(5, 480, 480, 480)));
        var term = salaryTerm("RUB", 100_000_00L);
        var production = mock(ProductionCalendarMonthDto.class);
        when(production.scheduleCoverageComplete()).thenReturn(false);
        when(workFacts.resolve(user, EVENT)).thenReturn(work);
        when(terms.findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(user, FROM))
                .thenReturn(Optional.of(term));
        when(productionCalendar.month(user, "2026-08")).thenReturn(production);

        var result = service.resolve(user, EVENT);

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventBasePayAuthorityService.PRODUCTION_NORM_INCOMPLETE,
                result.blockingReason()
        );
    }

    @Test
    void salaryRequiresPositiveProductionNorm() {
        var work = work(EVENT, List.of(day(5, 480, 480, 480)));
        var term = salaryTerm("RUB", 100_000_00L);
        var production = mock(ProductionCalendarMonthDto.class);
        when(production.scheduleCoverageComplete()).thenReturn(true);
        when(production.productionNormMinutes()).thenReturn(0);
        when(workFacts.resolve(user, EVENT)).thenReturn(work);
        when(terms.findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(user, FROM))
                .thenReturn(Optional.of(term));
        when(productionCalendar.month(user, "2026-08")).thenReturn(production);

        var result = service.resolve(user, EVENT);

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventBasePayAuthorityService.PRODUCTION_NORM_REQUIRED,
                result.blockingReason()
        );
    }

    @Test
    void salaryAuthorityCapsEachWorkedDayAtPlannedMinutes() {
        var work = work(EVENT, List.of(
                day(5, 600, 480, 480),
                day(6, 300, 480, 300),
                day(7, 120, 0, 0)
        ));
        var term = salaryTerm("RUB", 100_000_00L);
        var production = mock(ProductionCalendarMonthDto.class);
        when(production.scheduleCoverageComplete()).thenReturn(true);
        when(production.productionNormMinutes()).thenReturn(9600);
        when(workFacts.resolve(user, EVENT)).thenReturn(work);
        when(terms.findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(user, FROM))
                .thenReturn(Optional.of(term));
        when(productionCalendar.month(user, "2026-08")).thenReturn(production);

        var result = service.resolve(user, EVENT);

        assertTrue(result.ready());
        assertEquals("SALARY", result.payMode());
        assertEquals(780L, result.eligibleBasePayMinutes());
        assertEquals(100_000_00L, result.monthlySalaryMinor());
        assertEquals(9600, result.productionNormMinutes());
        assertNull(result.configuredHourlyRateMinor());
    }

    @Test
    void compensationLookupIsAnchoredToFirstDayOfLegalEventMonth() {
        var work = work(EVENT, List.of(day(18, 480, 480, 480)));
        var term = hourlyTerm("RUB", 12345L);
        when(workFacts.resolve(user, EVENT)).thenReturn(work);
        when(terms.findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(user, FROM))
                .thenReturn(Optional.of(term));

        service.resolve(user, EVENT);

        verify(terms).findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                user,
                FROM
        );
    }

    @Test
    void readyAuthorityCarriesOriginalWorkFactsForAudit() {
        var work = work(EVENT, List.of(day(5, 480, 480, 480)));
        var term = hourlyTerm("RUB", 12345L);
        when(workFacts.resolve(user, EVENT)).thenReturn(work);
        when(terms.findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(user, FROM))
                .thenReturn(Optional.of(term));

        var result = service.resolve(user, EVENT);

        assertSame(work, result.workFacts());
        assertEquals(EVENT, result.cutoffExclusive());
        assertEquals(FROM, result.periodFrom());
    }

    private AverageEarningsParagraph7PreEventWorkFactService.Resolution work(
            LocalDate event,
            List<AverageEarningsParagraph7PreEventWorkFactService.WorkedDayFact> days
    ) {
        long minutes = days.stream().mapToLong(
                AverageEarningsParagraph7PreEventWorkFactService.WorkedDayFact::workedMinutes
        ).sum();
        return AverageEarningsParagraph7PreEventWorkFactService.Resolution.ready(
                event,
                LocalDate.of(event.getYear(), event.getMonth(), 1),
                event,
                days.size(),
                minutes,
                days
        );
    }

    private AverageEarningsParagraph7PreEventWorkFactService.WorkedDayFact day(
            int dayOfMonth,
            int worked,
            int planned,
            int hourlyBase
    ) {
        return new AverageEarningsParagraph7PreEventWorkFactService.WorkedDayFact(
                LocalDate.of(2026, 8, dayOfMonth),
                worked,
                planned,
                hourlyBase
        );
    }

    private CompensationTerm hourlyTerm(String currency, long rate) {
        CompensationTerm term = mock(CompensationTerm.class);
        lenient().when(term.getCurrencyCode()).thenReturn(currency);
        lenient().when(term.getEffectiveFrom()).thenReturn(FROM);
        lenient().when(term.getPayMode()).thenReturn("HOURLY");
        lenient().when(term.getHourlyRateMinor()).thenReturn(rate);
        return term;
    }

    private CompensationTerm salaryTerm(String currency, long salary) {
        CompensationTerm term = mock(CompensationTerm.class);
        lenient().when(term.getCurrencyCode()).thenReturn(currency);
        lenient().when(term.getEffectiveFrom()).thenReturn(FROM);
        lenient().when(term.getPayMode()).thenReturn("SALARY");
        lenient().when(term.getMonthlySalaryMinor()).thenReturn(salary);
        return term;
    }
}
