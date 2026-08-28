package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.model.PayrollSnapshot;
import ru.daniil.shifts.model.PayrollQualifiedQuantity;

import java.time.LocalDate;
import java.util.List;
import ru.daniil.shifts.repo.PayrollSnapshotEarningLineRepository;
import ru.daniil.shifts.repo.PayrollSnapshotEarningManifestRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayrollSemanticFreezeServiceTest {

    private final PayrollSnapshotEarningLineRepository lines =
            mock(
                    PayrollSnapshotEarningLineRepository.class
            );

    private final PayrollSnapshotEarningManifestRepository manifests =
            mock(
                    PayrollSnapshotEarningManifestRepository.class
            );

    private final PayrollSemanticFreezeService service =
            new PayrollSemanticFreezeService(
                    lines,
                    manifests
            );

    private final PayrollSnapshot snapshot =
            mock(
                    PayrollSnapshot.class
            );

    @Test
    void baseOnlyFreezePersistsCompleteMachineOwnedHistory() {
        var result =
                service.freeze(
                        snapshot,
                        new PayrollSemanticFreezeProjection.Source(
                                6_054_800L,
                                0L,
                                0L,
                                0L,
                                0L
                        )
                );

        assertEquals(
                1,
                result.lines().size()
        );

        assertEquals(
                PayrollEarningKind.BASE_PAY.name(),
                result.lines()
                        .get(
                                0
                        )
                        .getEarningKind()
        );

        assertEquals(
                6_054_800L,
                result.lines()
                        .get(
                                0
                        )
                        .getAmountMinor()
        );

        assertTrue(
                result.manifest()
                        .isComplete()
        );

        assertEquals(
                0L,
                result.manifest()
                        .getUnclassifiedAmountMinor()
        );

        assertEquals(
                PayrollSemanticEarningFingerprint.calculate(
                        result.lines()
                ),
                result.manifest()
                        .getFingerprint()
        );

        verify(lines)
                .saveAll(
                        result.lines()
                );

        verify(manifests)
                .saveAndFlush(
                        result.manifest()
                );
    }

    @Test
    void unclassifiedMoneyPersistsIncompleteManifestWithoutGuessingKind() {
        var result =
                service.freeze(
                        snapshot,
                        new PayrollSemanticFreezeProjection.Source(
                                1_000L,
                                100L,
                                200L,
                                300L,
                                400L
                        )
                );

        assertEquals(
                1,
                result.lines().size()
        );

        assertEquals(
                PayrollEarningKind.BASE_PAY.name(),
                result.lines()
                        .get(
                                0
                        )
                        .getEarningKind()
        );

        assertFalse(
                result.manifest()
                        .isComplete()
        );

        assertEquals(
                1_000L,
                result.manifest()
                        .getAmountMinor()
        );

        assertEquals(
                1_000L,
                result.manifest()
                        .getUnclassifiedAmountMinor()
        );

        verify(lines)
                .saveAll(
                        result.lines()
                );

        verify(manifests)
                .saveAndFlush(
                        result.manifest()
                );
    }

    @Test
    void zeroEarningMonthPersistsCompleteEmptyFreeze() {
        var result =
                service.freeze(
                        snapshot,
                        new PayrollSemanticFreezeProjection.Source(
                                0L,
                                0L,
                                0L,
                                0L,
                                0L
                        )
                );

        assertTrue(
                result.lines()
                        .isEmpty()
        );

        assertTrue(
                result.manifest()
                        .isComplete()
        );

        assertEquals(
                0L,
                result.manifest()
                        .getAmountMinor()
        );

        assertEquals(
                0L,
                result.manifest()
                        .getUnclassifiedAmountMinor()
        );

        verify(lines, never())
                .saveAll(
                        any()
                );

        verify(manifests)
                .saveAndFlush(
                        result.manifest()
                );
    }
    @Test
    void freezePersistsExplicitSourceLineQuantityAndPeriods() {
        var result =
                service.freeze(
                        snapshot,
                        new PayrollSemanticFreezeProjection.Source(
                                2_000L,
                                0L,
                                0L,
                                0L,
                                0L,
                                null,
                                0L,
                                List.of(
                                        new PayrollSemanticFreezeProjection.SemanticLine(
                                                PayrollEarningKind.BASE_PAY,
                                                2_000L,
                                                PayrollQualifiedQuantity.minutes(
                                                        4_800L
                                                ),
                                                LocalDate.of(
                                                        2026,
                                                        3,
                                                        1
                                                ),
                                                LocalDate.of(
                                                        2026,
                                                        3,
                                                        10
                                                ),
                                                LocalDate.of(
                                                        2026,
                                                        3,
                                                        1
                                                ),
                                                LocalDate.of(
                                                        2026,
                                                        3,
                                                        10
                                                )
                                        )
                                ),
                                null
                        )
                );

        var frozen =
                result.lines()
                        .get(0);

        assertEquals(
                4_800L,
                frozen.getQualifiedQuantityValue()
        );

        assertEquals(
                "MINUTES",
                frozen.getQualifiedQuantityUnit()
        );

        assertEquals(
                LocalDate.of(
                        2026,
                        3,
                        1
                ),
                frozen.getEarningPeriodFrom()
        );

        assertEquals(
                LocalDate.of(
                        2026,
                        3,
                        10
                ),
                frozen.getCoverageTo()
        );

        assertEquals(
                PayrollSemanticEarningFingerprint.calculate(
                        result.lines()
                ),
                result.manifest()
                        .getFingerprint()
        );
    }


}
