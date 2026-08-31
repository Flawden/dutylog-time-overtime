package ru.daniil.shifts.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ru.daniil.shifts.model.AppUser;

class VacationPayOrchestratorTest {

    private static final LocalDate EVENT = LocalDate.of(2026, 9, 15);
    private static final YearMonth EVENT_MONTH = YearMonth.from(EVENT);
    private static final Long ABSENCE_ID = 77L;

    private VacationPayableDaysFactService payableDays;
    private VacationPayOrchestrator.DailyResolver dailyResolver;
    private VacationPayOrchestrator.MoneyCalculator moneyCalculator;
    private AppUser user;
    private AverageEarningsOrderedFallbackResolver.Resolution ordered;

    @BeforeEach
    void setUp() {
        payableDays = mock(VacationPayableDaysFactService.class);
        dailyResolver = mock(VacationPayOrchestrator.DailyResolver.class);
        moneyCalculator = mock(VacationPayOrchestrator.MoneyCalculator.class);
        user = mock(AppUser.class);
        ordered = ordered(
                AverageEarningsOrderedFallbackResolver.Selection.PRIMARY_REFERENCE_PERIOD
        );
    }

    @Test
    void constructorRejectsNullPayableDaysAuthority() {
        assertThrows(
                NullPointerException.class,
                () -> new VacationPayOrchestrator(null, dailyResolver, moneyCalculator)
        );
    }

    @Test
    void constructorRejectsNullDailyResolver() {
        assertThrows(
                NullPointerException.class,
                () -> new VacationPayOrchestrator(payableDays, null, moneyCalculator)
        );
    }

    @Test
    void constructorRejectsNullMoneyCalculator() {
        assertThrows(
                NullPointerException.class,
                () -> new VacationPayOrchestrator(payableDays, dailyResolver, null)
        );
    }

    @Test
    void nullUserIsRejectedBeforeL() {
        var service = service();

        assertThrows(
                NullPointerException.class,
                () -> service.resolve(
                        null,
                        EVENT,
                        ABSENCE_ID,
                        ordered,
                        () -> null,
                        () -> null,
                        () -> null
                )
        );

        verifyNoInteractions(payableDays);
    }

    @Test
    void nullEventDateIsRejectedBeforeL() {
        var service = service();

        assertThrows(
                NullPointerException.class,
                () -> service.resolve(
                        user,
                        null,
                        ABSENCE_ID,
                        ordered,
                        () -> null,
                        () -> null,
                        () -> null
                )
        );

        verifyNoInteractions(payableDays);
    }

    @Test
    void lBlockedWinsBeforeNullOrderedFallbackOrSuppliers() {
        var blocked = blockedL("L_BLOCK");
        when(payableDays.resolve(user, EVENT, ABSENCE_ID)).thenReturn(blocked);

        var result = service().resolve(
                user,
                EVENT,
                ABSENCE_ID,
                null,
                null,
                null,
                null
        );

        assertFalse(result.ready());
        assertSame(blocked, result.payableDaysAuthority());
        verifyNoInteractions(dailyResolver, moneyCalculator);
    }

    @Test
    void lBlockedPreservesUpstreamReason() {
        var blocked = blockedL("TK_RF_ARTICLE_120_POSTED_STATUS_REQUIRED");
        when(payableDays.resolve(user, EVENT, ABSENCE_ID)).thenReturn(blocked);

        var result = service().resolve(
                user,
                EVENT,
                ABSENCE_ID,
                ordered,
                () -> null,
                () -> null,
                () -> null
        );

        assertEquals(
                "TK_RF_ARTICLE_120_POSTED_STATUS_REQUIRED",
                result.upstreamBlockingReason()
        );
    }

    @Test
    void lBlockedUsesOwnOrchestrationReason() {
        var blocked = blockedL("L_BLOCK");
        when(payableDays.resolve(user, EVENT, ABSENCE_ID)).thenReturn(blocked);

        var result = service().resolve(
                user,
                EVENT,
                ABSENCE_ID,
                ordered,
                () -> null,
                () -> null,
                () -> null
        );

        assertEquals(
                VacationPayOrchestrator.PAYABLE_DAYS_AUTHORITY_BLOCKED,
                result.blockingReason()
        );
        assertEquals(
                VacationPayOrchestrator.BlockingStage.PAYABLE_DAYS_AUTHORITY,
                result.blockingStage()
        );
    }

