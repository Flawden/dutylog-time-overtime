package ru.daniil.shifts.model;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PayrollBonusAverageEarningsFactTest {

    private final AppUser owner = mock(AppUser.class);

    @Test
    void monthlyFactPreservesAwardPeriodAndNormalizesIndicatorWithoutInferringAnnualRule() {
        var fact = new PayrollBonusAverageEarningsFact(
                owner,
                10L,
                42L,
                PayrollEarningKind.MONTHLY_BONUS,
                " monthly.40 ",
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                false,
                true,
                true
        );

        assertEquals(10L, fact.getBonusSourceFactId());
        assertEquals(42L, fact.getComponentId());
        assertEquals(PayrollEarningKind.MONTHLY_BONUS, fact.getEarningKind());
        assertEquals("MONTHLY.40", fact.getIndicatorKey());
        assertEquals(LocalDate.of(2026, 3, 1), fact.getAwardPeriodFrom());
        assertEquals(LocalDate.of(2026, 3, 31), fact.getAwardPeriodTo());
        assertFalse(fact.getAnnualResult());
        assertTrue(fact.getAccruedForActualWorkTime());
        assertTrue(fact.getProratedForPartialAwardPeriod());
    }

    @Test
    void annualResultRequiresOneTimeBonusAndOneCompleteCalendarYear() {
        assertDoesNotThrow(() -> new PayrollBonusAverageEarningsFact(
                owner, 11L, 43L, PayrollEarningKind.ONE_TIME_BONUS,
                "YEAR_RESULT",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31),
                true, false, false
        ));

        assertThrows(IllegalArgumentException.class, () -> new PayrollBonusAverageEarningsFact(
                owner, 11L, 43L, PayrollEarningKind.MONTHLY_BONUS,
                "YEAR_RESULT",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31),
                true, false, false
        ));

        assertThrows(IllegalArgumentException.class, () -> new PayrollBonusAverageEarningsFact(
                owner, 11L, 43L, PayrollEarningKind.ONE_TIME_BONUS,
                "YEAR_RESULT",
                LocalDate.of(2025, 2, 1),
                LocalDate.of(2025, 12, 31),
                true, false, false
        ));
    }

    @Test
    void nonAnnualOneTimeFactMayCarryAnyExplicitOrderedAwardPeriodForLaterPolicyClassification() {
        assertDoesNotThrow(() -> new PayrollBonusAverageEarningsFact(
                owner, 12L, 44L, PayrollEarningKind.ONE_TIME_BONUS,
                "SPECIAL_PROJECT",
                LocalDate.of(2025, 10, 15),
                LocalDate.of(2026, 2, 14),
                false, false, false
        ));
    }

    @Test
    void constructorRejectsInvalidIdentityKindIndicatorOrAwardPeriod() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);

        assertThrows(IllegalArgumentException.class, () -> fact(0L, 42L, PayrollEarningKind.MONTHLY_BONUS, "A", from, to));
        assertThrows(IllegalArgumentException.class, () -> fact(10L, 0L, PayrollEarningKind.MONTHLY_BONUS, "A", from, to));
        assertThrows(IllegalArgumentException.class, () -> fact(10L, 42L, PayrollEarningKind.REGIONAL_COEFFICIENT, "A", from, to));
        assertThrows(IllegalArgumentException.class, () -> fact(10L, 42L, PayrollEarningKind.MONTHLY_BONUS, "bad key", from, to));
        assertThrows(IllegalArgumentException.class, () -> fact(10L, 42L, PayrollEarningKind.MONTHLY_BONUS, "A", null, to));
        assertThrows(IllegalArgumentException.class, () -> fact(10L, 42L, PayrollEarningKind.MONTHLY_BONUS, "A", to, from));
    }

    @Test
    void updateChangesOnlyParagraph15FactsAndKeepsSourceIdentity() {
        var fact = fact(
                10L,
                42L,
                PayrollEarningKind.ONE_TIME_BONUS,
                "PROJECT_A",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 31)
        );

        fact.update(
                "project_b",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31),
                true,
                false,
                true
        );

        assertEquals(10L, fact.getBonusSourceFactId());
        assertEquals(42L, fact.getComponentId());
        assertEquals(PayrollEarningKind.ONE_TIME_BONUS, fact.getEarningKind());
        assertEquals("PROJECT_B", fact.getIndicatorKey());
        assertTrue(fact.getAnnualResult());
        assertTrue(fact.getProratedForPartialAwardPeriod());
    }

    @Test
    void persistenceCallbacksRejectCorruptStoredShape() {
        var fact = fact(
                10L,
                42L,
                PayrollEarningKind.MONTHLY_BONUS,
                "MONTHLY_40",
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31)
        );

        fact.prePersist();
        assertNotNull(fact.getCreatedAt());
        assertNotNull(fact.getUpdatedAt());

        ReflectionTestUtils.setField(fact, "indicatorKey", "not valid");
        assertThrows(IllegalStateException.class, fact::preUpdate);
    }

    @Test
    void persistenceCallbackRejectsMissingOwner() {
        var fact = new PayrollBonusAverageEarningsFact();
        ReflectionTestUtils.setField(fact, "bonusSourceFactId", 10L);
        ReflectionTestUtils.setField(fact, "componentId", 42L);
        ReflectionTestUtils.setField(fact, "earningKind", PayrollEarningKind.MONTHLY_BONUS);
        ReflectionTestUtils.setField(fact, "indicatorKey", "MONTHLY_40");
        ReflectionTestUtils.setField(fact, "awardPeriodFrom", LocalDate.of(2026, 3, 1));
        ReflectionTestUtils.setField(fact, "awardPeriodTo", LocalDate.of(2026, 3, 31));

        assertThrows(IllegalStateException.class, fact::prePersist);
    }

    private PayrollBonusAverageEarningsFact fact(
            long sourceId,
            long componentId,
            PayrollEarningKind kind,
            String indicator,
            LocalDate from,
            LocalDate to
    ) {
        return new PayrollBonusAverageEarningsFact(
                owner,
                sourceId,
                componentId,
                kind,
                indicator,
                from,
                to,
                false,
                false,
                false
        );
    }
}
