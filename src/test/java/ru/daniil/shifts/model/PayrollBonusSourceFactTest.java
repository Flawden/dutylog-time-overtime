package ru.daniil.shifts.model;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PayrollBonusSourceFactTest {

    private final AppUser owner = mock(AppUser.class);

    @Test
    void constructorPreservesKindPeriodMoneyAndNormalizesCurrency() {
        var fact = new PayrollBonusSourceFact(
                owner,
                42L,
                PayrollEarningKind.MONTHLY_BONUS,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                2_550_880L,
                " rub "
        );

        assertEquals(42L, fact.getComponentId());
        assertEquals(PayrollEarningKind.MONTHLY_BONUS, fact.getEarningKind());
        assertEquals(LocalDate.of(2026, 3, 1), fact.getPeriodFrom());
        assertEquals(LocalDate.of(2026, 3, 31), fact.getPeriodTo());
        assertEquals(2_550_880L, fact.getAmountMinor());
        assertEquals("RUB", fact.getCurrencyCode());
    }

    @Test
    void bothBonusKindsAreSupportedButNonBonusKindsAreRejected() {
        assertDoesNotThrow(() -> new PayrollBonusSourceFact(
                owner, 42L, PayrollEarningKind.MONTHLY_BONUS,
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28),
                100L, "RUB"
        ));
        assertDoesNotThrow(() -> new PayrollBonusSourceFact(
                owner, 43L, PayrollEarningKind.ONE_TIME_BONUS,
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28),
                100L, "RUB"
        ));
        assertThrows(IllegalArgumentException.class, () -> new PayrollBonusSourceFact(
                owner, 44L, PayrollEarningKind.REGIONAL_COEFFICIENT,
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28),
                100L, "RUB"
        ));
        assertThrows(IllegalArgumentException.class, () -> new PayrollBonusSourceFact(
                owner, 44L, null,
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28),
                100L, "RUB"
        ));
    }

    @Test
    void constructorRejectsInvalidComponentPeriodMoneyOrCurrency() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);

        assertThrows(IllegalArgumentException.class, () -> new PayrollBonusSourceFact(
                owner, 0L, PayrollEarningKind.MONTHLY_BONUS,
                from, to, 100L, "RUB"));
        assertThrows(IllegalArgumentException.class, () -> new PayrollBonusSourceFact(
                owner, 42L, PayrollEarningKind.MONTHLY_BONUS,
                null, to, 100L, "RUB"));
        assertThrows(IllegalArgumentException.class, () -> new PayrollBonusSourceFact(
                owner, 42L, PayrollEarningKind.MONTHLY_BONUS,
                to, from, 100L, "RUB"));
        assertThrows(IllegalArgumentException.class, () -> new PayrollBonusSourceFact(
                owner, 42L, PayrollEarningKind.MONTHLY_BONUS,
                LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 1),
                100L, "RUB"));
        assertThrows(IllegalArgumentException.class, () -> new PayrollBonusSourceFact(
                owner, 42L, PayrollEarningKind.MONTHLY_BONUS,
                from, to, 0L, "RUB"));
        assertThrows(IllegalArgumentException.class, () -> new PayrollBonusSourceFact(
                owner, 42L, PayrollEarningKind.MONTHLY_BONUS,
                from, to, 1_000_000_000_001L, "RUB"));
        assertThrows(IllegalArgumentException.class, () -> new PayrollBonusSourceFact(
                owner, 42L, PayrollEarningKind.MONTHLY_BONUS,
                from, to, 100L, "R1B"));
    }

    @Test
    void updateKeepsSemanticIdentityAndChangesOnlySourceShape() {
        var fact = new PayrollBonusSourceFact(
                owner,
                42L,
                PayrollEarningKind.ONE_TIME_BONUS,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                1_905_000L,
                "RUB"
        );

        fact.update(
                LocalDate.of(2026, 7, 10),
                LocalDate.of(2026, 7, 31),
                1_900_000L,
                "rub"
        );

        assertEquals(42L, fact.getComponentId());
        assertEquals(PayrollEarningKind.ONE_TIME_BONUS, fact.getEarningKind());
        assertEquals(LocalDate.of(2026, 7, 10), fact.getPeriodFrom());
        assertEquals(1_900_000L, fact.getAmountMinor());
        assertEquals("RUB", fact.getCurrencyCode());
    }

    @Test
    void persistenceCallbacksRejectCorruptStoredShape() {
        var fact = new PayrollBonusSourceFact(
                owner,
                42L,
                PayrollEarningKind.MONTHLY_BONUS,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                2_550_880L,
                "RUB"
        );

        fact.prePersist();
        assertNotNull(fact.getCreatedAt());
        assertNotNull(fact.getUpdatedAt());

        ReflectionTestUtils.setField(
                fact,
                "earningKind",
                PayrollEarningKind.REGIONAL_COEFFICIENT
        );

        assertThrows(IllegalStateException.class, fact::preUpdate);
    }

    @Test
    void persistenceCallbackRejectsMissingOwner() {
        var fact = new PayrollBonusSourceFact();
        ReflectionTestUtils.setField(fact, "componentId", 42L);
        ReflectionTestUtils.setField(fact, "earningKind", PayrollEarningKind.MONTHLY_BONUS);
        ReflectionTestUtils.setField(fact, "periodFrom", LocalDate.of(2026, 3, 1));
        ReflectionTestUtils.setField(fact, "periodTo", LocalDate.of(2026, 3, 31));
        ReflectionTestUtils.setField(fact, "amountMinor", 100L);
        ReflectionTestUtils.setField(fact, "currencyCode", "RUB");

        assertThrows(IllegalStateException.class, fact::prePersist);
    }
}
