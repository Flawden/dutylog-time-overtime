package ru.daniil.shifts.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ru.daniil.shifts.model.AppUser;

class VacationPayApplicationServiceTest {

    private static final LocalDate EVENT = LocalDate.of(2026, 9, 15);
    private static final YearMonth EVENT_MONTH = YearMonth.from(EVENT);
    private static final YearMonth DISCOVERY = YearMonth.of(2026, 8);
    private static final Long ABSENCE_ID = 77L;

    private VacationPayApplicationService.Paragraph6Resolver paragraph6Resolver;
    private VacationPayApplicationService.OrderedFallbackResolver orderedResolver;
    private VacationPayApplicationService.ReferenceCalculator referenceCalculator;
    private VacationPayApplicationService.VacationResolver vacationResolver;
    private VacationPayApplicationService.Paragraph7CalendarBasisResolver
            paragraph7CalendarBasisResolver;
    private VacationPayApplicationService.Paragraph8FormulaBasisResolver
            paragraph8FormulaBasisResolver;
    private AppUser user;
    private AverageEarningsParagraph6ReferenceResolver.Resolution p6;
    private AverageEarningsOrderedFallbackResolver.Resolution ordered;
    private VacationPayOrchestrator.Resolution vacation;

    @BeforeEach
    void setUp() {
        paragraph6Resolver = mock(VacationPayApplicationService.Paragraph6Resolver.class);
        orderedResolver = mock(VacationPayApplicationService.OrderedFallbackResolver.class);
        referenceCalculator = mock(VacationPayApplicationService.ReferenceCalculator.class);
        vacationResolver = mock(VacationPayApplicationService.VacationResolver.class);
        paragraph7CalendarBasisResolver = mock(
                VacationPayApplicationService.Paragraph7CalendarBasisResolver.class
        );
        paragraph8FormulaBasisResolver = mock(
                VacationPayApplicationService.Paragraph8FormulaBasisResolver.class
        );
        serviceHolder = null;
        user = mock(AppUser.class);
        p6 = p6(AverageEarningsParagraph6ReferenceResolver.Selection.PRIMARY);
        ordered = ordered(
                AverageEarningsOrderedFallbackResolver.Selection.PRIMARY_REFERENCE_PERIOD
        );
        vacation = vacation(true, 1234L, "RUB");
        when(paragraph6Resolver.resolve(eq(user), eq(EVENT), eq(DISCOVERY), any()))
                .thenReturn(p6);
        when(orderedResolver.resolve(eq(p6), any(), any())).thenReturn(ordered);
        when(vacationResolver.resolve(
                eq(user), eq(EVENT), eq(ABSENCE_ID), eq(ordered), any(), any(), any()
        )).thenReturn(vacation);
    }

    @Test
    void constructorRejectsNullParagraph6Resolver() {
        assertThrows(
                NullPointerException.class,
                () -> new VacationPayApplicationService(
                        null, orderedResolver, referenceCalculator, vacationResolver
                )
        );
    }

    @Test
    void constructorRejectsNullOrderedResolver() {
        assertThrows(
                NullPointerException.class,
                () -> new VacationPayApplicationService(
                        paragraph6Resolver, null, referenceCalculator, vacationResolver
                )
        );
    }

    @Test
    void constructorRejectsNullReferenceCalculator() {
        assertThrows(
                NullPointerException.class,
                () -> new VacationPayApplicationService(
                        paragraph6Resolver, orderedResolver, null, vacationResolver
                )
        );
    }

    @Test
    void constructorRejectsNullVacationResolver() {
        assertThrows(
                NullPointerException.class,
                () -> new VacationPayApplicationService(
                        paragraph6Resolver, orderedResolver, referenceCalculator, null
                )
        );
    }

    @Test
    void nullUserRejectedBeforeP6() {
        assertThrows(
                NullPointerException.class,
                () -> service().resolve(
                        null, EVENT, ABSENCE_ID, DISCOVERY, List.of(),
                        () -> null, () -> null, () -> null, () -> null
                )
        );
        verifyNoInteractions(paragraph6Resolver);
    }

