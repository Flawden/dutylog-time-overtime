package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.PayrollEarningPhase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayrollEarningsPipelineTest {

    @Test
    void canonicalPhaseOrderPreservesLegacyMoneyExactly() {
        long basePay = 800_000L;
        long settlementPay = 150_000L;
        long ordinaryPremiumPay = 20_000L;
        long componentEarnings = 32_000L;

        long legacyTotal =
                Math.addExact(
                        Math.addExact(
                                Math.addExact(
                                        basePay,
                                        settlementPay
                                ),
                                ordinaryPremiumPay
                        ),
                        componentEarnings
                );

        PayrollEarningsPipeline.Result result =
                PayrollEarningsPipeline.assemble(
                        List.of(
                                new PayrollEarningsPipeline.Earning(
                                        PayrollEarningPhase.OTHER_EARNING,
                                        settlementPay
                                ),
                                new PayrollEarningsPipeline.Earning(
                                        PayrollEarningPhase.BASE_PAY,
                                        basePay
                                ),
                                new PayrollEarningsPipeline.Earning(
                                        PayrollEarningPhase.OTHER_EARNING,
                                        componentEarnings
                                ),
                                new PayrollEarningsPipeline.Earning(
                                        PayrollEarningPhase.TIME_PREMIUM,
                                        ordinaryPremiumPay
                                )
                        )
                );

        assertEquals(
                legacyTotal,
                result.totalAmountMinor()
        );

        assertEquals(
                List.of(
                        PayrollEarningPhase.BASE_PAY,
                        PayrollEarningPhase.TIME_PREMIUM,
                        PayrollEarningPhase.OTHER_EARNING,
                        PayrollEarningPhase.OTHER_EARNING
                ),
                result.orderedEarnings()
                        .stream()
                        .map(PayrollEarningsPipeline.Earning::phase)
                        .toList()
        );

        assertEquals(
                List.of(
                        basePay,
                        ordinaryPremiumPay,
                        settlementPay,
                        componentEarnings
                ),
                result.orderedEarnings()
                        .stream()
                        .map(
                                PayrollEarningsPipeline.Earning::amountMinor
                        )
                        .toList()
        );
    }

    @Test
    void independentSamePhaseEarningsKeepStableOrder() {
        PayrollEarningsPipeline.Result result =
                PayrollEarningsPipeline.assemble(
                        List.of(
                                new PayrollEarningsPipeline.Earning(
                                        PayrollEarningPhase.OTHER_EARNING,
                                        150_000L
                                ),
                                new PayrollEarningsPipeline.Earning(
                                        PayrollEarningPhase.OTHER_EARNING,
                                        32_000L
                                )
                        )
                );

        assertEquals(
                182_000L,
                result.totalAmountMinor()
        );

        assertEquals(
                List.of(
                        150_000L,
                        32_000L
                ),
                result.orderedEarnings()
                        .stream()
                        .map(
                                PayrollEarningsPipeline.Earning::amountMinor
                        )
                        .toList()
        );
    }

    @Test
    void overflowFailsClosed() {
        assertThrows(
                ArithmeticException.class,
                () -> PayrollEarningsPipeline.assemble(
                        List.of(
                                new PayrollEarningsPipeline.Earning(
                                        PayrollEarningPhase.BASE_PAY,
                                        Long.MAX_VALUE
                                ),
                                new PayrollEarningsPipeline.Earning(
                                        PayrollEarningPhase.TIME_PREMIUM,
                                        1L
                                )
                        )
                )
        );
    }

    @Test
    void payrollServiceUsesOrderedPipelineBeforeManualAdjustments()
            throws Exception {

        String payroll =
                Files.readString(
                        Path.of(
                                "src/main/java/ru/daniil/shifts/service/"
                                        + "PayrollService.java"
                        )
                );

        int earningsSubtotalStart =
                payroll.indexOf(
                        "long earningsSubtotal ="
                );

        int totalPayStart =
                payroll.indexOf(
                        "long totalPay =",
                        earningsSubtotalStart
                );

        int previewReturnStart =
                payroll.indexOf(
                        "return new PayrollPreviewDto(",
                        totalPayStart
                );

        assertTrue(earningsSubtotalStart >= 0);
        assertTrue(totalPayStart > earningsSubtotalStart);
        assertTrue(previewReturnStart > totalPayStart);

        String assembly =
                payroll.substring(
                        earningsSubtotalStart,
                        totalPayStart
                );

        assertTrue(
                assembly.contains(
                        "assembleEarnings("
                )
        );

        assertTrue(
                payroll.contains(
                        "PayrollEarningsPipeline.assemble("
                )
        );

        for (String marker : new String[] {
                "PayrollEarningPhase.BASE_PAY",
                "basePay",
                "PayrollEarningPhase.TIME_PREMIUM",
                "ordinaryPremiumPay",
                "PayrollEarningPhase.OTHER_EARNING",
                "settlementPay",
                "componentEarnings"
        }) {
            assertTrue(
                    assembly.contains(marker),
                    marker
            );
        }

        assertFalse(
                assembly.contains(
                        "safeMoney("
                ),
                "Legacy hardcoded earnings subtotal must be gone"
        );

        assertFalse(
                assembly.contains(
                        "displayName"
                ),
                "Assembly must never infer semantics from displayName"
        );

        String totalPay =
                payroll.substring(
                        totalPayStart,
                        previewReturnStart
                );

        assertTrue(
                totalPay.contains(
                        "earningsSubtotal"
                )
        );

        assertTrue(
                totalPay.contains(
                        "additions"
                )
        );

        assertTrue(
                totalPay.contains(
                        "deductions"
                )
        );
    }
}
