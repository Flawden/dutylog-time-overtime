package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.model.PayrollSnapshot;
import ru.daniil.shifts.model.PayrollSnapshotBonusAverageEarningsFact;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PayrollBonusAverageEarningsFingerprintTest {

    private final PayrollSnapshot snapshot = mock(PayrollSnapshot.class);

    @Test
    void unknownBooleanIsDifferentFromProvenFalse() {
        PayrollSnapshotBonusAverageEarningsFact unknown = fact(0, null);
        PayrollSnapshotBonusAverageEarningsFact provenFalse = fact(0, false);

        assertNotEquals(
                PayrollBonusAverageEarningsFingerprint.calculate(1, List.of(unknown)),
                PayrollBonusAverageEarningsFingerprint.calculate(1, List.of(provenFalse))
        );
    }

    @Test
    void sourceFactCountParticipatesEvenWhenMissingFactsMakeManifestIncomplete() {
        String oneMissing =
                PayrollBonusAverageEarningsFingerprint.calculate(
                        2,
                        List.of(fact(0, null))
                );

        String twoMissing =
                PayrollBonusAverageEarningsFingerprint.calculate(
                        3,
                        List.of(fact(0, null))
                );

        assertNotEquals(oneMissing, twoMissing);
        assertEquals(64, oneMissing.length());
    }

    @Test
    void factsMustBeCanonicalContiguousOrder() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PayrollBonusAverageEarningsFingerprint.calculate(
                        1,
                        List.of(fact(1, null))
                )
        );
    }

    private PayrollSnapshotBonusAverageEarningsFact fact(
            int index,
            Boolean annualResult
    ) {
        return new PayrollSnapshotBonusAverageEarningsFact(
                snapshot,
                index,
                100L + index,
                200L + index,
                300L + index,
                PayrollEarningKind.MONTHLY_BONUS,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                10_000L,
                "RUB",
                "MONTHLY_KPI",
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                annualResult,
                null,
                null
        );
    }
}