    @Test
    void lBlockedExposesOnlyLProvenance() {
        var blocked = blockedL("L_BLOCK");
        when(payableDays.resolve(user, EVENT, ABSENCE_ID)).thenReturn(blocked);

        var result = service().resolve(
                user,
                EVENT,
                ABSENCE_ID,
                ordered,
                () -> null,
                () -> null,
                () -> null
        );

        assertSame(blocked, result.payableDaysAuthority());
        assertNull(result.dailyAuthority());
        assertNull(result.moneyAuthority());
        assertNull(result.currencyCode());
        assertNull(result.vacationPayMinor());
        assertEquals(0, result.payableCalendarDays());
    }

    @Test
    void lBlockedDoesNotInvokeDailyResolver() {
        var blocked = blockedL("L_BLOCK");
        when(payableDays.resolve(user, EVENT, ABSENCE_ID)).thenReturn(blocked);

        service().resolve(
                user,
                EVENT,
                ABSENCE_ID,
                ordered,
                () -> null,
                () -> null,
                () -> null
        );

        verifyNoInteractions(dailyResolver);
    }

    @Test
    void lBlockedDoesNotEvaluateAnyKSupplier() {
        var blocked = blockedL("L_BLOCK");
        when(payableDays.resolve(user, EVENT, ABSENCE_ID)).thenReturn(blocked);
        AtomicInteger referenceCalls = new AtomicInteger();
        AtomicInteger p7Calls = new AtomicInteger();
        AtomicInteger p8Calls = new AtomicInteger();

        service().resolve(
                user,
                EVENT,
                ABSENCE_ID,
                ordered,
                () -> {
                    referenceCalls.incrementAndGet();
                    return null;
                },
                () -> {
                    p7Calls.incrementAndGet();
                    return null;
                },
                () -> {
                    p8Calls.incrementAndGet();
                    return null;
                }
        );

        assertEquals(0, referenceCalls.get());
        assertEquals(0, p7Calls.get());
        assertEquals(0, p8Calls.get());
    }

    @Test
    void readyLThenNullOrderedFallbackIsRejected() {
        var ready = readyL(5);
        when(payableDays.resolve(user, EVENT, ABSENCE_ID)).thenReturn(ready);

        assertThrows(
                NullPointerException.class,
                () -> service().resolve(
                        user,
                        EVENT,
                        ABSENCE_ID,
                        null,
                        () -> null,
                        () -> null,
                        () -> null
                )
        );

        verify(payableDays).resolve(user, EVENT, ABSENCE_ID);
    }

    @Test
    void readyLThenNullReferenceSupplierIsRejected() {
        var ready = readyL(5);
        when(payableDays.resolve(user, EVENT, ABSENCE_ID)).thenReturn(ready);

        assertThrows(
                NullPointerException.class,
                () -> service().resolve(
                        user,
                        EVENT,
                        ABSENCE_ID,
                        ordered,
                        null,
                        () -> null,
                        () -> null
                )
        );
    }

    @Test
    void readyLThenNullParagraph7SupplierIsRejected() {
        var ready = readyL(5);
        when(payableDays.resolve(user, EVENT, ABSENCE_ID)).thenReturn(ready);

        assertThrows(
                NullPointerException.class,
                () -> service().resolve(
                        user,
                        EVENT,
                        ABSENCE_ID,
                        ordered,
                        () -> null,
                        null,
                        () -> null
                )
        );
    }

    @Test
    void readyLThenNullParagraph8SupplierIsRejected() {
        var ready = readyL(5);
        when(payableDays.resolve(user, EVENT, ABSENCE_ID)).thenReturn(ready);

        assertThrows(
                NullPointerException.class,
                () -> service().resolve(
                        user,
                        EVENT,
                        ABSENCE_ID,
                        ordered,
                        () -> null,
                        () -> null,
                        null
                )
        );
    }

