package ru.daniil.shifts.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PayrollCombinationEpisodeFactTest {

    private final AppUser owner = mock(AppUser.class);

    @Test
    void constructorNormalizesCurrencyAndPreservesObservedFacts() {
        PayrollCombinationEpisodeFact fact = valid(" rub ", null);

        assertSame(owner, fact.getOwner());
        assertEquals(42L, fact.getComponentId());
        assertEquals(LocalDate.of(2026, 6, 1), fact.getPeriodFrom());
        assertEquals(LocalDate.of(2026, 6, 15), fact.getPeriodTo());
        assertEquals(4_740L, fact.getQualifiedMinutes());
        assertEquals(716_062L, fact.getAmountMinor());
        assertEquals("RUB", fact.getCurrencyCode());
        assertNull(fact.getAgreedRateBps());
        assertNotNull(fact.getCreatedAt());
        assertNotNull(fact.getUpdatedAt());
        assertNull(fact.getId());
    }

    @Test
    void constructorRejectsNonPositiveComponentIdentity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PayrollCombinationEpisodeFact(
                        owner, 0L,
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2),
                        60L, 10_000L, "RUB", null
                )
        );
    }

    @Test
    void constructorRejectsEitherMissingPeriodEndpoint() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PayrollCombinationEpisodeFact(
                        owner, 42L,
                        null, LocalDate.of(2026, 6, 2),
                        60L, 10_000L, "RUB", null
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new PayrollCombinationEpisodeFact(
                        owner, 42L,
                        LocalDate.of(2026, 6, 1), null,
                        60L, 10_000L, "RUB", null
                )
        );
    }

    @Test
    void constructorRejectsReversedAndCrossMonthPeriods() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PayrollCombinationEpisodeFact(
                        owner, 42L,
                        LocalDate.of(2026, 6, 3), LocalDate.of(2026, 6, 2),
                        60L, 10_000L, "RUB", null
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new PayrollCombinationEpisodeFact(
                        owner, 42L,
                        LocalDate.of(2026, 6, 30), LocalDate.of(2026, 7, 1),
                        60L, 10_000L, "RUB", null
                )
        );
    }

    @Test
    void constructorRejectsNonPositiveQualifiedMinutes() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PayrollCombinationEpisodeFact(
                        owner, 42L,
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2),
                        0L, 10_000L, "RUB", null
                )
        );
    }

    @Test
    void constructorRejectsObservedAmountOutsideAuthorityBounds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PayrollCombinationEpisodeFact(
                        owner, 42L,
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2),
                        60L, 0L, "RUB", null
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new PayrollCombinationEpisodeFact(
                        owner, 42L,
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2),
                        60L, 1_000_000_000_001L, "RUB", null
                )
        );
    }

    @Test
    void constructorRejectsMissingOrMalformedCurrency() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PayrollCombinationEpisodeFact(
                        owner, 42L,
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2),
                        60L, 10_000L, null, null
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new PayrollCombinationEpisodeFact(
                        owner, 42L,
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2),
                        60L, 10_000L, "R1B", null
                )
        );
    }

    @Test
    void constructorRejectsAgreedRateBoundsAndAcceptsBothNullAndMaximum() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PayrollCombinationEpisodeFact(
                        owner, 42L,
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2),
                        60L, 10_000L, "RUB", 0
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new PayrollCombinationEpisodeFact(
                        owner, 42L,
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2),
                        60L, 10_000L, "RUB", 10_000_001
                )
        );

        PayrollCombinationEpisodeFact max = new PayrollCombinationEpisodeFact(
                owner, 42L,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2),
                60L, 10_000L, "RUB", 10_000_000
        );
        assertEquals(10_000_000, max.getAgreedRateBps());
        assertNull(valid("RUB", null).getAgreedRateBps());
    }

    @Test
    void updateRevalidatesShapeAndRefreshesValues() {
        PayrollCombinationEpisodeFact fact = valid("RUB", 2_500);
        Instant before = fact.getUpdatedAt();

        fact.update(
                LocalDate.of(2026, 6, 16),
                LocalDate.of(2026, 6, 30),
                480L,
                85_824L,
                "usd",
                null
        );

        assertEquals(LocalDate.of(2026, 6, 16), fact.getPeriodFrom());
        assertEquals(LocalDate.of(2026, 6, 30), fact.getPeriodTo());
        assertEquals(480L, fact.getQualifiedMinutes());
        assertEquals(85_824L, fact.getAmountMinor());
        assertEquals("USD", fact.getCurrencyCode());
        assertNull(fact.getAgreedRateBps());
        assertFalse(fact.getUpdatedAt().isBefore(before));
    }

    @Test
    void prePersistValidatesAndPreservesExistingCreatedAt() {
        PayrollCombinationEpisodeFact fact = valid("RUB", 2_500);
        Instant created = fact.getCreatedAt();

        fact.prePersist();

        assertEquals(created, fact.getCreatedAt());
        assertNotNull(fact.getUpdatedAt());
    }

    @Test
    void prePersistInitializesMissingCreatedAt() throws Exception {
        PayrollCombinationEpisodeFact fact = valid("RUB", 2_500);
        setField(fact, "createdAt", null);

        fact.prePersist();

        assertNotNull(fact.getCreatedAt());
        assertNotNull(fact.getUpdatedAt());
    }

    @Test
    void lifecycleRejectsMissingOwnerAndWrapsCorruptPersistedShape() throws Exception {
        PayrollCombinationEpisodeFact noOwner = new PayrollCombinationEpisodeFact();
        assertThrows(IllegalStateException.class, noOwner::prePersist);

        PayrollCombinationEpisodeFact corrupt = valid("RUB", 2_500);
        setField(corrupt, "currencyCode", "R1B");

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                corrupt::preUpdate
        );
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
    }

    private PayrollCombinationEpisodeFact valid(String currency, Integer rate) {
        return new PayrollCombinationEpisodeFact(
                owner,
                42L,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 15),
                4_740L,
                716_062L,
                currency,
                rate
        );
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
