package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayrollBonusP15Nature;
import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.service.AverageEarningsBonusP15HistoricalFactDiscoveryService.DiscoveredBonusFact;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.AccrualOrigin;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.Resolution;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventBonusP15FactService.BonusP15Fact;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventSemanticWageFactService.SemanticWageFact;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventSemanticWageFactService.SourceAuthority;
import ru.daniil.shifts.service.PayrollBonusAverageEarningsFactService.AverageFact;
import ru.daniil.shifts.service.PayrollBonusP15NatureFactService.NatureFact;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AverageEarningsParagraph7PreEventBonusAccrualAuthorityServiceTest {
    private static final LocalDate EVENT = LocalDate.of(2026, 8, 20);
    private static final LocalDate FROM = LocalDate.of(2026, 8, 1);
    private static final YearMonth EVENT_MONTH = YearMonth.of(2026, 8);
    private static final YearMonth REF_FROM = YearMonth.of(2025, 8);
    private static final YearMonth REF_TO = YearMonth.of(2026, 7);
    private static final YearMonth THROUGH = YearMonth.of(2026, 10);
    private static final List<YearMonth> ZERO_PROOFS = List.of();

    private final AverageEarningsBonusP15HistoricalFactDiscoveryService historical =
            mock(AverageEarningsBonusP15HistoricalFactDiscoveryService.class);
    private final AverageEarningsParagraph7PreEventBonusAccrualAuthorityService service =
            new AverageEarningsParagraph7PreEventBonusAccrualAuthorityService(historical);
    private final AppUser user = mock(AppUser.class);

    @Test
    void constructorRequiresHistoricalDiscoveryAuthority() {
        assertThrows(
                NullPointerException.class,
                () -> new AverageEarningsParagraph7PreEventBonusAccrualAuthorityService(null)
        );
    }

    @Test
    void nullUserRejectedBeforeHistoricalDiscovery() {
        assertThrows(
                NullPointerException.class,
                () -> service.resolve(null, preEvent(List.of()), THROUGH, ZERO_PROOFS)
        );
        verifyNoInteractions(historical);
    }

    @Test
    void nullPreEventAuthorityRejected() {
        assertThrows(
                NullPointerException.class,
                () -> service.resolve(user, null, THROUGH, ZERO_PROOFS)
        );
        verifyNoInteractions(historical);
    }

    @Test
    void nullDiscoveryThroughRejected() {
        assertThrows(
                NullPointerException.class,
                () -> service.resolve(user, preEvent(List.of()), null, ZERO_PROOFS)
        );
        verifyNoInteractions(historical);
    }

    @Test
    void nullZeroProofsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> service.resolve(user, preEvent(List.of()), THROUGH, null)
        );
        verifyNoInteractions(historical);
    }

    @Test
    void blockedPreEventAuthorityCannotReachHistoricalDiscovery() {
        AverageEarningsParagraph7PreEventBonusP15FactService.Resolution blocked =
                mock(AverageEarningsParagraph7PreEventBonusP15FactService.Resolution.class);
        doReturn(false).when(blocked).ready();

        assertThrows(
                IllegalArgumentException.class,
                () -> service.resolve(user, blocked, THROUGH, ZERO_PROOFS)
        );
        verifyNoInteractions(historical);
    }

    @Test
    void preEventWindowMismatchBlocksBeforeHistoricalDiscovery() {
        AverageEarningsParagraph7PreEventBonusP15FactService.Resolution wrong =
                mock(AverageEarningsParagraph7PreEventBonusP15FactService.Resolution.class);
        doReturn(true).when(wrong).ready();
        doReturn(EVENT).when(wrong).eventDate();
        doReturn(FROM.plusDays(1)).when(wrong).periodFrom();
        doReturn(EVENT).when(wrong).cutoffExclusive();

        Resolution result = service.resolve(user, wrong, THROUGH, ZERO_PROOFS);
        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.AUTHORITY_WINDOW_MISMATCH,
                result.blockingReason()
        );
        assertTrue(result.bonusFacts().isEmpty());
        verifyNoInteractions(historical);
    }

    @Test
    void nullHistoricalDiscoveryResultIsStructuralFailure() {
        doReturn(null).when(historical).resolve(user, EVENT, THROUGH, ZERO_PROOFS);

        assertThrows(
                NullPointerException.class,
                () -> service.resolve(user, preEvent(List.of()), THROUGH, ZERO_PROOFS)
        );
    }

    @Test
    void historicalWindowMismatchBlocksWithoutExposingFacts() {
        YearMonth precedingFrom = REF_FROM.minusMonths(12);
        YearMonth precedingTo = REF_TO.minusMonths(12);
        var mismatched = new AverageEarningsBonusP15HistoricalFactDiscoveryService.Resolution(
                true,
                EVENT,
                EVENT_MONTH,
                precedingFrom,
                precedingTo,
                THROUGH,
                null,
                null,
                null,
                List.of()
        );
        doReturn(mismatched).when(historical).resolve(user, EVENT, THROUGH, ZERO_PROOFS);

        Resolution result = service.resolve(user, preEvent(List.of()), THROUGH, ZERO_PROOFS);
        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.HISTORICAL_WINDOW_MISMATCH,
                result.blockingReason()
        );
        assertTrue(result.bonusFacts().isEmpty());
    }

    @Test
    void historicalBlockerPropagatesAtExactPayrollMonth() {
        YearMonth blockerMonth = YearMonth.of(2026, 3);
        var blocked = AverageEarningsBonusP15HistoricalFactDiscoveryService.Resolution.blocked(
                EVENT,
                EVENT_MONTH,
                REF_FROM,
                REF_TO,
                THROUGH,
                "HISTORICAL_P15_PAYROLL_SNAPSHOT_MISSING",
                blockerMonth
        );
        doReturn(blocked).when(historical).resolve(user, EVENT, THROUGH, ZERO_PROOFS);

        Resolution result = service.resolve(user, preEvent(List.of()), THROUGH, ZERO_PROOFS);
        assertFalse(result.ready());
        assertEquals("HISTORICAL_P15_PAYROLL_SNAPSHOT_MISSING", result.blockingReason());
        assertEquals(blockerMonth, result.blockingPeriod());
        assertTrue(result.bonusFacts().isEmpty());
    }

    @Test
    void noBonusesAndNoAnnualDiscoveryAreReadyEmpty() {
        stubHistorical(List.of());

        Resolution result = service.resolve(user, preEvent(List.of()), THROUGH, ZERO_PROOFS);
        assertTrue(result.ready());
        assertTrue(result.bonusFacts().isEmpty());
    }

    @Test
    void monthlyPreEventBonusGetsSnapshotMonthAsAccrualAuthority() {
        BonusP15Fact direct = monthlyDirect(201L, 101L, 11L, 21L, 10_000L);
        DiscoveredBonusFact frozen = matchingHistorical(direct, EVENT_MONTH, 2);
        stubHistorical(List.of(frozen));

        Resolution result = service.resolve(user, preEvent(List.of(direct)), THROUGH, ZERO_PROOFS);
        assertTrue(result.ready());
        assertEquals(1, result.bonusFacts().size());
        var fact = result.bonusFacts().get(0);
        assertEquals(AccrualOrigin.PRE_EVENT_SOURCE, fact.origin());
        assertEquals(EVENT_MONTH, fact.accrualMonth());
        assertEquals(2, fact.snapshotRevision());
    }

    @Test
    void workPeriodPreEventBonusGetsExplicitAccrualAuthority() {
        BonusP15Fact direct = workPeriodDirect(202L, 102L, 12L, 22L, 30_000L);
        stubHistorical(List.of(matchingHistorical(direct, EVENT_MONTH, 1)));

        var fact = service.resolve(user, preEvent(List.of(direct)), THROUGH, ZERO_PROOFS)
                .bonusFacts().get(0);
        assertEquals(PayrollBonusP15Nature.WORK_PERIOD, fact.p15Nature());
        assertEquals(EVENT_MONTH, fact.accrualMonth());
    }

    @Test
    void directAnnualBonusKeepsPreEventOriginInsteadOfDuplicatingHistoricalDiscovery() {
        BonusP15Fact direct = annualDirect(203L, 103L, 13L, 23L, 40_000L);
        stubHistorical(List.of(matchingHistorical(direct, EVENT_MONTH, 1)));

        Resolution result = service.resolve(user, preEvent(List.of(direct)), THROUGH, ZERO_PROOFS);
        assertEquals(1, result.bonusFacts().size());
        assertEquals(AccrualOrigin.PRE_EVENT_SOURCE, result.bonusFacts().get(0).origin());
    }

    @Test
    void directServiceLengthBonusKeepsPreEventOrigin() {
        BonusP15Fact direct = serviceLengthDirect(204L, 104L, 14L, 24L, 45_000L);
        stubHistorical(List.of(matchingHistorical(direct, EVENT_MONTH, 1)));

        var fact = service.resolve(user, preEvent(List.of(direct)), THROUGH, ZERO_PROOFS)
                .bonusFacts().get(0);
        assertEquals(AccrualOrigin.PRE_EVENT_SOURCE, fact.origin());
        assertEquals(PayrollBonusP15Nature.SERVICE_LENGTH, fact.p15Nature());
    }

    @Test
    void missingDirectAccrualAuthorityBlocksWithoutPartialFacts() {
        BonusP15Fact direct = monthlyDirect(201L, 101L, 11L, 21L, 10_000L);
        stubHistorical(List.of());

        Resolution result = service.resolve(user, preEvent(List.of(direct)), THROUGH, ZERO_PROOFS);
        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.DIRECT_ACCRUAL_AUTHORITY_MISSING,
                result.blockingReason()
        );
        assertTrue(result.bonusFacts().isEmpty());
    }

    @Test
    void componentIdentityMismatchBlocks() {
        BonusP15Fact direct = monthlyDirect(201L, 101L, 11L, 21L, 10_000L);
        DiscoveredBonusFact wrong = mutate(matchingHistorical(direct, EVENT_MONTH, 1),
                999L, null, null, null, null, null, null, null, null);
        stubHistorical(List.of(wrong));
        assertIdentityBlocked(direct);
    }

    @Test
    void sourcePeriodMismatchBlocks() {
        BonusP15Fact direct = monthlyDirect(201L, 101L, 11L, 21L, 10_000L);
        DiscoveredBonusFact wrong = mutate(matchingHistorical(direct, EVENT_MONTH, 1),
                null, LocalDate.of(2026, 8, 2), null, null, null, null, null, null, null);
        stubHistorical(List.of(wrong));
        assertIdentityBlocked(direct);
    }

    @Test
    void factualAmountMismatchBlocks() {
        BonusP15Fact direct = monthlyDirect(201L, 101L, 11L, 21L, 10_000L);
        DiscoveredBonusFact wrong = mutate(matchingHistorical(direct, EVENT_MONTH, 1),
                null, null, 10_001L, null, null, null, null, null, null);
        stubHistorical(List.of(wrong));
        assertIdentityBlocked(direct);
    }

    @Test
    void currencyMismatchBlocks() {
        BonusP15Fact direct = monthlyDirect(201L, 101L, 11L, 21L, 10_000L);
        DiscoveredBonusFact wrong = mutate(matchingHistorical(direct, EVENT_MONTH, 1),
                null, null, null, "USD", null, null, null, null, null);
        stubHistorical(List.of(wrong));
        assertIdentityBlocked(direct);
    }

    @Test
    void indicatorMismatchBlocks() {
        BonusP15Fact direct = monthlyDirect(201L, 101L, 11L, 21L, 10_000L);
        DiscoveredBonusFact wrong = mutate(matchingHistorical(direct, EVENT_MONTH, 1),
                null, null, null, null, "OTHER_KPI", null, null, null, null);
        stubHistorical(List.of(wrong));
        assertIdentityBlocked(direct);
    }

    @Test
    void awardPeriodMismatchBlocks() {
        BonusP15Fact direct = monthlyDirect(201L, 101L, 11L, 21L, 10_000L);
        DiscoveredBonusFact wrong = mutate(matchingHistorical(direct, EVENT_MONTH, 1),
                null, null, null, null, null, LocalDate.of(2026, 8, 2), null, null, null);
        stubHistorical(List.of(wrong));
        assertIdentityBlocked(direct);
    }

    @Test
    void actualWorkAccrualFlagMismatchBlocks() {
        BonusP15Fact direct = monthlyDirect(201L, 101L, 11L, 21L, 10_000L);
        DiscoveredBonusFact wrong = mutate(matchingHistorical(direct, EVENT_MONTH, 1),
                null, null, null, null, null, null, null, false, null);
        stubHistorical(List.of(wrong));
        assertIdentityBlocked(direct);
    }

    @Test
    void partialAwardProrationFlagMismatchBlocks() {
        BonusP15Fact direct = monthlyDirect(201L, 101L, 11L, 21L, 10_000L);
        DiscoveredBonusFact wrong = mutate(matchingHistorical(direct, EVENT_MONTH, 1),
                null, null, null, null, null, null, null, null, false);
        stubHistorical(List.of(wrong));
        assertIdentityBlocked(direct);
    }

    @Test
    void unmatchedHistoricalMonthlyBonusIsIgnored() {
        DiscoveredBonusFact unmatched = standaloneHistorical(
                301L, 401L, 501L, 31L,
                PayrollEarningKind.MONTHLY_BONUS,
                PayrollBonusP15Nature.MONTHLY,
                YearMonth.of(2026, 9),
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
                12_000L,
                "MONTHLY_FUTURE",
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
                true,
                true
        );
        stubHistorical(List.of(unmatched));

        Resolution result = service.resolve(user, preEvent(List.of()), THROUGH, ZERO_PROOFS);
        assertTrue(result.ready());
        assertTrue(result.bonusFacts().isEmpty());
    }

    @Test
    void unmatchedHistoricalWorkPeriodBonusIsIgnored() {
        DiscoveredBonusFact unmatched = standaloneHistorical(
                302L, 402L, 502L, 32L,
                PayrollEarningKind.ONE_TIME_BONUS,
                PayrollBonusP15Nature.WORK_PERIOD,
                YearMonth.of(2026, 9),
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 5),
                25_000L,
                "PROJECT_FUTURE",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 30),
                true,
                true
        );
        stubHistorical(List.of(unmatched));

        assertTrue(service.resolve(user, preEvent(List.of()), THROUGH, ZERO_PROOFS)
                .bonusFacts().isEmpty());
    }

    @Test
    void unmatchedHistoricalAnnualRewardIsSurfacedForLaterP15Policy() {
        DiscoveredBonusFact annual = standaloneHistorical(
                303L, 403L, 503L, 33L,
                PayrollEarningKind.ONE_TIME_BONUS,
                PayrollBonusP15Nature.ANNUAL_RESULT,
                YearMonth.of(2026, 10),
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 1),
                90_000L,
                "ANNUAL_RESULT",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31),
                false,
                false
        );
        stubHistorical(List.of(annual));

        var fact = service.resolve(user, preEvent(List.of()), THROUGH, ZERO_PROOFS)
                .bonusFacts().get(0);
        assertEquals(AccrualOrigin.HISTORICAL_ANNUAL_OR_SERVICE_DISCOVERY, fact.origin());
        assertEquals(YearMonth.of(2026, 10), fact.accrualMonth());
        assertEquals(PayrollBonusP15Nature.ANNUAL_RESULT, fact.p15Nature());
    }

    @Test
    void unmatchedHistoricalServiceLengthRewardIsSurfacedForLaterP15Policy() {
        DiscoveredBonusFact reward = standaloneHistorical(
                304L, 404L, 504L, 34L,
                PayrollEarningKind.ONE_TIME_BONUS,
                PayrollBonusP15Nature.SERVICE_LENGTH,
                YearMonth.of(2026, 9),
                LocalDate.of(2026, 9, 2),
                LocalDate.of(2026, 9, 2),
                70_000L,
                "SERVICE_LENGTH",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31),
                false,
                false
        );
        stubHistorical(List.of(reward));

        assertEquals(
                PayrollBonusP15Nature.SERVICE_LENGTH,
                service.resolve(user, preEvent(List.of()), THROUGH, ZERO_PROOFS)
                        .bonusFacts().get(0).p15Nature()
        );
    }

    @Test
    void laterAnnualAccrualMonthIsPreservedWithoutEligibilityDecision() {
        DiscoveredBonusFact annual = standaloneHistorical(
                305L, 405L, 505L, 35L,
                PayrollEarningKind.ONE_TIME_BONUS,
                PayrollBonusP15Nature.ANNUAL_RESULT,
                THROUGH,
                THROUGH.atDay(3),
                THROUGH.atDay(3),
                99_999L,
                "ANNUAL_LATE",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31),
                false,
                false
        );
        stubHistorical(List.of(annual));

        var fact = service.resolve(user, preEvent(List.of()), THROUGH, ZERO_PROOFS)
                .bonusFacts().get(0);
        assertEquals(THROUGH, fact.accrualMonth());
        assertEquals(99_999L, fact.factualAmountMinor());
    }

    @Test
    void directAnnualAndHistoricalDiscoveryDoNotDuplicateSameNatureIdentity() {
        BonusP15Fact direct = annualDirect(203L, 103L, 13L, 23L, 40_000L);
        DiscoveredBonusFact matching = matchingHistorical(direct, EVENT_MONTH, 1);
        DiscoveredBonusFact otherAnnual = standaloneHistorical(
                306L, 406L, 506L, 36L,
                PayrollEarningKind.ONE_TIME_BONUS,
                PayrollBonusP15Nature.ANNUAL_RESULT,
                YearMonth.of(2026, 10),
                LocalDate.of(2026, 10, 4),
                LocalDate.of(2026, 10, 4),
                88_000L,
                "ANNUAL_OTHER",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31),
                false,
                false
        );
        stubHistorical(List.of(matching, otherAnnual));

        Resolution result = service.resolve(user, preEvent(List.of(direct)), THROUGH, ZERO_PROOFS);
        assertEquals(2, result.bonusFacts().size());
        assertEquals(AccrualOrigin.PRE_EVENT_SOURCE, result.bonusFacts().get(0).origin());
        assertEquals(AccrualOrigin.HISTORICAL_ANNUAL_OR_SERVICE_DISCOVERY,
                result.bonusFacts().get(1).origin());
    }

    @Test
    void duplicateHistoricalNatureIdentityIsStructuralFailure() {
        DiscoveredBonusFact first = standaloneHistorical(
                307L, 407L, 507L, 37L,
                PayrollEarningKind.ONE_TIME_BONUS,
                PayrollBonusP15Nature.ANNUAL_RESULT,
                YearMonth.of(2026, 9),
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 1),
                80_000L,
                "ANNUAL_DUP",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31),
                false,
                false
        );
        DiscoveredBonusFact duplicate = new DiscoveredBonusFact(
                YearMonth.of(2026, 10).atDay(1),
                1,
                first.bonusNatureFactId(),
                999L,
                998L,
                997L,
                PayrollEarningKind.ONE_TIME_BONUS,
                PayrollBonusP15Nature.ANNUAL_RESULT,
                YearMonth.of(2026, 10),
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 1),
                81_000L,
                "RUB",
                "ANNUAL_DUP_2",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31),
                false,
                false
        );
        stubHistorical(List.of(first, duplicate));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.resolve(user, preEvent(List.of()), THROUGH, ZERO_PROOFS)
        );
        assertEquals(
                AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.DUPLICATE_HISTORICAL_IDENTITY,
                ex.getMessage()
        );
    }

    @Test
    void secondDirectMismatchClearsOtherwiseAcceptedFirstFact() {
        BonusP15Fact first = monthlyDirect(201L, 101L, 11L, 21L, 10_000L);
        BonusP15Fact second = workPeriodDirect(202L, 102L, 12L, 22L, 30_000L);
        DiscoveredBonusFact firstHistorical = matchingHistorical(first, EVENT_MONTH, 1);
        DiscoveredBonusFact wrongSecond = mutate(matchingHistorical(second, EVENT_MONTH, 1),
                null, null, 30_001L, null, null, null, null, null, null);
        stubHistorical(List.of(firstHistorical, wrongSecond));

        Resolution result = service.resolve(
                user,
                preEvent(List.of(first, second)),
                THROUGH,
                ZERO_PROOFS
        );
        assertFalse(result.ready());
        assertTrue(result.bonusFacts().isEmpty());
    }

    @Test
    void factualMoneyAndCurrencyArePreservedAsFactsWithoutFormula() {
        BonusP15Fact direct = monthlyDirect(201L, 101L, 11L, 21L, 12_345L);
        stubHistorical(List.of(matchingHistorical(direct, EVENT_MONTH, 1)));

        var fact = service.resolve(user, preEvent(List.of(direct)), THROUGH, ZERO_PROOFS)
                .bonusFacts().get(0);
        assertEquals(12_345L, fact.factualAmountMinor());
        assertEquals("RUB", fact.currencyCode());
    }

    private void assertIdentityBlocked(BonusP15Fact direct) {
        Resolution result = service.resolve(user, preEvent(List.of(direct)), THROUGH, ZERO_PROOFS);
        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.FACT_IDENTITY_MISMATCH,
                result.blockingReason()
        );
        assertTrue(result.bonusFacts().isEmpty());
    }

    private void stubHistorical(List<DiscoveredBonusFact> facts) {
        doReturn(AverageEarningsBonusP15HistoricalFactDiscoveryService.Resolution.ready(
                EVENT,
                EVENT_MONTH,
                REF_FROM,
                REF_TO,
                THROUGH,
                facts.isEmpty() ? null : "RUB",
                facts
        )).when(historical).resolve(user, EVENT, THROUGH, ZERO_PROOFS);
    }

    private static AverageEarningsParagraph7PreEventBonusP15FactService.Resolution preEvent(
            List<BonusP15Fact> facts
    ) {
        AverageEarningsParagraph7PreEventSemanticWageFactService.Resolution semantic =
                mock(AverageEarningsParagraph7PreEventSemanticWageFactService.Resolution.class);
        doReturn(true).when(semantic).ready();
        doReturn(EVENT).when(semantic).eventDate();
        doReturn(FROM).when(semantic).periodFrom();
        doReturn(EVENT).when(semantic).cutoffExclusive();
        return new AverageEarningsParagraph7PreEventBonusP15FactService.Resolution(
                EVENT,
                FROM,
                EVENT,
                true,
                null,
                null,
                semantic,
                facts
        );
    }

    private static BonusP15Fact monthlyDirect(
            long natureId,
            long averageId,
            long sourceId,
            long componentId,
            long amount
    ) {
        SemanticWageFact source = new SemanticWageFact(
                SourceAuthority.BONUS_SOURCE,
                PayrollEarningKind.MONTHLY_BONUS,
                sourceId,
                componentId,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 15),
                amount,
                "RUB",
                null,
                null
        );
        AverageFact average = new AverageFact(
                averageId,
                sourceId,
                componentId,
                PayrollEarningKind.MONTHLY_BONUS,
                "MONTHLY_KPI",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                false,
                true,
                true
        );
        NatureFact nature = new NatureFact(
                natureId,
                averageId,
                sourceId,
                componentId,
                PayrollEarningKind.MONTHLY_BONUS,
                PayrollBonusP15Nature.MONTHLY
        );
        return new BonusP15Fact(source, average, nature);
    }

    private static BonusP15Fact workPeriodDirect(
            long natureId,
            long averageId,
            long sourceId,
            long componentId,
            long amount
    ) {
        SemanticWageFact source = new SemanticWageFact(
                SourceAuthority.BONUS_SOURCE,
                PayrollEarningKind.ONE_TIME_BONUS,
                sourceId,
                componentId,
                LocalDate.of(2026, 8, 3),
                LocalDate.of(2026, 8, 10),
                amount,
                "RUB",
                null,
                null
        );
        AverageFact average = new AverageFact(
                averageId,
                sourceId,
                componentId,
                PayrollEarningKind.ONE_TIME_BONUS,
                "PROJECT",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 30),
                false,
                true,
                true
        );
        NatureFact nature = new NatureFact(
                natureId,
                averageId,
                sourceId,
                componentId,
                PayrollEarningKind.ONE_TIME_BONUS,
                PayrollBonusP15Nature.WORK_PERIOD
        );
        return new BonusP15Fact(source, average, nature);
    }

    private static BonusP15Fact annualDirect(
            long natureId,
            long averageId,
            long sourceId,
            long componentId,
            long amount
    ) {
        SemanticWageFact source = new SemanticWageFact(
                SourceAuthority.BONUS_SOURCE,
                PayrollEarningKind.ONE_TIME_BONUS,
                sourceId,
                componentId,
                LocalDate.of(2026, 8, 5),
                LocalDate.of(2026, 8, 6),
                amount,
                "RUB",
                null,
                null
        );
        AverageFact average = new AverageFact(
                averageId,
                sourceId,
                componentId,
                PayrollEarningKind.ONE_TIME_BONUS,
                "ANNUAL_RESULT",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31),
                true,
                false,
                false
        );
        NatureFact nature = new NatureFact(
                natureId,
                averageId,
                sourceId,
                componentId,
                PayrollEarningKind.ONE_TIME_BONUS,
                PayrollBonusP15Nature.ANNUAL_RESULT
        );
        return new BonusP15Fact(source, average, nature);
    }

    private static BonusP15Fact serviceLengthDirect(
            long natureId,
            long averageId,
            long sourceId,
            long componentId,
            long amount
    ) {
        SemanticWageFact source = new SemanticWageFact(
                SourceAuthority.BONUS_SOURCE,
                PayrollEarningKind.ONE_TIME_BONUS,
                sourceId,
                componentId,
                LocalDate.of(2026, 8, 7),
                LocalDate.of(2026, 8, 8),
                amount,
                "RUB",
                null,
                null
        );
        AverageFact average = new AverageFact(
                averageId,
                sourceId,
                componentId,
                PayrollEarningKind.ONE_TIME_BONUS,
                "SERVICE_LENGTH",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31),
                false,
                false,
                false
        );
        NatureFact nature = new NatureFact(
                natureId,
                averageId,
                sourceId,
                componentId,
                PayrollEarningKind.ONE_TIME_BONUS,
                PayrollBonusP15Nature.SERVICE_LENGTH
        );
        return new BonusP15Fact(source, average, nature);
    }

    private static DiscoveredBonusFact matchingHistorical(
            BonusP15Fact direct,
            YearMonth accrualMonth,
            int revision
    ) {
        var source = direct.sourceFact();
        var average = direct.averageFact();
        var nature = direct.natureFact();
        return new DiscoveredBonusFact(
                accrualMonth.atDay(1),
                revision,
                nature.factId(),
                source.factId(),
                average.factId(),
                source.componentId(),
                source.earningKind(),
                nature.p15Nature(),
                accrualMonth,
                source.periodFrom(),
                source.periodTo(),
                source.amountMinor(),
                source.currencyCode(),
                average.indicatorKey(),
                average.awardPeriodFrom(),
                average.awardPeriodTo(),
                average.accruedForActualWorkTime(),
                average.proratedForPartialAwardPeriod()
        );
    }

    private static DiscoveredBonusFact standaloneHistorical(
            long natureId,
            long sourceId,
            long averageId,
            long componentId,
            PayrollEarningKind kind,
            PayrollBonusP15Nature nature,
            YearMonth accrualMonth,
            LocalDate sourceFrom,
            LocalDate sourceTo,
            long amount,
            String indicator,
            LocalDate awardFrom,
            LocalDate awardTo,
            Boolean actualWork,
            Boolean prorated
    ) {
        return new DiscoveredBonusFact(
                accrualMonth.atDay(1),
                1,
                natureId,
                sourceId,
                averageId,
                componentId,
                kind,
                nature,
                accrualMonth,
                sourceFrom,
                sourceTo,
                amount,
                "RUB",
                indicator,
                awardFrom,
                awardTo,
                actualWork,
                prorated
        );
    }

    private static DiscoveredBonusFact mutate(
            DiscoveredBonusFact source,
            Long componentId,
            LocalDate sourceFrom,
            Long amount,
            String currency,
            String indicator,
            LocalDate awardFrom,
            LocalDate awardTo,
            Boolean actualWork,
            Boolean prorated
    ) {
        return new DiscoveredBonusFact(
                source.snapshotPeriodMonth(),
                source.snapshotRevision(),
                source.bonusNatureFactId(),
                source.bonusSourceFactId(),
                source.bonusAverageFactId(),
                componentId == null ? source.componentId() : componentId,
                source.earningKind(),
                source.p15Nature(),
                source.accrualMonth(),
                sourceFrom == null ? source.sourcePeriodFrom() : sourceFrom,
                source.sourcePeriodTo(),
                amount == null ? source.amountMinor() : amount,
                currency == null ? source.currencyCode() : currency,
                indicator == null ? source.indicatorKey() : indicator,
                awardFrom == null ? source.awardPeriodFrom() : awardFrom,
                awardTo == null ? source.awardPeriodTo() : awardTo,
                actualWork == null ? source.accruedForActualWorkTime() : actualWork,
                prorated == null ? source.proratedForPartialAwardPeriod() : prorated
        );
    }
}