    @Test
    void blockedJ5MakesKBlockedAndMIsNotCalled() {
        var l = readyL(5);
        when(payableDays.resolve(user, EVENT, ABSENCE_ID)).thenReturn(l);
        var blockedOrdered = mock(AverageEarningsOrderedFallbackResolver.Resolution.class);
        when(blockedOrdered.eventDate()).thenReturn(EVENT);
        when(blockedOrdered.eventMonth()).thenReturn(EVENT_MONTH);
        when(blockedOrdered.ready()).thenReturn(false);
        when(blockedOrdered.blockingReason()).thenReturn("J5_BLOCK");

        var service = new VacationPayOrchestrator(
                payableDays,
                VacationAverageUnifiedDailyResolver::resolve,
                moneyCalculator
        );
        var result = service.resolve(
                user,
                EVENT,
                ABSENCE_ID,
                blockedOrdered,
                () -> null,
                () -> null,
                () -> null
        );

        assertFalse(result.ready());
        assertEquals(VacationPayOrchestrator.BlockingStage.DAILY_AUTHORITY, result.blockingStage());
        verifyNoInteractions(moneyCalculator);
    }

    @Test
    void kBlockedPreservesKUpstreamReason() {
        var l = readyL(5);
        var k = blockedK("K_BLOCK");
        when(payableDays.resolve(user, EVENT, ABSENCE_ID)).thenReturn(l);
        when(dailyResolver.resolve(any(), any(), any(), any())).thenReturn(k);

        var result = service().resolve(
                user,
                EVENT,
                ABSENCE_ID,
                ordered,
                () -> null,
                () -> null,
                () -> null
        );

        assertEquals(VacationPayOrchestrator.DAILY_AUTHORITY_BLOCKED, result.blockingReason());
        assertEquals("K_BLOCK", result.upstreamBlockingReason());
    }

    @Test
    void kBlockedExposesLAndKButNoM() {
        var l = readyL(5);
        var k = blockedK("K_BLOCK");
        when(payableDays.resolve(user, EVENT, ABSENCE_ID)).thenReturn(l);
        when(dailyResolver.resolve(any(), any(), any(), any())).thenReturn(k);

        var result = service().resolve(
                user,
                EVENT,
                ABSENCE_ID,
                ordered,
                () -> null,
                () -> null,
                () -> null
        );

        assertSame(l, result.payableDaysAuthority());
        assertSame(k, result.dailyAuthority());
        assertNull(result.moneyAuthority());
        assertNull(result.vacationPayMinor());
    }

    @Test
    void primaryUsesOnlyReferenceSupplierThroughRealK() {
        var l = readyL(2);
        when(payableDays.resolve(user, EVENT, ABSENCE_ID)).thenReturn(l);
        var primaryOrdered = ordered(
                AverageEarningsOrderedFallbackResolver.Selection.PRIMARY_REFERENCE_PERIOD
        );
        var window = AverageEarningsReferenceWindow.primary(EVENT);
        when(primaryOrdered.selectedReferenceWindow()).thenReturn(window);
        var reference = reference(window, exact(1200L, 1L));
        AtomicInteger referenceCalls = new AtomicInteger();
        AtomicInteger p7Calls = new AtomicInteger();
        AtomicInteger p8Calls = new AtomicInteger();
        var readyMoney = readyM(2400L, "RUB");
        when(moneyCalculator.calculate(any(), any())).thenReturn(readyMoney);

        var result = new VacationPayOrchestrator(
                payableDays,
                VacationAverageUnifiedDailyResolver::resolve,
                moneyCalculator
        ).resolve(
                user,
                EVENT,
                ABSENCE_ID,
                primaryOrdered,
                () -> {
                    referenceCalls.incrementAndGet();
                    return reference;
                },
                () -> {
                    p7Calls.incrementAndGet();
                    return null;
                },
                () -> {
                    p8Calls.incrementAndGet();
                    return null;
                }
        );

        assertTrue(result.ready());
        assertEquals(1, referenceCalls.get());
        assertEquals(0, p7Calls.get());
        assertEquals(0, p8Calls.get());
    }

