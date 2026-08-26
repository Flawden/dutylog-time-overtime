package ru.daniil.shifts.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class PayrollSnapshotCompensationComponentModelTest {

    private static final String FINGERPRINT =
            "c".repeat(64);

    @Test
    void legacyConstructorKeepsGenericComponentSnapshotNeutral() {
        PayrollSnapshot value =
                legacySnapshot();

        assertEquals(
                0,
                value.getCompensationComponentCount()
        );

        assertEquals(
                0L,
                value.getCompensationComponentEarningsMinor()
        );

        assertNull(
                value.getCompensationComponentFingerprint()
        );
    }

    @Test
    void nonEmptyComponentSnapshotRequiresFingerprint() {
        IllegalArgumentException error =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                snapshot(
                                        1,
                                        32_000L,
                                        null
                                )
                );

        assertTrue(
                error.getMessage()
                        .contains(
                                "requires fingerprint"
                        )
        );
    }

    @Test
    void emptyComponentSnapshotCannotContainMoneyOrFingerprint() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        snapshot(
                                0,
                                1L,
                                null
                        )
        );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        snapshot(
                                0,
                                0L,
                                FINGERPRINT
                        )
        );
    }

    @Test
    void zeroMoneyComponentProjectionMayStillFreezeIdentity() {
        PayrollSnapshot value =
                snapshot(
                        1,
                        0L,
                        FINGERPRINT
                );

        assertEquals(
                1,
                value.getCompensationComponentCount()
        );

        assertEquals(
                0L,
                value.getCompensationComponentEarningsMinor()
        );

        assertEquals(
                FINGERPRINT,
                value.getCompensationComponentFingerprint()
        );
    }

    @Test
    void percentageLineFreezesUserNameAndReferenceBase() {
        PayrollSnapshot snapshot =
                snapshot(
                        1,
                        32_000L,
                        FINGERPRINT
                );

        PayrollSnapshotComponentLine line =
                new PayrollSnapshotComponentLine(
                        snapshot,
                        0,
                        7L,
                        17L,
                        LocalDate.of(
                                2026,
                                8,
                                1
                        ),
                        "Премия за выживание после ночной смены",
                        PayrollEarningKind.HARMFUL_CONDITIONS,
                        "PERCENT_OF_BASE",
                        "EARNED_BASE_PAY",
                        400,
                        null,
                        null,
                        800_000L,
                        32_000L
                );

        assertEquals(
                "Премия за выживание после ночной смены",
                line.getDisplayName()
        );

        assertEquals(
                PayrollEarningKind.HARMFUL_CONDITIONS,
                line.getEarningKind()
        );

        assertEquals(
                "EARNED_BASE_PAY",
                line.getCalculationBase()
        );

        assertEquals(
                800_000L,
                line.getReferenceBaseMinor()
        );

        assertEquals(
                32_000L,
                line.getAmountMinor()
        );
    }

    @Test
    void fixedLineFreezesConfiguredAmountAndCurrency() {
        PayrollSnapshot snapshot =
                snapshot(
                        1,
                        50_000L,
                        FINGERPRINT
                );

        PayrollSnapshotComponentLine line =
                new PayrollSnapshotComponentLine(
                        snapshot,
                        0,
                        8L,
                        18L,
                        LocalDate.of(
                                2026,
                                8,
                                1
                        ),
                        "Фиксированная премия",
                        "FIXED_AMOUNT",
                        null,
                        null,
                        50_000L,
                        "RUB",
                        0L,
                        50_000L
                );

        assertEquals(
                50_000L,
                line.getConfiguredAmountMinor()
        );

        assertEquals(
                "RUB",
                line.getConfiguredCurrencyCode()
        );

        assertEquals(
                0L,
                line.getReferenceBaseMinor()
        );
    }

    @Test
    void invalidFrozenLineShapeFailsClosed() {
        PayrollSnapshot snapshot =
                snapshot(
                        1,
                        1L,
                        FINGERPRINT
                );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PayrollSnapshotComponentLine(
                                snapshot,
                                0,
                                9L,
                                19L,
                                LocalDate.of(
                                        2026,
                                        8,
                                        1
                                ),
                                "Сломанная",
                                "PERCENT_OF_BASE",
                                null,
                                400,
                                null,
                                null,
                                100L,
                                4L
                        )
        );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PayrollSnapshotComponentLine(
                                snapshot,
                                0,
                                9L,
                                19L,
                                LocalDate.of(
                                        2026,
                                        8,
                                        1
                                ),
                                "Сломанная",
                                "FIXED_AMOUNT",
                                null,
                                null,
                                100L,
                                "USD",
                                0L,
                                99L
                        )
        );
    }

    private PayrollSnapshot snapshot(
            int componentCount,
            long componentEarnings,
            String componentFingerprint
    ) {
        AppUser owner =
                new AppUser(
                        "component-snapshot-model",
                        "{noop}unused"
                );

        return new PayrollSnapshot(
                owner,
                LocalDate.of(2026, 8, 1),
                1,
                "RUB",
                100_000L,
                "HOURLY",
                LocalDate.of(2026, 8, 1),
                100_000L,
                null,
                0,
                0,
                480,
                480,
                0,
                0,
                0,
                0,
                0,
                0,
                480,
                480,
                800_000L,
                componentCount,
                componentEarnings,
                componentFingerprint,
                0,
                0L,
                0L,
                null,
                0,
                0,
                0L,
                0L,
                0L,
                null,
                0L,
                0L,
                800_000L + componentEarnings,
                Instant.parse(
                        "2026-09-01T00:00:00Z"
                ),
                Instant.parse(
                        "2026-09-01T00:01:00Z"
                ),
                "a".repeat(64)
        );
    }

    private PayrollSnapshot legacySnapshot() {
        AppUser owner =
                new AppUser(
                        "component-snapshot-legacy",
                        "{noop}unused"
                );

        /*
         * Existing ordinary-premium-aware constructor.
         * Generic component state must be supplied neutrally by compatibility
         * delegation.
         */
        return new PayrollSnapshot(
                owner,
                LocalDate.of(2026, 8, 1),
                1,
                "RUB",
                100_000L,
                "HOURLY",
                LocalDate.of(2026, 8, 1),
                100_000L,
                null,
                0,
                0,
                480,
                480,
                0,
                0,
                0,
                0,
                0,
                0,
                480,
                480,
                800_000L,
                0,
                0L,
                0L,
                null,
                0,
                0,
                0L,
                0L,
                0L,
                null,
                0L,
                0L,
                800_000L,
                Instant.parse(
                        "2026-09-01T00:00:00Z"
                ),
                Instant.parse(
                        "2026-09-01T00:01:00Z"
                ),
                "a".repeat(64)
        );
    }
}
