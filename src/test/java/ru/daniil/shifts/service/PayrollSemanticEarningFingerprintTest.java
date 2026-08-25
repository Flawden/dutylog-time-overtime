package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.model.PayrollQualifiedQuantity;
import ru.daniil.shifts.model.PayrollSnapshot;
import ru.daniil.shifts.model.PayrollSnapshotEarningLine;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class PayrollSemanticEarningFingerprintTest {

    private final PayrollSnapshot snapshot =
            mock(
                    PayrollSnapshot.class
            );

    @Test
    void emptySemanticSetHasStableSha256Identity() {
        String first =
                PayrollSemanticEarningFingerprint.calculate(
                        List.of()
                );

        String second =
                PayrollSemanticEarningFingerprint.calculate(
                        List.of()
                );

        assertEquals(
                first,
                second
        );

        assertEquals(
                64,
                first.length()
        );
    }

    @Test
    void everyHistoricalSemanticFieldParticipatesInFingerprint() {
        PayrollSnapshotEarningLine base =
                new PayrollSnapshotEarningLine(
                        snapshot,
                        0,
                        PayrollEarningKind.VACATION_PAY,
                        5_160_988L,
                        PayrollQualifiedQuantity.calendarDays(
                                14
                        ),
                        null,
                        null,
                        LocalDate.of(
                                2026,
                                6,
                                1
                        ),
                        LocalDate.of(
                                2026,
                                6,
                                14
                        )
                );

        PayrollSnapshotEarningLine changedCoverage =
                new PayrollSnapshotEarningLine(
                        snapshot,
                        0,
                        PayrollEarningKind.VACATION_PAY,
                        5_160_988L,
                        PayrollQualifiedQuantity.calendarDays(
                                14
                        ),
                        null,
                        null,
                        LocalDate.of(
                                2026,
                                6,
                                2
                        ),
                        LocalDate.of(
                                2026,
                                6,
                                15
                        )
                );

        assertNotEquals(
                PayrollSemanticEarningFingerprint.calculate(
                        List.of(
                                base
                        )
                ),
                PayrollSemanticEarningFingerprint.calculate(
                        List.of(
                                changedCoverage
                        )
                )
        );
    }

    @Test
    void lineOrderMustBeContiguousAndCanonical() {
        PayrollSnapshotEarningLine line =
                new PayrollSnapshotEarningLine(
                        snapshot,
                        1,
                        PayrollEarningKind.BASE_PAY,
                        1L,
                        null,
                        null,
                        null,
                        null,
                        null
                );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PayrollSemanticEarningFingerprint.calculate(
                                List.of(
                                        line
                                )
                        )
        );
    }
}