    @Test
    void paragraph7UsesOnlyParagraph7SupplierThroughRealK() {
        var l = readyL(2);
        when(payableDays.resolve(user, EVENT, ABSENCE_ID)).thenReturn(l);
        var p7Ordered = ordered(
                AverageEarningsOrderedFallbackResolver.Selection.PARAGRAPH_7_PRE_EVENT_ACCRUED_WAGE
        );
        var p7Authority = paragraph7(93000L, "RUB");
        when(p7Ordered.paragraph7Authority()).thenReturn(p7Authority);
        var basis = VacationAverageUnifiedDailyResolver.Paragraph7CalendarBasis.of(
                EVENT,
                14,
                "P10_EVENT_MONTH_CALENDAR_AUTHORITY"
        );
        AtomicInteger referenceCalls = new AtomicInteger();
        AtomicInteger p7Calls = new AtomicInteger();
        AtomicInteger p8Calls = new AtomicInteger();
        var readyMoney = readyM(1L, "RUB");
        when(moneyCalculator.calculate(any(), any())).thenReturn(readyMoney);

        var result = new VacationPayOrchestrator(
                payableDays,
                VacationAverageUnifiedDailyResolver::resolve,
                moneyCalculator
        ).resolve(
                user,
                EVENT,
                ABSENCE_ID,
                p7Ordered,
                () -> {
                    referenceCalls.incrementAndGet();
                    return null;
                },
                () -> {
                    p7Calls.incrementAndGet();
                    return basis;
                },
                () -> {
                    p8Calls.incrementAndGet();
                    return null;
                }
        );

        assertTrue(result.ready());
        assertEquals(0, referenceCalls.get());
        assertEquals(1, p7Calls.get());
        assertEquals(0, p8Calls.get());
    }

    @Test
    void paragraph8UsesOnlyParagraph8SupplierThroughRealK() {
        var l = readyL(2);
        when(payableDays.resolve(user, EVENT, ABSENCE_ID)).thenReturn(l);
        var p8Ordered = ordered(
                AverageEarningsOrderedFallbackResolver.Selection.PARAGRAPH_8_ESTABLISHED_TARIFF_OR_SALARY
        );
        var p8Authority = paragraph8Salary(90000L, "RUB");
        when(p8Ordered.paragraph8Authority()).thenReturn(p8Authority);
        var basis = VacationAverageUnifiedDailyResolver.Paragraph8FormulaBasis.monthlySalary(
                EVENT,
                "RUB",
                "P8_MONTHLY_SALARY_AUTHORITY"
        );
        AtomicInteger referenceCalls = new AtomicInteger();
        AtomicInteger p7Calls = new AtomicInteger();
        AtomicInteger p8Calls = new AtomicInteger();
        var readyMoney = readyM(1L, "RUB");
        when(moneyCalculator.calculate(any(), any())).thenReturn(readyMoney);

        var result = new VacationPayOrchestrator(
                payableDays,
                VacationAverageUnifiedDailyResolver::resolve,
                moneyCalculator
        ).resolve(
                user,
                EVENT,
                ABSENCE_ID,
                p8Ordered,
                () -> {
                    referenceCalls.incrementAndGet();
                    return null;
                },
                () -> {
                    p7Calls.incrementAndGet();
                    return null;
                },
                () -> {
                    p8Calls.incrementAndGet();
                    return basis;
                }
        );

        assertTrue(result.ready());
        assertEquals(0, referenceCalls.get());
        assertEquals(0, p7Calls.get());
        assertEquals(1, p8Calls.get());
    }

    @Test
    void readyFlowCallsMWithExactKAndLAuthorities() {
        var l = readyL(3);
        var k = readyK();
        var m = readyM(1234L, "RUB");
        when(payableDays.resolve(user, EVENT, ABSENCE_ID)).thenReturn(l);
        when(dailyResolver.resolve(any(), any(), any(), any())).thenReturn(k);
        when(moneyCalculator.calculate(k, l)).thenReturn(m);

        var result = service().resolve(
                user,
                EVENT,
                ABSENCE_ID,
                ordered,
                () -> null,
                () -> null,
                () -> null
        );

        assertTrue(result.ready());
        verify(moneyCalculator).calculate(k, l);
    }

