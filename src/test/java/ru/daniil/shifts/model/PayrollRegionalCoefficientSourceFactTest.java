package ru.daniil.shifts.model;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PayrollRegionalCoefficientSourceFactTest {

    private final AppUser owner = mock(AppUser.class);

    @Test
    void constructorPreservesExplicitPeriodMoneyAndNormalizesCurrency() {
        var fact = new PayrollRegionalCoefficientSourceFact(
                owner,
                42L,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                1_339_212L,
                " rub "
        );

        assertEquals(42L, fact.getComponentId());
        assertEquals(LocalDate.of(2026, 3, 1), fact.getPeriodFrom());
        assertEquals(LocalDate.of(2026, 3, 31), fact.getPeriodTo());
        assertEquals(1_339_212L, fact.getAmountMinor());
        assertEquals("RUB", fact.getCurrencyCode());
    }

    @Test
    void constructorRejectsInvalidComponentPeriodMoneyOrCurrency() {
        assertThrows(IllegalArgumentException.class,
                () -> new PayrollRegionalCoefficientSourceFact(
                        owner, 0L,
                        LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                        100L, "RUB"));
        assertThrows(IllegalArgumentException.class,
                () -> new PayrollRegionalCoefficientSourceFact(
                        owner, 42L,
                        null, LocalDate.of(2026, 3, 31),
                        100L, "RUB"));
        assertThrows(IllegalArgumentException.class,
                () -> new PayrollRegionalCoefficientSourceFact(
                        owner, 42L,
                        LocalDate.of(2026, 3, 31), LocalDate.of(2026, 3, 1),
                        100L, "RUB"));
        assertThrows(IllegalArgumentException.class,
                () -> new PayrollRegionalCoefficientSourceFact(
                        owner, 42L,
                        LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 1),
                        100L, "RUB"));
        assertThrows(IllegalArgumentException.class,
                () -> new PayrollRegionalCoefficientSourceFact(
                        owner, 42L,
                        LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                        0L, "RUB"));
        assertThrows(IllegalArgumentException.class,
                () -> new PayrollRegionalCoefficientSourceFact(
                        owner, 42L,
                        LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                        1_000_000_000_001L, "RUB"));
        assertThrows(IllegalArgumentException.class,
                () -> new PayrollRegionalCoefficientSourceFact(
                        owner, 42L,
                        LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                        100L, "R1B"));
    }

    @Test
    void updateChangesOnlyExplicitSourceShape() {
        var fact = new PayrollRegionalCoefficientSourceFact(
                owner,
                42L,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                1_339_212L,
                "RUB"
        );

        fact.update(
                LocalDate.of(2026, 3, 19),
                LocalDate.of(2026, 3, 31),
                560_474L,
                "rub"
        );

        assertEquals(LocalDate.of(2026, 3, 19), fact.getPeriodFrom());
        assertEquals(560_474L, fact.getAmountMinor());
        assertEquals("RUB", fact.getCurrencyCode());
    }

    @Test
    void persistenceCallbacksRejectCorruptStoredShape() {
        var fact = new PayrollRegionalCoefficientSourceFact(
                owner,
                42L,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                1_339_212L,
                "RUB"
        );

        fact.prePersist();
        assertNotNull(fact.getCreatedAt());
        assertNotNull(fact.getUpdatedAt());

        ReflectionTestUtils.setField(fact, "currencyCode", "R1B");

        assertThrows(
                IllegalStateException.class,
                fact::preUpdate
        );
    }

    @Test
    void persistenceCallbackRejectsMissingOwner() {
        var fact = new PayrollRegionalCoefficientSourceFact();
        ReflectionTestUtils.setField(fact, "componentId", 42L);
        ReflectionTestUtils.setField(fact, "periodFrom", LocalDate.of(2026, 3, 1));
        ReflectionTestUtils.setField(fact, "periodTo", LocalDate.of(2026, 3, 31));
        ReflectionTestUtils.setField(fact, "amountMinor", 100L);
        ReflectionTestUtils.setField(fact, "currencyCode", "RUB");

        assertThrows(
                IllegalStateException.class,
                fact::prePersist
        );
    }
}