    @Test
    void nullEventRejectedBeforeP6() {
        assertThrows(
                NullPointerException.class,
                () -> service().resolve(
                        user, null, ABSENCE_ID, DISCOVERY, List.of(),
                        () -> null, () -> null, () -> null, () -> null
                )
        );
        verifyNoInteractions(paragraph6Resolver);
    }

    @Test
    void nullDiscoveryRejectedBeforeP6() {
        assertThrows(
                NullPointerException.class,
                () -> service().resolve(
                        user, EVENT, ABSENCE_ID, null, List.of(),
                        () -> null, () -> null, () -> null, () -> null
                )
        );
        verifyNoInteractions(paragraph6Resolver);
    }

    @Test
    void nullZeroProofsRejectedBeforeP6() {
        assertThrows(
                NullPointerException.class,
                () -> service().resolve(
                        user, EVENT, ABSENCE_ID, DISCOVERY, null,
                        () -> null, () -> null, () -> null, () -> null
                )
        );
        verifyNoInteractions(paragraph6Resolver);
    }

    @Test
    void nullParagraph7AuthoritySupplierRejectedBeforeP6() {
        assertThrows(
                NullPointerException.class,
                () -> service().resolve(
                        user, EVENT, ABSENCE_ID, DISCOVERY, List.of(),
                        null, () -> null, () -> null, () -> null
                )
        );
        verifyNoInteractions(paragraph6Resolver);
    }

    @Test
    void nullParagraph8AuthoritySupplierRejectedBeforeP6() {
        assertThrows(
                NullPointerException.class,
                () -> service().resolve(
                        user, EVENT, ABSENCE_ID, DISCOVERY, List.of(),
                        () -> null, null, () -> null, () -> null
                )
        );
        verifyNoInteractions(paragraph6Resolver);
    }

    @Test
    void nullParagraph7CalendarSupplierRejectedBeforeP6() {
        assertThrows(
                NullPointerException.class,
                () -> service().resolve(
                        user, EVENT, ABSENCE_ID, DISCOVERY, List.of(),
                        () -> null, () -> null, null, () -> null
                )
        );
        verifyNoInteractions(paragraph6Resolver);
    }

    @Test
    void nullParagraph8FormulaSupplierRejectedBeforeP6() {
        assertThrows(
                NullPointerException.class,
                () -> service().resolve(
                        user, EVENT, ABSENCE_ID, DISCOVERY, List.of(),
                        () -> null, () -> null, () -> null, null
                )
        );
        verifyNoInteractions(paragraph6Resolver);
    }

    @Test
    void zeroProofsAreCopiedBeforeAuthoritiesUseThem() {
        List<YearMonth> mutable = new ArrayList<>();
        mutable.add(YearMonth.of(2025, 12));

        var result = resolve(mutable);

        mutable.clear();
        assertEquals(List.of(YearMonth.of(2025, 12)), result.provenNoPayrollMonths());
        verify(paragraph6Resolver).resolve(
                user, EVENT, DISCOVERY, List.of(YearMonth.of(2025, 12))
        );
    }

    @Test
    void paragraph6RunsBeforeOrderedFallback() {
        resolve(List.of());

        var order = inOrder(paragraph6Resolver, orderedResolver);
        order.verify(paragraph6Resolver).resolve(eq(user), eq(EVENT), eq(DISCOVERY), any());
        order.verify(orderedResolver).resolve(eq(p6), any(), any());
    }

    @Test
    void orderedFallbackRunsBeforeVacationOrchestrator() {
        resolve(List.of());

        var order = inOrder(orderedResolver, vacationResolver);
        order.verify(orderedResolver).resolve(eq(p6), any(), any());
        order.verify(vacationResolver).resolve(
                eq(user), eq(EVENT), eq(ABSENCE_ID), eq(ordered), any(), any(), any()
        );
    }

    @Test
    void selectedReferenceCalculationIsLazyUntilNRequestsIt() {
        resolve(List.of());

        verifyNoInteractions(referenceCalculator);
    }

