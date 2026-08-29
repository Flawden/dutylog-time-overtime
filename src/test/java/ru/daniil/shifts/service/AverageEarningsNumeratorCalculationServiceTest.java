package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayrollBonusP15Nature;
import ru.daniil.shifts.model.PayrollEarningKind;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static ru.daniil.shifts.service.AverageEarningsBonusP15Policy.AmountTreatment.FACTUAL_ACCRUED_AMOUNT;
import static ru.daniil.shifts.service.AverageEarningsBonusP15Policy.Eligibility.INCLUDE;
import static ru.daniil.shifts.service.AverageEarningsBonusP15Policy.LegalRule.PP_540_P15_MONTHLY;
import static ru.daniil.shifts.service.AverageEarningsBonusP15Policy.ReferenceTimeAdjustment.NONE_REFERENCE_PERIOD_FULLY_WORKED;
import static ru.daniil.shifts.service.AverageEarningsLegalPolicy.EarningTreatment.ORDINARY_REMUNERATION;
import static ru.daniil.shifts.service.AverageEarningsLegalPolicy.EarningTreatment.PREMIUM_SPECIAL_RULE;
import static ru.daniil.shifts.service.AverageEarningsLegalPolicy.LegalBasis.PP_540_P2;
import static ru.daniil.shifts.service.AverageEarningsLegalPolicy.LegalBasis.PP_540_P2_AND_P15;

@ExtendWith(MockitoExtension.class)
class AverageEarningsNumeratorCalculationServiceTest {

    private static final LocalDate EVENT = LocalDate.of(2026, 9, 10);
    private static final YearMonth EVENT_MONTH = YearMonth.of(2026, 9);
    private static final YearMonth FROM = YearMonth.of(2025, 9);
    private static final YearMonth TO = YearMonth.of(2026, 8);
    private static final YearMonth THROUGH = YearMonth.of(2026, 10);

    @Mock
    private AverageEarningsNumeratorFactsService numeratorFacts;

    @Mock
    private AverageEarningsBonusP15CalculationPipelineService p15;

    private AppUser user;
    private AverageEarningsNumeratorCalculationService service;

    @BeforeEach
    void setUp() {
        user = mock(AppUser.class);
        service = new AverageEarningsNumeratorCalculationService(numeratorFacts, p15);
    }

    @Test
    void finalNumeratorReplacesRawPremiumBucketWithCalculatedParagraph15Money() {
        var months = fullEmploymentMonths();
        months.set(11, employed(TO,
                List.of(
                        ordinary(600_000L, null, null),
                        premium(TO, 400_000L)
                )));
        var facts = facts(months);
        when(numeratorFacts.resolve(user, EVENT)).thenReturn(facts);
        when(p15.calculate(eq(user), eq(EVENT), eq(THROUGH), anyList()))
                .thenReturn(readyP15(List.of(decision(1L, TO, 400_000L)), 360_000L, "RUB", List.of()));

        var result = service.calculate(user, EVENT, THROUGH, List.of());

        assertTrue(result.ready());
        assertEquals(600_000L, result.includedOrdinaryAmountMinor());
        assertEquals(400_000L, result.rawPremiumSpecialAmountMinor());
        assertEquals(360_000L, result.includedPremiumAmountMinor());
        assertEquals(960_000L, result.numeratorAmountMinor());
    }

    @Test
    void paragraph5ExactOrdinaryExclusionIsRemovedBeforePremiumMoneyIsAdded() {
        var months = fullEmploymentMonths();
        months.set(0, employed(FROM, List.of(
                ordinary(600_000L, LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 10)),
                ordinary(200_000L, LocalDate.of(2025, 9, 20), LocalDate.of(2025, 9, 22))
        )));
        var facts = facts(months);
        when(numeratorFacts.resolve(user, EVENT)).thenReturn(facts);
        when(p15.calculate(eq(user), eq(EVENT), eq(THROUGH), anyList()))
                .thenReturn(readyP15(List.of(), 50_000L, "RUB",
                        List.of(exclusion(LocalDate.of(2025, 9, 20), LocalDate.of(2025, 9, 22)))));

        var result = service.calculate(user, EVENT, THROUGH, List.of());

