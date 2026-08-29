package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayrollBonusP15Nature;
import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.model.PayrollSnapshot;
import ru.daniil.shifts.repo.PayrollSnapshotRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AverageEarningsBonusP15HistoricalFactDiscoveryServiceTest {

    private static final LocalDate EVENT = LocalDate.of(2026, 1, 10);
    private static final YearMonth REF_FROM = YearMonth.of(2025, 1);
    private static final YearMonth REF_TO = YearMonth.of(2025, 12);

    private final PayrollSnapshotRepository snapshots = mock(PayrollSnapshotRepository.class);
    private final PayrollHistoricalBonusP15NatureFactsService historical =
            mock(PayrollHistoricalBonusP15NatureFactsService.class);
    private final AppUser user = mock(AppUser.class);
    private final AverageEarningsBonusP15HistoricalFactDiscoveryService service =
            new AverageEarningsBonusP15HistoricalFactDiscoveryService(snapshots, historical);

    @Test
    void snapshotMonthIsExplicitAccrualAuthorityForPolicyFact() {
        PayrollSnapshot march = snapshot(YearMonth.of(2025, 3), 2, "RUB");
        when(snapshots.findByOwnerAndPeriodMonthBetweenOrderByPeriodMonthAscRevisionDesc(
                user, REF_FROM.atDay(1), REF_TO.atDay(1)))
                .thenReturn(List.of(march));
        when(historical.resolve(march)).thenReturn(ready(monthlyFact(
                301L,
                YearMonth.of(2025, 2),
                LocalDate.of(2025, 3, 1),
                LocalDate.of(2025, 3, 31)
        )));

        var result = service.resolve(
                user,
                EVENT,
                REF_TO,
                zeroExcept(REF_TO, YearMonth.of(2025, 3))
        );

        assertTrue(result.ready());
        assertEquals("RUB", result.currencyCode());
        assertEquals(1, result.facts().size());
        var fact = result.facts().get(0);
        assertEquals(YearMonth.of(2025, 3), fact.accrualMonth());
        assertEquals(YearMonth.of(2025, 2), YearMonth.from(fact.awardPeriodFrom()));
        assertEquals(YearMonth.of(2025, 3), result.policyFacts().get(0).accrualMonth());
    }

    @Test
    void annualRewardAccruedAfterEventIsDiscoverableAsOfLaterPayrollMonth() {
        YearMonth through = YearMonth.of(2026, 3);
        PayrollSnapshot march = snapshot(through, 1, "RUB");
        when(snapshots.findByOwnerAndPeriodMonthBetweenOrderByPeriodMonthAscRevisionDesc(
                user, REF_FROM.atDay(1), through.atDay(1)))
                .thenReturn(List.of(march));
        when(historical.resolve(march)).thenReturn(ready(annualFact(9001L)));

        var discovered = service.resolve(user, EVENT, through, zeroExcept(through, through));
        assertTrue(discovered.ready());
        assertEquals(through, discovered.policyFacts().get(0).accrualMonth());

        var policy = AverageEarningsBonusP15Policy.resolve(
                EVENT,
                REF_FROM,
                REF_TO,
                true,
                discovered.policyFacts()
        );

        assertTrue(policy.ready());
        assertEquals(AverageEarningsBonusP15Policy.Eligibility.INCLUDE,
                policy.decisions().get(0).eligibility());
        assertEquals(AverageEarningsBonusP15Policy.LegalRule.PP_540_P15_PREVIOUS_CALENDAR_YEAR,
                policy.decisions().get(0).legalRule());
    }

    @Test
    void monthlyRewardAccruedAfterReferenceIsDiscoveredButPolicyExcludesIt() {
        YearMonth through = YearMonth.of(2026, 2);
        PayrollSnapshot february = snapshot(through, 1, "RUB");
        when(snapshots.findByOwnerAndPeriodMonthBetweenOrderByPeriodMonthAscRevisionDesc(
                user, REF_FROM.atDay(1), through.atDay(1)))
                .thenReturn(List.of(february));
        when(historical.resolve(february)).thenReturn(ready(monthlyFact(
                500L,
                through,
                through.atDay(1),
                through.atEndOfMonth()
        )));

        var discovered = service.resolve(user, EVENT, through, zeroExcept(through, through));
        var policy = AverageEarningsBonusP15Policy.resolve(
                EVENT, REF_FROM, REF_TO, true, discovered.policyFacts()
        );

        assertTrue(policy.ready());
        assertEquals(AverageEarningsBonusP15Policy.Eligibility.EXCLUDE_NOT_ACCRUED_IN_REFERENCE_PERIOD,
                policy.decisions().get(0).eligibility());
    }

    @Test
    void missingMonthWithoutExplicitZeroProofBlocksWithoutPartialFacts() {
        PayrollSnapshot january = snapshot(REF_FROM, 1, "RUB");
        when(snapshots.findByOwnerAndPeriodMonthBetweenOrderByPeriodMonthAscRevisionDesc(
                user, REF_FROM.atDay(1), REF_TO.atDay(1)))
                .thenReturn(List.of(january));
        when(historical.resolve(january)).thenReturn(ready(monthlyFact(
                1L, REF_FROM, REF_FROM.atDay(1), REF_FROM.atEndOfMonth()
        )));

        var result = service.resolve(user, EVENT, REF_TO, List.of());

        assertFalse(result.ready());
        assertEquals("HISTORICAL_P15_PAYROLL_SNAPSHOT_MISSING", result.blockingReason());
        assertEquals(YearMonth.of(2025, 2), result.blockingPeriod());
        assertTrue(result.facts().isEmpty());
        assertTrue(result.policyFacts().isEmpty());
        assertNull(result.currencyCode());
    }

    @Test
    void explicitNoPayrollProofMakesMissingMonthAnAuthoritativeZero() {
        when(snapshots.findByOwnerAndPeriodMonthBetweenOrderByPeriodMonthAscRevisionDesc(
                user, REF_FROM.atDay(1), REF_TO.atDay(1)))
                .thenReturn(List.of());

        var result = service.resolve(user, EVENT, REF_TO, allMonths(REF_FROM, REF_TO));

        assertTrue(result.ready());
        assertTrue(result.facts().isEmpty());
        assertNull(result.currencyCode());
        verifyNoInteractions(historical);
    }

    @Test
    void explicitZeroProofContradictingSnapshotBlocks() {
        PayrollSnapshot january = snapshot(REF_FROM, 1, "RUB");
        when(snapshots.findByOwnerAndPeriodMonthBetweenOrderByPeriodMonthAscRevisionDesc(
                user, REF_FROM.atDay(1), REF_TO.atDay(1)))
                .thenReturn(List.of(january));

        var result = service.resolve(user, EVENT, REF_TO, allMonths(REF_FROM, REF_TO));

        assertFalse(result.ready());
        assertEquals("HISTORICAL_P15_NO_PAYROLL_PROOF_CONTRADICTS_SNAPSHOT", result.blockingReason());
        assertEquals(REF_FROM, result.blockingPeriod());
        verifyNoInteractions(historical);
    }

    @Test
    void highestRevisionIsSelectedPerPayrollMonth() {
        PayrollSnapshot oldRevision = snapshot(YearMonth.of(2025, 6), 1, "RUB");
        PayrollSnapshot latestRevision = snapshot(YearMonth.of(2025, 6), 3, "RUB");
        when(snapshots.findByOwnerAndPeriodMonthBetweenOrderByPeriodMonthAscRevisionDesc(
                user, REF_FROM.atDay(1), REF_TO.atDay(1)))
                .thenReturn(List.of(oldRevision, latestRevision));
        when(historical.resolve(latestRevision)).thenReturn(ready(monthlyFact(
                603L,
                YearMonth.of(2025, 6),
                LocalDate.of(2025, 6, 1),
                LocalDate.of(2025, 6, 30)
        )));

        var result = service.resolve(
                user, EVENT, REF_TO, zeroExcept(REF_TO, YearMonth.of(2025, 6))
        );

        assertTrue(result.ready());
        assertEquals(3, result.facts().get(0).snapshotRevision());
        verify(historical).resolve(latestRevision);
        verify(historical, never()).resolve(oldRevision);
    }

    @Test
    void latestRevisionMarkedSupersededBlocks() {
        PayrollSnapshot january = snapshot(REF_FROM, 2, "RUB");
        when(january.getSupersededBy()).thenReturn(mock(PayrollSnapshot.class));
        when(snapshots.findByOwnerAndPeriodMonthBetweenOrderByPeriodMonthAscRevisionDesc(
                user, REF_FROM.atDay(1), REF_TO.atDay(1)))
                .thenReturn(List.of(january));

        var result = service.resolve(
                user, EVENT, REF_TO, zeroExcept(REF_TO, REF_FROM)
        );

        assertEquals("HISTORICAL_P15_PAYROLL_LATEST_REVISION_SUPERSEDED", result.blockingReason());
        assertEquals(REF_FROM, result.blockingPeriod());
        verifyNoInteractions(historical);
    }

    @Test
    void historicalP15BlockerPropagatesAtExactPayrollMonth() {
        YearMonth august = YearMonth.of(2025, 8);
        PayrollSnapshot snapshot = snapshot(august, 1, "RUB");
        when(snapshots.findByOwnerAndPeriodMonthBetweenOrderByPeriodMonthAscRevisionDesc(
                user, REF_FROM.atDay(1), REF_TO.atDay(1)))
                .thenReturn(List.of(snapshot));
        when(historical.resolve(snapshot)).thenReturn(new PayrollHistoricalBonusP15NatureFactsService.Resolution(
                false,
                "HISTORICAL_BONUS_P15_NATURE_INCOMPLETE",
                List.of()
        ));

        var result = service.resolve(user, EVENT, REF_TO, zeroExcept(REF_TO, august));

        assertFalse(result.ready());
        assertEquals("HISTORICAL_BONUS_P15_NATURE_INCOMPLETE", result.blockingReason());
        assertEquals(august, result.blockingPeriod());
        assertTrue(result.facts().isEmpty());
    }

    @Test
    void currencyMismatchAcrossDiscoveryMonthsBlocksWithoutPartialFacts() {
        PayrollSnapshot january = snapshot(REF_FROM, 1, "RUB");
        PayrollSnapshot february = snapshot(YearMonth.of(2025, 2), 1, "USD");
        when(snapshots.findByOwnerAndPeriodMonthBetweenOrderByPeriodMonthAscRevisionDesc(
                user, REF_FROM.atDay(1), REF_TO.atDay(1)))
                .thenReturn(List.of(january, february));
        when(historical.resolve(january)).thenReturn(emptyReady());

        var result = service.resolve(
                user,
                EVENT,
                REF_TO,
                zeroExcept(REF_TO, REF_FROM, YearMonth.of(2025, 2))
        );

        assertFalse(result.ready());
        assertEquals("HISTORICAL_P15_PAYROLL_CURRENCY_MISMATCH", result.blockingReason());
        assertEquals(YearMonth.of(2025, 2), result.blockingPeriod());
        assertNull(result.currencyCode());
        verify(historical, never()).resolve(february);
    }

    @Test
    void duplicateImmutableBonusIdentityAcrossPayrollMonthsBlocks() {
        PayrollSnapshot january = snapshot(REF_FROM, 1, "RUB");
        PayrollSnapshot february = snapshot(YearMonth.of(2025, 2), 1, "RUB");
        when(snapshots.findByOwnerAndPeriodMonthBetweenOrderByPeriodMonthAscRevisionDesc(
                user, REF_FROM.atDay(1), REF_TO.atDay(1)))
                .thenReturn(List.of(january, february));
        when(historical.resolve(january)).thenReturn(ready(monthlyFact(
                77L, REF_FROM, REF_FROM.atDay(1), REF_FROM.atEndOfMonth()
        )));
        when(historical.resolve(february)).thenReturn(ready(monthlyFact(
                77L,
                YearMonth.of(2025, 2),
                LocalDate.of(2025, 2, 1),
                LocalDate.of(2025, 2, 28)
        )));

        var result = service.resolve(
                user,
                EVENT,
                REF_TO,
                zeroExcept(REF_TO, REF_FROM, YearMonth.of(2025, 2))
        );

        assertFalse(result.ready());
        assertEquals("HISTORICAL_P15_BONUS_IDENTITY_DUPLICATE", result.blockingReason());
        assertEquals(YearMonth.of(2025, 2), result.blockingPeriod());
        assertTrue(result.facts().isEmpty());
    }

    @Test
    void discoveryThroughCannotPrecedeCanonicalReferenceEnd() {
        assertThrows(IllegalArgumentException.class, () -> service.resolve(
                user,
                EVENT,
                YearMonth.of(2025, 11),
                List.of()
        ));
        verifyNoInteractions(snapshots, historical);
    }

    @Test
    void zeroProofsMustBeOrderedUniqueAndInsideDiscoveryWindow() {
        assertThrows(IllegalArgumentException.class, () -> service.resolve(
                user,
                EVENT,
                REF_TO,
                List.of(YearMonth.of(2025, 2), YearMonth.of(2025, 1))
        ));
        assertThrows(IllegalArgumentException.class, () -> service.resolve(
                user,
                EVENT,
                REF_TO,
                List.of(YearMonth.of(2024, 12))
        ));
        verifyNoInteractions(snapshots, historical);
    }

    @Test
    void repositorySnapshotOutsideRequestedWindowIsRejected() {
        PayrollSnapshot outside = snapshot(YearMonth.of(2026, 1), 1, "RUB");
        when(snapshots.findByOwnerAndPeriodMonthBetweenOrderByPeriodMonthAscRevisionDesc(
                user, REF_FROM.atDay(1), REF_TO.atDay(1)))
                .thenReturn(List.of(outside));

        assertThrows(IllegalStateException.class, () -> service.resolve(
                user, EVENT, REF_TO, allMonths(REF_FROM, REF_TO)
        ));
    }

    @Test
    void nonCanonicalSnapshotPeriodDayIsRejected() {
        PayrollSnapshot invalid = mock(PayrollSnapshot.class);
        when(invalid.getPeriodMonth()).thenReturn(LocalDate.of(2025, 3, 15));
        when(invalid.getRevision()).thenReturn(1);
        when(snapshots.findByOwnerAndPeriodMonthBetweenOrderByPeriodMonthAscRevisionDesc(
                user, REF_FROM.atDay(1), REF_TO.atDay(1)))
                .thenReturn(List.of(invalid));

        assertThrows(IllegalStateException.class, () -> service.resolve(
                user, EVENT, REF_TO, allMonths(REF_FROM, REF_TO)
        ));
    }

    @Test
    void emptyHistoricalMonthsPreserveCoverageAndCurrencyWithoutInventingFacts() {
        PayrollSnapshot january = snapshot(REF_FROM, 1, "RUB");
        when(snapshots.findByOwnerAndPeriodMonthBetweenOrderByPeriodMonthAscRevisionDesc(
                user, REF_FROM.atDay(1), REF_TO.atDay(1)))
                .thenReturn(List.of(january));
        when(historical.resolve(january)).thenReturn(emptyReady());

        var result = service.resolve(
                user, EVENT, REF_TO, zeroExcept(REF_TO, REF_FROM)
        );

        assertTrue(result.ready());
        assertEquals("RUB", result.currencyCode());
        assertEquals(EVENT, result.eventDate());
        assertEquals(REF_FROM, result.referenceFrom());
        assertEquals(REF_TO, result.referenceTo());
        assertEquals(REF_TO, result.discoveryThroughMonth());
        assertTrue(result.facts().isEmpty());
    }

    private PayrollSnapshot snapshot(YearMonth month, int revision, String currency) {
        PayrollSnapshot snapshot = mock(PayrollSnapshot.class);
        when(snapshot.getPeriodMonth()).thenReturn(month.atDay(1));
        when(snapshot.getRevision()).thenReturn(revision);
        when(snapshot.getCurrencyCode()).thenReturn(currency);
        when(snapshot.getSupersededBy()).thenReturn(null);
        return snapshot;
    }

    private PayrollHistoricalBonusP15NatureFactsService.Resolution ready(
            PayrollHistoricalBonusP15NatureFactsService.HistoricalBonusP15Fact... facts
    ) {
        return new PayrollHistoricalBonusP15NatureFactsService.Resolution(
                true,
                null,
                Arrays.asList(facts)
        );
    }

    private PayrollHistoricalBonusP15NatureFactsService.Resolution emptyReady() {
        return new PayrollHistoricalBonusP15NatureFactsService.Resolution(true, null, List.of());
    }

    private PayrollHistoricalBonusP15NatureFactsService.HistoricalBonusP15Fact monthlyFact(
            long natureId,
            YearMonth awardMonth,
            LocalDate sourceFrom,
            LocalDate sourceTo
    ) {
        var base = new PayrollHistoricalBonusAverageEarningsFactsService.HistoricalBonusFact(
                natureId + 100,
                natureId + 200,
                natureId + 300,
                PayrollEarningKind.MONTHLY_BONUS,
                sourceFrom,
                sourceTo,
                450_000L,
                "RUB",
                "MONTHLY_KPI",
                awardMonth.atDay(1),
                awardMonth.atEndOfMonth(),
                false,
                true,
                false
        );
        return new PayrollHistoricalBonusP15NatureFactsService.HistoricalBonusP15Fact(
                natureId,
                base,
                PayrollBonusP15Nature.MONTHLY
        );
    }

    private PayrollHistoricalBonusP15NatureFactsService.HistoricalBonusP15Fact annualFact(long natureId) {
        var base = new PayrollHistoricalBonusAverageEarningsFactsService.HistoricalBonusFact(
                natureId + 100,
                natureId + 200,
                natureId + 300,
                PayrollEarningKind.ONE_TIME_BONUS,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                2_000_000L,
                "RUB",
                "ANNUAL_RESULT",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31),
                true,
                false,
                false
        );
        return new PayrollHistoricalBonusP15NatureFactsService.HistoricalBonusP15Fact(
                natureId,
                base,
                PayrollBonusP15Nature.ANNUAL_RESULT
        );
    }

    private List<YearMonth> zeroExcept(YearMonth through, YearMonth... snapshotsPresent) {
        List<YearMonth> present = List.of(snapshotsPresent);
        List<YearMonth> zero = new ArrayList<>();
        for (YearMonth month = REF_FROM; !month.isAfter(through); month = month.plusMonths(1)) {
            if (!present.contains(month)) {
                zero.add(month);
            }
        }
        return zero;
    }

    private List<YearMonth> allMonths(YearMonth from, YearMonth through) {
        List<YearMonth> result = new ArrayList<>();
        for (YearMonth month = from; !month.isAfter(through); month = month.plusMonths(1)) {
            result.add(month);
        }
        return result;
    }
}