    @Test
    void readyFlowReturnsCanonicalProvenance() {
        var l = readyL(3);
        var k = readyK();
        var m = readyM(1234L, "RUB");
        when(payableDays.resolve(user, EVENT, ABSENCE_ID)).thenReturn(l);
        when(dailyResolver.resolve(any(), any(), any(), any())).thenReturn(k);
        when(moneyCalculator.calculate(k, l)).thenReturn(m);

        var result = service().resolve(
                user,
                EVENT,
                ABSENCE_ID,
                ordered,
                () -> null,
                () -> null,
                () -> null
        );

        assertSame(l, result.payableDaysAuthority());
        assertSame(k, result.dailyAuthority());
        assertSame(m, result.moneyAuthority());
        assertEquals("RUB", result.currencyCode());
        assertEquals(1234L, result.vacationPayMinor());
        assertEquals(3, result.payableCalendarDays());
    }

    @Test
    void provenZeroPayableDaysRemainReadyZeroEndToEnd() {
        var l = readyL(0);
        when(payableDays.resolve(user, EVENT, ABSENCE_ID)).thenReturn(l);
        var primaryOrdered = ordered(
                AverageEarningsOrderedFallbackResolver.Selection.PRIMARY_REFERENCE_PERIOD
        );
        var window = AverageEarningsReferenceWindow.primary(EVENT);
        when(primaryOrdered.selectedReferenceWindow()).thenReturn(window);
        var reference = reference(window, exact(12345L, 2L));

        var result = new VacationPayOrchestrator(payableDays).resolve(
                user,
                EVENT,
                ABSENCE_ID,
                primaryOrdered,
                () -> reference,
                () -> null,
                () -> null
        );

        assertTrue(result.ready());
        assertEquals(0L, result.vacationPayMinor());
        assertEquals("RUB", result.currencyCode());
        assertEquals(0, result.payableCalendarDays());
    }

    @Test
    void mIdentityMismatchPropagatesWithoutPartialMoney() {
        var l = readyL(1, EVENT.plusDays(1));
        when(payableDays.resolve(user, EVENT, ABSENCE_ID)).thenReturn(l);
        var primaryOrdered = ordered(
                AverageEarningsOrderedFallbackResolver.Selection.PRIMARY_REFERENCE_PERIOD
        );
        var window = AverageEarningsReferenceWindow.primary(EVENT);
        when(primaryOrdered.selectedReferenceWindow()).thenReturn(window);
        var reference = reference(window, exact(100L, 1L));

        var result = new VacationPayOrchestrator(payableDays).resolve(
                user,
                EVENT,
                ABSENCE_ID,
                primaryOrdered,
                () -> reference,
                () -> null,
                () -> null
        );

        assertFalse(result.ready());
        assertEquals(VacationPayOrchestrator.BlockingStage.MONEY_FORMULA, result.blockingStage());
        assertEquals(VacationPayMoneyFormula.IDENTITY_MISMATCH, result.upstreamBlockingReason());
        assertNull(result.vacationPayMinor());
        assertNull(result.currencyCode());
        assertEquals(0, result.payableCalendarDays());
    }

    @Test
    void mOverflowPropagatesWithoutPartialMoney() {
        var l = readyL(1);
        when(payableDays.resolve(user, EVENT, ABSENCE_ID)).thenReturn(l);
        var primaryOrdered = ordered(
                AverageEarningsOrderedFallbackResolver.Selection.PRIMARY_REFERENCE_PERIOD
        );
        var window = AverageEarningsReferenceWindow.primary(EVENT);
        when(primaryOrdered.selectedReferenceWindow()).thenReturn(window);
        var huge = new VacationAverageDailyEarningsFormula.ExactMoneyPerDay(
                BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE),
                BigInteger.ONE
        );
        var reference = reference(window, huge);