        assertTrue(result.ready());
        assertEquals(600_000L, result.includedOrdinaryAmountMinor());
        assertEquals(200_000L, result.excludedParagraph5OrdinaryAmountMinor());
        assertEquals(650_000L, result.numeratorAmountMinor());
    }

    @Test
    void explicitPreviousReferenceWindowFlowsFromNumeratorFactsIntoParagraph15AtSameEventDate() {
        AverageEarningsReferenceWindow window = new AverageEarningsReferenceWindow(
                EVENT_MONTH,
                YearMonth.of(2024, 9),
                YearMonth.of(2025, 8)
        );
        List<AverageEarningsNumeratorFactsService.MonthFact> months = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            months.add(AverageEarningsNumeratorFactsService.MonthFact.notEmployed(
                    window.referenceFrom().plusMonths(i)
            ));
        }
        var facts = facts(months, window);
        when(numeratorFacts.resolve(user, EVENT, window)).thenReturn(facts);

        var blocked = mock(AverageEarningsBonusP15CalculationPipelineService.Resolution.class);
        when(blocked.ready()).thenReturn(false);
        when(blocked.blockingReason()).thenReturn("P15_ALT_BLOCKED");
        when(p15.calculate(eq(user), eq(EVENT), eq(window), eq(THROUGH), anyList()))
                .thenReturn(blocked);

        var result = service.calculate(user, EVENT, window, THROUGH, List.of());

        assertFalse(result.ready());
        assertEquals(window.referenceFrom(), result.referenceFrom());
        assertEquals(window.referenceTo(), result.referenceTo());
        assertEquals("P15_ALT_BLOCKED", result.blockingReason());
        verify(numeratorFacts).resolve(user, EVENT, window);
        verify(p15).calculate(eq(user), eq(EVENT), eq(window), eq(THROUGH), anyList());
    }

    @Test
    void numeratorFactsBlockerStopsBeforeParagraph15Pipeline() {
        when(numeratorFacts.resolve(user, EVENT)).thenReturn(
                AverageEarningsNumeratorFactsService.Resolution.blocked(
                        EVENT, EVENT_MONTH, FROM, TO,
                        AverageEarningsLegalPolicy.LegalRegime.RU_PP_540_2025,
                        "FACT_BLOCKED", YearMonth.of(2026, 1)
                )
        );

        var result = service.calculate(user, EVENT, THROUGH, List.of());

        assertFalse(result.ready());
        assertEquals(AverageEarningsNumeratorCalculationService.BlockingStage.NUMERATOR_FACTS,
                result.blockingStage());
        assertEquals("FACT_BLOCKED", result.blockingReason());
        verifyNoInteractions(p15);
    }

    @Test
    void nullNumeratorFactsAuthorityResultFailsFast() {
        when(numeratorFacts.resolve(user, EVENT)).thenReturn(null);
        assertThrows(NullPointerException.class,
                () -> service.calculate(user, EVENT, THROUGH, List.of()));
        verifyNoInteractions(p15);
    }

    @Test
    void numeratorFactsWindowMismatchBlocksWithoutCallingParagraph15() {
        var wrong = mock(AverageEarningsNumeratorFactsService.Resolution.class);
        when(wrong.ready()).thenReturn(true);
        when(wrong.eventDate()).thenReturn(EVENT);
        when(wrong.eventMonth()).thenReturn(EVENT_MONTH);
        when(wrong.referenceFrom()).thenReturn(FROM.minusMonths(1));
        when(numeratorFacts.resolve(user, EVENT)).thenReturn(wrong);

        var result = service.calculate(user, EVENT, THROUGH, List.of());

        assertFalse(result.ready());
        assertEquals(AverageEarningsNumeratorCalculationService.AUTHORITY_WINDOW_MISMATCH,
                result.blockingReason());
        verifyNoInteractions(p15);
    }

    @Test
    void explicitNoPayrollProofCannotContradictEmploymentFact() {
        var facts = facts(fullEmploymentMonths());
        when(numeratorFacts.resolve(user, EVENT)).thenReturn(facts);

        var result = service.calculate(user, EVENT, THROUGH, List.of(FROM));

        assertFalse(result.ready());
        assertEquals(AverageEarningsNumeratorCalculationService.BlockingStage.NO_PAYROLL_AUTHORITY,
                result.blockingStage());
        assertEquals(AverageEarningsNumeratorCalculationService.NO_PAYROLL_EMPLOYMENT_CONTRADICTION,
                result.blockingReason());
        assertEquals(FROM, result.blockingPeriod());
        verifyNoInteractions(p15);
    }

    @Test
    void wholePreEmploymentReferenceMonthAutomaticallyBecomesNoPayrollAuthorityForG() {
        var months = fullEmploymentMonths();
        months.set(0, AverageEarningsNumeratorFactsService.MonthFact.notEmployed(FROM));
        var facts = facts(months);
        when(numeratorFacts.resolve(user, EVENT)).thenReturn(facts);
        when(p15.calculate(eq(user), eq(EVENT), eq(THROUGH), anyList()))
                .thenReturn(readyP15(List.of(), 0L, "RUB", List.of()));

        service.calculate(user, EVENT, THROUGH, List.of());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<YearMonth>> captor = ArgumentCaptor.forClass(List.class);
        verify(p15).calculate(eq(user), eq(EVENT), eq(THROUGH), captor.capture());
        assertEquals(List.of(FROM), captor.getValue());
    }

    @Test
    void explicitLaterNoPayrollProofIsPreservedTogetherWithEmploymentDerivedReferenceZeroMonth() {
        var months = fullEmploymentMonths();
        months.set(1, AverageEarningsNumeratorFactsService.MonthFact.notEmployed(FROM.plusMonths(1)));
        var facts = facts(months);
        when(numeratorFacts.resolve(user, EVENT)).thenReturn(facts);
        when(p15.calculate(eq(user), eq(EVENT), eq(THROUGH), anyList()))
                .thenReturn(readyP15(List.of(), 0L, "RUB", List.of()));

        service.calculate(user, EVENT, THROUGH, List.of(YearMonth.of(2026, 10)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<YearMonth>> captor = ArgumentCaptor.forClass(List.class);
        verify(p15).calculate(eq(user), eq(EVENT), eq(THROUGH), captor.capture());
        assertEquals(List.of(FROM.plusMonths(1), YearMonth.of(2026, 10)), captor.getValue());
    }

    @Test
    void duplicateNoPayrollProofsAreCanonicalizedBeforeCallingG() {
        var months = fullEmploymentMonths();
        months.set(0, AverageEarningsNumeratorFactsService.MonthFact.notEmployed(FROM));
        when(numeratorFacts.resolve(user, EVENT)).thenReturn(facts(months));
        when(p15.calculate(eq(user), eq(EVENT), eq(THROUGH), anyList()))
                .thenReturn(readyP15(List.of(), 0L, "RUB", List.of()));

        service.calculate(user, EVENT, THROUGH, List.of(FROM, FROM));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<YearMonth>> captor = ArgumentCaptor.forClass(List.class);
        verify(p15).calculate(eq(user), eq(EVENT), eq(THROUGH), captor.capture());
        assertEquals(List.of(FROM), captor.getValue());
    }

    @Test
    void noPayrollProofOutsideDiscoveryWindowIsRejectedInsteadOfSilentlyIgnored() {
        when(numeratorFacts.resolve(user, EVENT)).thenReturn(facts(fullEmploymentMonths()));
        assertThrows(IllegalArgumentException.class,
                () -> service.calculate(user, EVENT, THROUGH, List.of(THROUGH.plusMonths(1))));
        verifyNoInteractions(p15);
    }

    @Test
    void paragraph15BlockerPropagatesWithoutPartialNumeratorMoney() {
        when(numeratorFacts.resolve(user, EVENT)).thenReturn(facts(fullEmploymentMonths()));
        var blocked = mock(AverageEarningsBonusP15CalculationPipelineService.Resolution.class);
        when(blocked.ready()).thenReturn(false);
        when(blocked.blockingReason()).thenReturn("P15_BLOCKED");
        when(blocked.blockingPeriod()).thenReturn(YearMonth.of(2026, 2));
        when(p15.calculate(eq(user), eq(EVENT), eq(THROUGH), anyList())).thenReturn(blocked);

        var result = service.calculate(user, EVENT, THROUGH, List.of());

        assertFalse(result.ready());
        assertEquals(AverageEarningsNumeratorCalculationService.BlockingStage.P15_PIPELINE,
                result.blockingStage());
        assertEquals("P15_BLOCKED", result.blockingReason());
        assertEquals(0L, result.numeratorAmountMinor());
    }

    @Test
    void nullParagraph15PipelineResultFailsFast() {
        when(numeratorFacts.resolve(user, EVENT)).thenReturn(facts(fullEmploymentMonths()));
        when(p15.calculate(eq(user), eq(EVENT), eq(THROUGH), anyList())).thenReturn(null);
        assertThrows(NullPointerException.class,
                () -> service.calculate(user, EVENT, THROUGH, List.of()));
    }

    @Test
    void paragraph15WindowMismatchBlocksBeforeMoneyAssembly() {
        when(numeratorFacts.resolve(user, EVENT)).thenReturn(facts(fullEmploymentMonths()));
        var premium = mock(AverageEarningsBonusP15CalculationPipelineService.Resolution.class);
        when(premium.ready()).thenReturn(true);
        when(premium.eventDate()).thenReturn(EVENT);
        when(premium.eventMonth()).thenReturn(EVENT_MONTH);
        when(premium.referenceFrom()).thenReturn(FROM);
        when(premium.referenceTo()).thenReturn(TO.minusMonths(1));
        when(p15.calculate(eq(user), eq(EVENT), eq(THROUGH), anyList())).thenReturn(premium);

        var result = service.calculate(user, EVENT, THROUGH, List.of());

        assertFalse(result.ready());
        assertEquals(AverageEarningsNumeratorCalculationService.AUTHORITY_WINDOW_MISMATCH,
                result.blockingReason());
    }

    @Test
    void referencePremiumSemanticAndP15AuthorityMismatchBlocksFirstAccrualMonth() {
        var months = fullEmploymentMonths();
        months.set(4, employed(FROM.plusMonths(4), List.of(premium(FROM.plusMonths(4), 100_000L))));
        when(numeratorFacts.resolve(user, EVENT)).thenReturn(facts(months));
        when(p15.calculate(eq(user), eq(EVENT), eq(THROUGH), anyList()))
                .thenReturn(readyP15(List.of(decision(1L, FROM.plusMonths(4), 90_000L)),
                        90_000L, "RUB", List.of()));

        var result = service.calculate(user, EVENT, THROUGH, List.of());

        assertFalse(result.ready());
        assertEquals(AverageEarningsNumeratorCalculationService.BlockingStage.P15_RECONCILIATION,
                result.blockingStage());
        assertEquals(AverageEarningsNumeratorCalculationService.RAW_PREMIUM_RECONCILIATION_MISMATCH,
                result.blockingReason());
        assertEquals(FROM.plusMonths(4), result.blockingPeriod());
    }

    @Test
    void multipleP15FactsInSameAccrualMonthReconcileAgainstSemanticPremiumTotal() {
        var months = fullEmploymentMonths();
        months.set(2, employed(FROM.plusMonths(2), List.of(
                premium(FROM.plusMonths(2), 40_000L),
                premium(FROM.plusMonths(2), 60_000L)
        )));
        when(numeratorFacts.resolve(user, EVENT)).thenReturn(facts(months));
        when(p15.calculate(eq(user), eq(EVENT), eq(THROUGH), anyList()))
                .thenReturn(readyP15(List.of(
                        decision(1L, FROM.plusMonths(2), 40_000L),
                        decision(2L, FROM.plusMonths(2), 60_000L)
                ), 100_000L, "RUB", List.of()));

        var result = service.calculate(user, EVENT, THROUGH, List.of());

        assertTrue(result.ready());
        assertEquals(100_000L, result.includedPremiumAmountMinor());
    }

    @Test
    void laterDiscoveredAwardMayIncreaseP15MoneyWithoutBeingMistakenForReferenceRawPremiumMismatch() {
        when(numeratorFacts.resolve(user, EVENT)).thenReturn(facts(fullEmploymentMonths()));
        when(p15.calculate(eq(user), eq(EVENT), eq(THROUGH), anyList()))
                .thenReturn(readyP15(List.of(decision(99L, YearMonth.of(2026, 10), 300_000L)),
                        300_000L, "RUB", List.of()));

        var result = service.calculate(user, EVENT, THROUGH, List.of());

        assertTrue(result.ready());
        assertEquals(0L, result.rawPremiumSpecialAmountMinor());
        assertEquals(300_000L, result.includedPremiumAmountMinor());
        assertEquals(300_000L, result.numeratorAmountMinor());
    }

    @Test
    void paragraph5MoneyBlockerPropagatesWithoutPartialFinalNumerator() {
        var months = fullEmploymentMonths();
        months.set(0, employed(FROM, List.of(ordinary(100_000L, null, null))));
        when(numeratorFacts.resolve(user, EVENT)).thenReturn(facts(months));
        when(p15.calculate(eq(user), eq(EVENT), eq(THROUGH), anyList()))
                .thenReturn(readyP15(List.of(), 0L, "RUB",
                        List.of(exclusion(LocalDate.of(2025, 9, 5), LocalDate.of(2025, 9, 6)))));

        var result = service.calculate(user, EVENT, THROUGH, List.of());

        assertFalse(result.ready());
        assertEquals(AverageEarningsNumeratorCalculationService.BlockingStage.PARAGRAPH_5_MONEY,
                result.blockingStage());
        assertEquals(AverageEarningsParagraph5MoneyPolicy.TIME_AUTHORITY_MISSING,
                result.blockingReason());
        assertEquals(0L, result.numeratorAmountMinor());
    }

    @Test
    void numeratorAndP15CurrenciesMustAgreeWhenBothAuthoritiesExist() {
        when(numeratorFacts.resolve(user, EVENT)).thenReturn(facts(fullEmploymentMonths()));
        when(p15.calculate(eq(user), eq(EVENT), eq(THROUGH), anyList()))
                .thenReturn(readyP15(List.of(), 0L, "USD", List.of()));

        var result = service.calculate(user, EVENT, THROUGH, List.of());

        assertFalse(result.ready());
        assertEquals(AverageEarningsNumeratorCalculationService.CURRENCY_MISMATCH,
                result.blockingReason());
    }

    @Test
    void numeratorCurrencyRemainsAuthorityWhenP15HasNoCurrencyAndNoPremiumMoney() {
        when(numeratorFacts.resolve(user, EVENT)).thenReturn(facts(fullEmploymentMonths()));
        when(p15.calculate(eq(user), eq(EVENT), eq(THROUGH), anyList()))
                .thenReturn(readyP15(List.of(), 0L, null, List.of()));

        var result = service.calculate(user, EVENT, THROUGH, List.of());

        assertTrue(result.ready());
        assertEquals("RUB", result.currencyCode());
    }

    @Test
    void p15CurrencyCanSupplyFinalCurrencyWhenReferencePeriodIsEntirelyPreEmployment() {
        var months = allNotEmployedMonths();
        when(numeratorFacts.resolve(user, EVENT)).thenReturn(facts(months));
        when(p15.calculate(eq(user), eq(EVENT), eq(THROUGH), anyList()))
                .thenReturn(readyP15(List.of(decision(77L, YearMonth.of(2026, 10), 90_000L)),
                        90_000L, "RUB", List.of()));

        var result = service.calculate(user, EVENT, THROUGH, List.of());

        assertTrue(result.ready());
        assertEquals("RUB", result.currencyCode());
        assertEquals(90_000L, result.numeratorAmountMinor());
    }

    @Test
    void positiveFinalMoneyWithoutAnyCurrencyAuthorityBlocks() {
        var months = allNotEmployedMonths();
        when(numeratorFacts.resolve(user, EVENT)).thenReturn(facts(months));
        when(p15.calculate(eq(user), eq(EVENT), eq(THROUGH), anyList()))
                .thenReturn(readyP15(List.of(decision(77L, YearMonth.of(2026, 10), 90_000L)),
                        90_000L, null, List.of()));

        var result = service.calculate(user, EVENT, THROUGH, List.of());

        assertFalse(result.ready());
        assertEquals(AverageEarningsNumeratorCalculationService.CURRENCY_MISSING,
                result.blockingReason());
    }

    @Test
    void zeroMoneyWithNoEmploymentAndNoP15CurrencyRemainsValidZeroNumerator() {
        var months = allNotEmployedMonths();
        when(numeratorFacts.resolve(user, EVENT)).thenReturn(facts(months));
        when(p15.calculate(eq(user), eq(EVENT), eq(THROUGH), anyList()))
                .thenReturn(readyP15(List.of(), 0L, null, List.of()));

        var result = service.calculate(user, EVENT, THROUGH, List.of());

        assertTrue(result.ready());
        assertNull(result.currencyCode());
        assertEquals(0L, result.numeratorAmountMinor());
    }

    @Test
    void excludedPreservedAverageBucketIsReportedButNeverAddedToNumerator() {
        var months = fullEmploymentMonths();
        months.set(0, AverageEarningsNumeratorFactsService.MonthFact.employed(
                FROM,
                coverage(FROM),
                1,
                "RUB",
                List.of(new AverageEarningsNumeratorFactsService.EarningFact(
                        PayrollEarningKind.VACATION_PAY,
                        PayrollEarningKind.VACATION_PAY.phase(),
                        75_000L,
                        null, null, null, null, null,
                        AverageEarningsLegalPolicy.EarningTreatment.EXCLUDE_PRESERVED_AVERAGE,
                        AverageEarningsLegalPolicy.LegalBasis.PP_540_P5_A
                )),
                0L, 0L, 75_000L
        ));
        when(numeratorFacts.resolve(user, EVENT)).thenReturn(facts(months));
        when(p15.calculate(eq(user), eq(EVENT), eq(THROUGH), anyList()))
                .thenReturn(readyP15(List.of(), 0L, "RUB", List.of()));

        var result = service.calculate(user, EVENT, THROUGH, List.of());

        assertTrue(result.ready());
        assertEquals(75_000L, result.excludedPreservedAverageAmountMinor());
        assertEquals(0L, result.numeratorAmountMinor());
    }

    @Test
    void discoveryThroughMonthCannotEndBeforeCanonicalReferencePeriod() {
        assertThrows(IllegalArgumentException.class,
                () -> service.calculate(user, EVENT, TO.minusMonths(1), List.of()));
        verifyNoInteractions(numeratorFacts, p15);
    }

    private AverageEarningsNumeratorFactsService.Resolution facts(
            List<AverageEarningsNumeratorFactsService.MonthFact> months
    ) {
        return facts(
                months,
                AverageEarningsReferenceWindow.primary(EVENT)
        );
    }

    private AverageEarningsNumeratorFactsService.Resolution facts(
            List<AverageEarningsNumeratorFactsService.MonthFact> months,
            AverageEarningsReferenceWindow window
    ) {
        boolean employed = months.stream().anyMatch(AverageEarningsNumeratorFactsService.MonthFact::employed);
        long ordinary = months.stream().mapToLong(AverageEarningsNumeratorFactsService.MonthFact::ordinaryCandidateAmountMinor).sum();
        long premium = months.stream().mapToLong(AverageEarningsNumeratorFactsService.MonthFact::premiumSpecialAmountMinor).sum();
        long excluded = months.stream().mapToLong(AverageEarningsNumeratorFactsService.MonthFact::excludedAmountMinor).sum();
        return AverageEarningsNumeratorFactsService.Resolution.ready(
                EVENT,
                EVENT_MONTH,
                window.referenceFrom(),
                window.referenceTo(),
                AverageEarningsLegalPolicy.LegalRegime.RU_PP_540_2025,
                employed ? "RUB" : null,
                months,
                ordinary,
                premium,
                excluded
        );
    }

    private List<AverageEarningsNumeratorFactsService.MonthFact> fullEmploymentMonths() {
        List<AverageEarningsNumeratorFactsService.MonthFact> result = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            YearMonth month = FROM.plusMonths(i);
            result.add(employed(month, List.of()));
        }
        return result;
    }

    private List<AverageEarningsNumeratorFactsService.MonthFact> allNotEmployedMonths() {
        List<AverageEarningsNumeratorFactsService.MonthFact> result = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            result.add(AverageEarningsNumeratorFactsService.MonthFact.notEmployed(FROM.plusMonths(i)));
        }
        return result;
    }

    private AverageEarningsNumeratorFactsService.MonthFact employed(
            YearMonth month,
            List<AverageEarningsNumeratorFactsService.EarningFact> earnings
    ) {
        long ordinary = earnings.stream().filter(e -> e.treatment() == ORDINARY_REMUNERATION)
                .mapToLong(AverageEarningsNumeratorFactsService.EarningFact::amountMinor).sum();
        long premium = earnings.stream().filter(e -> e.treatment() == PREMIUM_SPECIAL_RULE)
                .mapToLong(AverageEarningsNumeratorFactsService.EarningFact::amountMinor).sum();
        long excluded = earnings.stream().filter(e -> e.treatment() == AverageEarningsLegalPolicy.EarningTreatment.EXCLUDE_PRESERVED_AVERAGE)
                .mapToLong(AverageEarningsNumeratorFactsService.EarningFact::amountMinor).sum();
        return AverageEarningsNumeratorFactsService.MonthFact.employed(
                month,
                coverage(month),
                1,
                "RUB",
                earnings,
                ordinary,
                premium,
                excluded
        );
    }

    private List<AverageEarningsNumeratorFactsService.EmploymentCoverageFact> coverage(YearMonth month) {
        return List.of(new AverageEarningsNumeratorFactsService.EmploymentCoverageFact(
                1L,
                FROM.atDay(1),
                null,
                month.atDay(1),
                month.atEndOfMonth()
        ));
    }

    private AverageEarningsNumeratorFactsService.EarningFact ordinary(
            long amount,
            LocalDate from,
            LocalDate to
    ) {
        return new AverageEarningsNumeratorFactsService.EarningFact(
                PayrollEarningKind.BASE_PAY,
                PayrollEarningKind.BASE_PAY.phase(),
                amount,
                null,
                from,
                to,
                null,
                null,
                ORDINARY_REMUNERATION,
                PP_540_P2
        );
    }

    private AverageEarningsNumeratorFactsService.EarningFact premium(
            YearMonth month,
            long amount
    ) {
        return new AverageEarningsNumeratorFactsService.EarningFact(
                PayrollEarningKind.MONTHLY_BONUS,
                PayrollEarningKind.MONTHLY_BONUS.phase(),
                amount,
                null,
                month.atDay(1),
                month.atEndOfMonth(),
                null,
                null,
                PREMIUM_SPECIAL_RULE,
                PP_540_P2_AND_P15
        );
    }

    private AverageEarningsBonusP15CalculationPipelineService.Resolution readyP15(
            List<AverageEarningsBonusP15Policy.Decision> decisions,
            long includedPremium,
            String currency,
            List<AverageEarningsBonusP15ReferenceCompletenessService.Paragraph5Exclusion> exclusions
    ) {
        boolean paragraph5Present = !exclusions.isEmpty();
        var completeness = AverageEarningsBonusP15ReferenceCompletenessService.Resolution.ready(
                EVENT,
                EVENT_MONTH,
                FROM,
                TO,
                ru.daniil.shifts.model.WorkTimeAccountingMode.SUMMARIZED,
                new AverageEarningsBonusP15Formula.ReferenceWorkedTimeFact(
                        AverageEarningsBonusP15Formula.WorkMeasureUnit.WORKING_MINUTES,
                        1_000L,
                        1_000L
                ),
                true,
                paragraph5Present,
                List.of(),
                true,
                !paragraph5Present,
                exclusions
        );

        var policy = AverageEarningsBonusP15Policy.Resolution.ready(decisions);
        var calculation = AverageEarningsBonusP15Formula.Calculation.ready(List.of(), includedPremium);

        return AverageEarningsBonusP15CalculationPipelineService.Resolution.ready(
                EVENT,
                EVENT_MONTH,
                FROM,
                TO,
                THROUGH,
                currency,
                completeness,
                policy,
                calculation
        );
    }

    private AverageEarningsBonusP15Policy.Decision decision(
            long id,
            YearMonth accrual,
            long amount
    ) {
        return new AverageEarningsBonusP15Policy.Decision(
                id,
                PayrollBonusP15Nature.MONTHLY,
                "indicator-" + id,
                accrual,
                amount,
                accrual.atDay(1),
                accrual.atEndOfMonth(),
                null,
                PP_540_P15_MONTHLY,
                INCLUDE,
                FACTUAL_ACCRUED_AMOUNT,
                NONE_REFERENCE_PERIOD_FULLY_WORKED
        );
    }

    private AverageEarningsBonusP15ReferenceCompletenessService.Paragraph5Exclusion exclusion(
            LocalDate from,
            LocalDate to
    ) {
        return new AverageEarningsBonusP15ReferenceCompletenessService.Paragraph5Exclusion(
                Math.abs(from.toEpochDay()) + 1,
                "VACATION",
                AverageEarningsLegalPolicy.AbsenceTreatment.EXCLUDE_PRESERVED_AVERAGE,
                AverageEarningsLegalPolicy.LegalBasis.PP_540_P5_A,
                from,
                to,
                null
        );
    }
}