    @Test
    void selectedReferenceWindowIsPassedExactlyToCalculator() {
        AverageEarningsReferenceWindow selected = AverageEarningsReferenceWindow.primary(EVENT);
        when(ordered.selectedReferenceWindow()).thenReturn(selected);
        var reference = mock(VacationAveragePrimaryCalculationService.Resolution.class);
        when(referenceCalculator.calculate(user, EVENT, selected, DISCOVERY, List.of()))
                .thenReturn(reference);
        when(vacationResolver.resolve(
                eq(user), eq(EVENT), eq(ABSENCE_ID), eq(ordered), any(), any(), any()
        )).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<VacationAveragePrimaryCalculationService.Resolution> supplier =
                    invocation.getArgument(4);
            assertSame(reference, supplier.get());
            return vacation;
        });

        resolve(List.of());

        verify(referenceCalculator).calculate(
                user, EVENT, selected, DISCOVERY, List.of()
        );
    }

    @Test
    void paragraph7CalendarSupplierIsForwardedWithoutApplicationEvaluation() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<VacationAverageUnifiedDailyResolver.Paragraph7CalendarBasis> supplier =
                () -> {
                    calls.incrementAndGet();
                    return null;
                };
        when(vacationResolver.resolve(
                eq(user), eq(EVENT), eq(ABSENCE_ID), eq(ordered), any(), eq(supplier), any()
        )).thenReturn(vacation);

        resolveWithBasisSuppliers(supplier, () -> null);

        assertEquals(0, calls.get());
    }

    @Test
    void paragraph8FormulaSupplierIsForwardedWithoutApplicationEvaluation() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<VacationAverageUnifiedDailyResolver.Paragraph8FormulaBasis> supplier =
                () -> {
                    calls.incrementAndGet();
                    return null;
                };
        when(vacationResolver.resolve(
                eq(user), eq(EVENT), eq(ABSENCE_ID), eq(ordered), any(), any(), eq(supplier)
        )).thenReturn(vacation);

        resolveWithBasisSuppliers(() -> null, supplier);

        assertEquals(0, calls.get());
    }

    @Test
    void canonicalBasisAuthoritiesStayLazyUntilVacationResolverEvaluatesSuppliers() {
        service().resolve(
                user,
                EVENT,
                ABSENCE_ID,
                DISCOVERY,
                List.of(),
                () -> null,
                () -> null
        );

        verifyNoInteractions(
                paragraph7CalendarBasisResolver,
                paragraph8FormulaBasisResolver
        );
        verify(ordered, never()).paragraph8Authority();
    }

    @Test
    void canonicalParagraph7SupplierUsesP7AuthorityOnlyWhenRequested() {
        var basis = mock(
                VacationAverageUnifiedDailyResolver.Paragraph7CalendarBasis.class
        );
        var authority = mock(
                AverageEarningsParagraph7CalendarBasisAuthorityService.Resolution.class
        );
        when(authority.ready()).thenReturn(true);
        when(authority.basis()).thenReturn(basis);
        when(paragraph7CalendarBasisResolver.resolve(user, EVENT)).thenReturn(authority);
        when(vacationResolver.resolve(
                eq(user), eq(EVENT), eq(ABSENCE_ID), eq(ordered), any(), any(), any()
        )).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<VacationAverageUnifiedDailyResolver.Paragraph7CalendarBasis> p7Basis =
                    invocation.getArgument(5);
            assertSame(basis, p7Basis.get());
            return vacation;
        });

        resolveCanonical(() -> null, () -> null);

        verify(paragraph7CalendarBasisResolver).resolve(user, EVENT);
        verifyNoInteractions(paragraph8FormulaBasisResolver);
    }

    @Test
    void canonicalParagraph7BlockedAuthorityBecomesExplicitMissingBasisForK() {
        var authority = mock(
                AverageEarningsParagraph7CalendarBasisAuthorityService.Resolution.class
        );
        when(authority.ready()).thenReturn(false);
        when(paragraph7CalendarBasisResolver.resolve(user, EVENT)).thenReturn(authority);
        when(vacationResolver.resolve(
                eq(user), eq(EVENT), eq(ABSENCE_ID), eq(ordered), any(), any(), any()
        )).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<VacationAverageUnifiedDailyResolver.Paragraph7CalendarBasis> p7Basis =
                    invocation.getArgument(5);
            assertNull(p7Basis.get());
            return vacation;
        });

        resolveCanonical(() -> null, () -> null);

        verify(paragraph7CalendarBasisResolver).resolve(user, EVENT);
        verifyNoInteractions(paragraph8FormulaBasisResolver);
    }

    @Test
    void canonicalParagraph8SupplierUsesExactSelectedJ5Authority() {
        var selectedParagraph8 = mock(
                AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution.class
        );
        when(ordered.paragraph8Authority()).thenReturn(selectedParagraph8);
        var basis = mock(
                VacationAverageUnifiedDailyResolver.Paragraph8FormulaBasis.class
        );
        var authority = mock(
                AverageEarningsParagraph8VacationFormulaBasisAuthorityService.Resolution.class
        );
        when(authority.ready()).thenReturn(true);
        when(authority.basis()).thenReturn(basis);
        when(paragraph8FormulaBasisResolver.resolve(
                user,
                EVENT,
                selectedParagraph8
        )).thenReturn(authority);
        when(vacationResolver.resolve(
                eq(user), eq(EVENT), eq(ABSENCE_ID), eq(ordered), any(), any(), any()
        )).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<VacationAverageUnifiedDailyResolver.Paragraph8FormulaBasis> p8Basis =
                    invocation.getArgument(6);
            assertSame(basis, p8Basis.get());
            return vacation;
        });

        resolveCanonical(() -> null, () -> null);

        verify(paragraph8FormulaBasisResolver).resolve(
                user,
                EVENT,
                selectedParagraph8
        );
        verifyNoInteractions(paragraph7CalendarBasisResolver);
    }

    @Test
    void canonicalParagraph8BlockedAuthorityBecomesExplicitMissingBasisForK() {
        var selectedParagraph8 = mock(
                AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution.class
        );
        when(ordered.paragraph8Authority()).thenReturn(selectedParagraph8);
        var authority = mock(
                AverageEarningsParagraph8VacationFormulaBasisAuthorityService.Resolution.class
        );
        when(authority.ready()).thenReturn(false);
        when(paragraph8FormulaBasisResolver.resolve(
                user,
                EVENT,
                selectedParagraph8
        )).thenReturn(authority);
        when(vacationResolver.resolve(
                eq(user), eq(EVENT), eq(ABSENCE_ID), eq(ordered), any(), any(), any()
        )).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<VacationAverageUnifiedDailyResolver.Paragraph8FormulaBasis> p8Basis =
                    invocation.getArgument(6);
            assertNull(p8Basis.get());
            return vacation;
        });

        resolveCanonical(() -> null, () -> null);

        verify(paragraph8FormulaBasisResolver).resolve(
                user,
                EVENT,
                selectedParagraph8
        );
        verifyNoInteractions(paragraph7CalendarBasisResolver);
    }

    @Test
    void explicitBasisSupplierSeamNeverTouchesCanonicalBasisAuthorities() {
        var p7Basis = mock(
                VacationAverageUnifiedDailyResolver.Paragraph7CalendarBasis.class
        );
        var p8Basis = mock(
                VacationAverageUnifiedDailyResolver.Paragraph8FormulaBasis.class
        );
        Supplier<VacationAverageUnifiedDailyResolver.Paragraph7CalendarBasis> p7 =
                () -> p7Basis;
        Supplier<VacationAverageUnifiedDailyResolver.Paragraph8FormulaBasis> p8 =
                () -> p8Basis;
        when(vacationResolver.resolve(
                eq(user), eq(EVENT), eq(ABSENCE_ID), eq(ordered), any(), eq(p7), eq(p8)
        )).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<VacationAverageUnifiedDailyResolver.Paragraph7CalendarBasis> p7Supplier =
                    invocation.getArgument(5);
            @SuppressWarnings("unchecked")
            Supplier<VacationAverageUnifiedDailyResolver.Paragraph8FormulaBasis> p8Supplier =
                    invocation.getArgument(6);
            assertSame(p7Basis, p7Supplier.get());
            assertSame(p8Basis, p8Supplier.get());
            return vacation;
        });

        resolveWithBasisSuppliers(p7, p8);

        verifyNoInteractions(
                paragraph7CalendarBasisResolver,
                paragraph8FormulaBasisResolver
        );
    }

    @Test
    void resultPreservesP6J5AndNProvenance() {
        var result = resolve(List.of());

        assertSame(p6, result.paragraph6Authority());
        assertSame(ordered, result.orderedFallback());
        assertSame(vacation, result.vacationPay());
    }

    @Test
    void readyResultDelegatesCanonicalMoneyFacade() {
        var result = resolve(List.of());

        assertTrue(result.ready());
        assertEquals("RUB", result.currencyCode());
        assertEquals(1234L, result.vacationPayMinor());
        assertEquals(5, result.payableCalendarDays());
        assertEquals(
                AverageEarningsOrderedFallbackResolver.Selection.PRIMARY_REFERENCE_PERIOD,
                result.selectedBasis()
        );
    }

    @Test
    void blockedResultDelegatesNBlockerWithoutPartialMoney() {
        vacation = vacation(false, null, null);
        when(vacationResolver.resolve(
                eq(user), eq(EVENT), eq(ABSENCE_ID), eq(ordered), any(), any(), any()
        )).thenReturn(vacation);

        var result = resolve(List.of());

        assertFalse(result.ready());
        assertEquals(VacationPayOrchestrator.DAILY_AUTHORITY_BLOCKED, result.blockingReason());
        assertEquals("J5_BLOCK", result.upstreamBlockingReason());
        assertNull(result.currencyCode());
        assertNull(result.vacationPayMinor());
    }

    @Test
    void realJ5PrimaryDoesNotEvaluateParagraph7OrParagraph8Authorities() {
        p6 = p6(AverageEarningsParagraph6ReferenceResolver.Selection.PRIMARY);
        useRealJ5();
        AtomicInteger p7 = new AtomicInteger();
        AtomicInteger p8 = new AtomicInteger();

        resolveAuthorities(
                () -> { p7.incrementAndGet(); return null; },
                () -> { p8.incrementAndGet(); return null; }
        );

        assertEquals(0, p7.get());
        assertEquals(0, p8.get());
    }

    @Test
    void realJ5PrecedingDoesNotEvaluateParagraph7OrParagraph8Authorities() {
        p6 = p6(AverageEarningsParagraph6ReferenceResolver.Selection.PARAGRAPH_6_PRECEDING);
        useRealJ5();
        AtomicInteger p7 = new AtomicInteger();
        AtomicInteger p8 = new AtomicInteger();

        resolveAuthorities(
                () -> { p7.incrementAndGet(); return null; },
                () -> { p8.incrementAndGet(); return null; }
        );

        assertEquals(0, p7.get());
        assertEquals(0, p8.get());
    }

    @Test
    void realJ5Paragraph7EvaluatesOnlyParagraph7Authority() {
        p6 = p6(AverageEarningsParagraph6ReferenceResolver.Selection.PARAGRAPH_6_EXHAUSTED);
        useRealJ5();
        AtomicInteger p7 = new AtomicInteger();
        AtomicInteger p8 = new AtomicInteger();

        resolveAuthorities(
                () -> {
                    p7.incrementAndGet();
                    return p7(true, true);
                },
                () -> {
                    p8.incrementAndGet();
                    return p8();
                }
        );

        assertEquals(1, p7.get());
        assertEquals(0, p8.get());
    }

    @Test
    void realJ5Paragraph8EvaluatesParagraph7ThenParagraph8ExactlyOnce() {
        p6 = p6(AverageEarningsParagraph6ReferenceResolver.Selection.PARAGRAPH_6_EXHAUSTED);
        useRealJ5();
        AtomicInteger p7 = new AtomicInteger();
        AtomicInteger p8 = new AtomicInteger();

        resolveAuthorities(
                () -> {
                    p7.incrementAndGet();
                    return p7(false, false);
                },
                () -> {
                    p8.incrementAndGet();
                    return p8();
                }
        );

        assertEquals(1, p7.get());
        assertEquals(1, p8.get());
    }

    @Test
    void realJ5BlockedP6EvaluatesNoLaterAuthority() {
        p6 = mock(AverageEarningsParagraph6ReferenceResolver.Resolution.class);
        when(p6.eventDate()).thenReturn(EVENT);
        when(p6.eventMonth()).thenReturn(EVENT_MONTH);
        when(p6.discoveryThroughMonth()).thenReturn(DISCOVERY);
        when(p6.ready()).thenReturn(false);
        when(p6.blockingReason()).thenReturn("P6_BLOCK");
        vacation = vacation(false, null, null);
        useRealJ5();
        AtomicInteger p7 = new AtomicInteger();
        AtomicInteger p8 = new AtomicInteger();

        resolveAuthorities(
                () -> { p7.incrementAndGet(); return null; },
                () -> { p8.incrementAndGet(); return null; }
        );

        assertEquals(0, p7.get());
        assertEquals(0, p8.get());
    }

    @Test
    void referenceSupplierUsesImmutableZeroProofCopy() {
        List<YearMonth> mutable = new ArrayList<>();
        mutable.add(YearMonth.of(2025, 11));
        AverageEarningsReferenceWindow selected = AverageEarningsReferenceWindow.primary(EVENT);
        when(ordered.selectedReferenceWindow()).thenReturn(selected);
        var reference = mock(VacationAveragePrimaryCalculationService.Resolution.class);
        when(referenceCalculator.calculate(
                user, EVENT, selected, DISCOVERY, List.of(YearMonth.of(2025, 11))
        )).thenReturn(reference);
        when(vacationResolver.resolve(
                eq(user), eq(EVENT), eq(ABSENCE_ID), eq(ordered), any(), any(), any()
        )).thenAnswer(invocation -> {
            mutable.clear();
            @SuppressWarnings("unchecked")
            Supplier<VacationAveragePrimaryCalculationService.Resolution> supplier =
                    invocation.getArgument(4);
            assertSame(reference, supplier.get());
            return vacation;
        });

        resolve(mutable);

        verify(referenceCalculator).calculate(
                user, EVENT, selected, DISCOVERY, List.of(YearMonth.of(2025, 11))
        );
    }

    @Test
    void absenceIdentityIsPreservedEvenWhenExplicitIdIsNull() {
        vacation = vacation(true, 0L, "RUB");
        when(vacation.requestedAbsencePeriodId()).thenReturn(null);
        when(vacationResolver.resolve(
                eq(user), eq(EVENT), eq((Long) null), eq(ordered), any(), any(), any()
        )).thenReturn(vacation);

        var result = service().resolve(
                user, EVENT, null, DISCOVERY, List.of(),
                () -> null, () -> null, () -> null, () -> null
        );

        assertNull(result.requestedAbsencePeriodId());
    }

    private VacationPayApplicationService service() {
        if (serviceHolder != null) {
            return serviceHolder;
        }
        return new VacationPayApplicationService(
                paragraph6Resolver,
                orderedResolver,
                referenceCalculator,
                vacationResolver,
                paragraph7CalendarBasisResolver,
                paragraph8FormulaBasisResolver
        );
    }

    private VacationPayApplicationService.Resolution resolve(List<YearMonth> zeroProofs) {
        return service().resolve(
                user, EVENT, ABSENCE_ID, DISCOVERY, zeroProofs,
                () -> null, () -> null, () -> null, () -> null
        );
    }

    private VacationPayApplicationService.Resolution resolveCanonical(
            Supplier<AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution> p7Authority,
            Supplier<AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution> p8Authority
    ) {
        return service().resolve(
                user,
                EVENT,
                ABSENCE_ID,
                DISCOVERY,
                List.of(),
                p7Authority,
                p8Authority
        );
    }

    private VacationPayApplicationService.Resolution resolveWithBasisSuppliers(
            Supplier<VacationAverageUnifiedDailyResolver.Paragraph7CalendarBasis> p7Basis,
            Supplier<VacationAverageUnifiedDailyResolver.Paragraph8FormulaBasis> p8Basis
    ) {
        return service().resolve(
                user, EVENT, ABSENCE_ID, DISCOVERY, List.of(),
                () -> null, () -> null, p7Basis, p8Basis
        );
    }

    private VacationPayApplicationService.Resolution resolveAuthorities(
            Supplier<AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution> p7Authority,
            Supplier<AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution> p8Authority
    ) {
        return service().resolve(
                user, EVENT, ABSENCE_ID, DISCOVERY, List.of(),
                p7Authority, p8Authority, () -> null, () -> null
        );
    }

    private void useRealJ5() {
        when(paragraph6Resolver.resolve(eq(user), eq(EVENT), eq(DISCOVERY), any()))
                .thenReturn(p6);
        var real = new VacationPayApplicationService(
                paragraph6Resolver,
                AverageEarningsOrderedFallbackResolver::resolve,
                referenceCalculator,
                vacationResolver,
                paragraph7CalendarBasisResolver,
                paragraph8FormulaBasisResolver
        );
        serviceHolder = real;
        when(vacationResolver.resolve(
                eq(user), eq(EVENT), eq(ABSENCE_ID), any(), any(), any(), any()
        )).thenReturn(vacation);
    }

    private VacationPayApplicationService serviceHolder;

    private AverageEarningsParagraph6ReferenceResolver.Resolution p6(
            AverageEarningsParagraph6ReferenceResolver.Selection selection
    ) {
        var result = mock(AverageEarningsParagraph6ReferenceResolver.Resolution.class);
        when(result.eventDate()).thenReturn(EVENT);
        when(result.eventMonth()).thenReturn(EVENT_MONTH);
        when(result.discoveryThroughMonth()).thenReturn(DISCOVERY);
        when(result.ready()).thenReturn(true);
        when(result.selection()).thenReturn(selection);
        var evidence = mock(AverageEarningsParagraph6ReferenceResolver.PeriodEvidence.class);
        AverageEarningsReferenceWindow window =
                selection == AverageEarningsParagraph6ReferenceResolver.Selection.PRIMARY
                        ? AverageEarningsReferenceWindow.primary(EVENT)
                        : AverageEarningsReferenceWindow.primary(EVENT).precedingEqual();
        when(evidence.window()).thenReturn(window);
        when(result.selectedEvidence()).thenReturn(evidence);
        return result;
    }

    private AverageEarningsOrderedFallbackResolver.Resolution ordered(
            AverageEarningsOrderedFallbackResolver.Selection selection
    ) {
        var result = mock(AverageEarningsOrderedFallbackResolver.Resolution.class);
        when(result.eventDate()).thenReturn(EVENT);
        when(result.eventMonth()).thenReturn(EVENT_MONTH);
        when(result.ready()).thenReturn(true);
        when(result.selection()).thenReturn(selection);
        when(result.selectedReferenceWindow()).thenReturn(
                AverageEarningsReferenceWindow.primary(EVENT)
        );
        return result;
    }

    private AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution p7(
            boolean wage,
            boolean work
    ) {
        var result = mock(
                AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution.class
        );
        when(result.eventDate()).thenReturn(EVENT);
        when(result.periodFrom()).thenReturn(EVENT_MONTH.atDay(1));
        when(result.cutoffExclusive()).thenReturn(EVENT);
        when(result.ready()).thenReturn(true);
        when(result.accruedWagePresent()).thenReturn(wage);
        when(result.workedTimePresent()).thenReturn(work);
        return result;
    }

    private AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution p8() {
        var result = mock(
                AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution.class
        );
        when(result.eventDate()).thenReturn(EVENT);
        when(result.eventMonth()).thenReturn(EVENT_MONTH);
        when(result.compensationBoundary()).thenReturn(EVENT_MONTH.atDay(1));
        when(result.legalRegime()).thenReturn(AverageEarningsLegalPolicy.requireRegime(EVENT));
        when(result.ready()).thenReturn(true);
        return result;
    }

    private VacationPayOrchestrator.Resolution vacation(
            boolean ready,
            Long money,
            String currency
    ) {
        var result = mock(VacationPayOrchestrator.Resolution.class);
        when(result.eventDate()).thenReturn(EVENT);
        when(result.eventMonth()).thenReturn(EVENT_MONTH);
        when(result.requestedAbsencePeriodId()).thenReturn(ABSENCE_ID);
        when(result.ready()).thenReturn(ready);
        when(result.currencyCode()).thenReturn(currency);
        when(result.vacationPayMinor()).thenReturn(money);
        when(result.payableCalendarDays()).thenReturn(ready ? 5 : 0);
        if (!ready) {
            when(result.blockingStage()).thenReturn(
                    VacationPayOrchestrator.BlockingStage.DAILY_AUTHORITY
            );
            when(result.blockingReason()).thenReturn(
                    VacationPayOrchestrator.DAILY_AUTHORITY_BLOCKED
            );
            when(result.upstreamBlockingReason()).thenReturn("J5_BLOCK");
        }
        return result;
    }
}