        var result = new VacationPayOrchestrator(payableDays).resolve(
                user,
                EVENT,
                ABSENCE_ID,
                primaryOrdered,
                () -> reference,
                () -> null,
                () -> null
        );

        assertFalse(result.ready());
        assertEquals(VacationPayOrchestrator.MONEY_FORMULA_BLOCKED, result.blockingReason());
        assertEquals(VacationPayMoneyFormula.AMOUNT_OVERFLOW, result.upstreamBlockingReason());
        assertNull(result.vacationPayMinor());
        assertNull(result.currencyCode());
    }

    @Test
    void mBlockedSeamPreservesMReasonAndProvenance() {
        var l = readyL(3);
        var k = readyK();
        var m = blockedM(VacationPayMoneyFormula.CURRENCY_REQUIRED);
        when(payableDays.resolve(user, EVENT, ABSENCE_ID)).thenReturn(l);
        when(dailyResolver.resolve(any(), any(), any(), any())).thenReturn(k);
        when(moneyCalculator.calculate(k, l)).thenReturn(m);

        var result = service().resolve(
                user,
                EVENT,
                ABSENCE_ID,
                ordered,
                () -> null,
                () -> null,
                () -> null
        );

        assertFalse(result.ready());
        assertSame(m, result.moneyAuthority());
        assertEquals(VacationPayMoneyFormula.CURRENCY_REQUIRED, result.upstreamBlockingReason());
        assertNull(result.vacationPayMinor());
    }

    @Test
    void eventMonthAndRequestedAbsenceIdentityArePreserved() {
        var l = readyL(3);
        var k = readyK();
        var m = readyM(1234L, "RUB");
        when(payableDays.resolve(user, EVENT, ABSENCE_ID)).thenReturn(l);
        when(dailyResolver.resolve(any(), any(), any(), any())).thenReturn(k);
        when(moneyCalculator.calculate(k, l)).thenReturn(m);

        var result = service().resolve(
                user,
                EVENT,
                ABSENCE_ID,
                ordered,
                () -> null,
                () -> null,
                () -> null
        );

        assertEquals(EVENT, result.eventDate());
        assertEquals(EVENT_MONTH, result.eventMonth());
        assertEquals(ABSENCE_ID, result.requestedAbsencePeriodId());
    }

    private VacationPayOrchestrator service() {
        return new VacationPayOrchestrator(payableDays, dailyResolver, moneyCalculator);
    }

    private VacationPayableDaysFactService.Resolution readyL(int payableCalendarDays) {
        return readyL(payableCalendarDays, EVENT);
    }

    private VacationPayableDaysFactService.Resolution readyL(
            int payableCalendarDays,
            LocalDate authorityEventDate
    ) {
        var result = mock(VacationPayableDaysFactService.Resolution.class);
        when(result.eventDate()).thenReturn(authorityEventDate);
        when(result.eventMonth()).thenReturn(YearMonth.from(authorityEventDate));
        when(result.requestedAbsencePeriodId()).thenReturn(ABSENCE_ID);
        when(result.absencePeriodId()).thenReturn(ABSENCE_ID);
        when(result.vacationFrom()).thenReturn(authorityEventDate);
        when(result.ready()).thenReturn(true);
        when(result.payableCalendarDays()).thenReturn(payableCalendarDays);
        return result;
    }

    private VacationPayableDaysFactService.Resolution blockedL(String reason) {
        var result = mock(VacationPayableDaysFactService.Resolution.class);
        when(result.eventDate()).thenReturn(EVENT);
        when(result.eventMonth()).thenReturn(EVENT_MONTH);
        when(result.requestedAbsencePeriodId()).thenReturn(ABSENCE_ID);
        when(result.ready()).thenReturn(false);
        when(result.blockingReason()).thenReturn(reason);
        return result;
    }

    private VacationAverageUnifiedDailyResolver.Resolution readyK() {
        var result = mock(VacationAverageUnifiedDailyResolver.Resolution.class);
        when(result.eventDate()).thenReturn(EVENT);
        when(result.eventMonth()).thenReturn(EVENT_MONTH);
        when(result.ready()).thenReturn(true);
        return result;
    }

    private VacationAverageUnifiedDailyResolver.Resolution blockedK(String reason) {
        var result = mock(VacationAverageUnifiedDailyResolver.Resolution.class);
        when(result.eventDate()).thenReturn(EVENT);
        when(result.eventMonth()).thenReturn(EVENT_MONTH);
        when(result.ready()).thenReturn(false);
        when(result.blockingReason()).thenReturn(reason);
        return result;
    }

    private VacationPayMoneyFormula.Resolution readyM(long minor, String currency) {
        var result = mock(VacationPayMoneyFormula.Resolution.class);
        when(result.ready()).thenReturn(true);
        when(result.vacationPayMinor()).thenReturn(minor);
        when(result.currencyCode()).thenReturn(currency);
        return result;
    }

    private VacationPayMoneyFormula.Resolution blockedM(String reason) {
        var result = mock(VacationPayMoneyFormula.Resolution.class);
        when(result.ready()).thenReturn(false);
        when(result.blockingReason()).thenReturn(reason);
        return result;
    }

    private static AverageEarningsOrderedFallbackResolver.Resolution ordered(
            AverageEarningsOrderedFallbackResolver.Selection selection
    ) {
        var result = mock(AverageEarningsOrderedFallbackResolver.Resolution.class);
        when(result.eventDate()).thenReturn(EVENT);
        when(result.eventMonth()).thenReturn(EVENT_MONTH);
        when(result.ready()).thenReturn(true);
        when(result.selection()).thenReturn(selection);
        return result;
    }

    private static VacationAveragePrimaryCalculationService.Resolution reference(
            AverageEarningsReferenceWindow window,
            VacationAverageDailyEarningsFormula.ExactMoneyPerDay daily
    ) {
        var result = mock(VacationAveragePrimaryCalculationService.Resolution.class);
        when(result.eventDate()).thenReturn(EVENT);
        when(result.eventMonth()).thenReturn(EVENT_MONTH);
        when(result.referenceFrom()).thenReturn(window.referenceFrom());
        when(result.referenceTo()).thenReturn(window.referenceTo());
        when(result.ready()).thenReturn(true);
        when(result.currencyCode()).thenReturn("RUB");
        when(result.averageDaily()).thenReturn(daily);
        return result;
    }

    private static AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution paragraph7(
            long amount,
            String currency
    ) {
        var result = mock(
                AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution.class
        );
        when(result.eventDate()).thenReturn(EVENT);
        when(result.periodFrom()).thenReturn(EVENT_MONTH.atDay(1));
        when(result.cutoffExclusive()).thenReturn(EVENT);
        when(result.ready()).thenReturn(true);
        when(result.accruedWagePresent()).thenReturn(amount > 0L);
        when(result.workedTimePresent()).thenReturn(true);
        when(result.totalAccruedWageMinor()).thenReturn(amount);
        when(result.currencyCode()).thenReturn(currency);
        return result;
    }

    private static AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution paragraph8Salary(
            long salaryMinor,
            String currency
    ) {
        var result = mock(
                AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution.class
        );
        when(result.eventDate()).thenReturn(EVENT);
        when(result.eventMonth()).thenReturn(EVENT_MONTH);
        when(result.compensationBoundary()).thenReturn(EVENT_MONTH.atDay(1));
        when(result.ready()).thenReturn(true);
        when(result.establishedBasis()).thenReturn(
                AverageEarningsParagraph8TariffSalaryAuthorityService
                        .EstablishedBasis.MONTHLY_OFFICIAL_SALARY
        );
        when(result.establishedAmountMinor()).thenReturn(salaryMinor);
        when(result.currencyCode()).thenReturn(currency);
        return result;
    }

    private static VacationAverageDailyEarningsFormula.ExactMoneyPerDay exact(
            long numerator,
            long denominator
    ) {
        return new VacationAverageDailyEarningsFormula.ExactMoneyPerDay(
                BigInteger.valueOf(numerator),
                BigInteger.valueOf(denominator)
        );
    }
}
