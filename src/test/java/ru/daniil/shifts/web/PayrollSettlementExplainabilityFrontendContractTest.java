package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PayrollSettlementExplainabilityFrontendContractTest {

    private static final Path COMPONENT =
            Path.of(
                    "frontend/src/features/payroll/components/"
                            + "PayrollWorkspace.vue"
            );

    @Test
    void payrollExplainsBankFirstBaseAndExplicitSettlementMoney()
            throws Exception {

        String component =
                Files.readString(
                        COMPONENT,
                        StandardCharsets.UTF_8
                );

        for (String field : new String[] {
                "hourlyBasePayableMinutes",
                "settlementCount",
                "settlementMinutes",
                "settlementBasePayMinor",
                "settlementPremiumPayMinor",
                "settlementPayMinor",
                "settlementPricingReady",
                "settlementPricingBlockingReason"
        }) {
            assertTrue(
                    component.contains(field),
                    field
            );
        }

        for (String id : new String[] {
                "payrollHourlyBasePayable",
                "payrollSettlementBreakdown",
                "payrollSettlementTotal",
                "payrollSettlementBase",
                "payrollSettlementPremium",
                "payrollSettlementPricingStatus",
                "payrollGrandTotal"
        }) {
            assertTrue(
                    component.contains(
                            "id=\"" + id + "\""
                    ),
                    id
            );
        }

        assertTrue(
                component.contains(
                        "PAY_PRICING_PROVENANCE_REQUIRED"
                )
        );

        assertTrue(
                component.contains(
                        "PAY_PRICING_RULES_REQUIRED"
                )
        );

        assertTrue(
                component.contains(
                        "PAY_PRICING_CURRENCY_MISMATCH"
                )
        );

        assertTrue(
                component.contains(
                        "PAYROLL_SETTLEMENT_CURRENCY_MISMATCH"
                )
        );

        /*
         * Settlement month is payout identity, not necessarily source-work
         * month. UI must never present settlement minutes as a subtraction
         * from the current month's ordinary payable time.
         */
        assertFalse(
                component.contains(
                        "payableMinutes-preview.hourlyBasePayableMinutes"
                )
        );

        assertFalse(
                component.contains(
                        "payableMinutes - preview.hourlyBasePayableMinutes"
                )
        );
    }

    @Test
    void immutableRevisionKeepsFingerprintAsTechnicalDetailNotPrimaryMoneyUi()
            throws Exception {

        String component =
                Files.readString(
                        COMPONENT,
                        StandardCharsets.UTF_8
                );

        assertTrue(
                component.contains(
                        "item.settlementPricingFingerprint"
                )
        );

        assertTrue(
                component.contains(
                        "<details v-if=\"item.settlementPricingFingerprint\""
                )
        );

        assertTrue(
                component.contains(
                        "payrollFingerprint"
                )
        );

        assertFalse(
                component.contains(
                        "Premium multipliers come in the next Payroll step."
                )
        );

        assertFalse(
                component.contains(
                        "Коэффициенты доплат появятся следующим шагом Payroll."
                )
        );

        assertFalse(
                component.contains(
                        "Доплаты за переработку, ночь и праздник будут отдельной классификацией следующего шага Payroll."
                )
        );
    }
}
