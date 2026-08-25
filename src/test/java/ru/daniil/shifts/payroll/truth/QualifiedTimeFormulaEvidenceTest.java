package ru.daniil.shifts.payroll.truth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.PayrollQuantityUnit;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualifiedTimeFormulaEvidenceTest {

    private static final String RESOURCE =
            "/payroll/truth/real-payroll-truth-v1.json";

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    @Test
    void realNightPremiumEvidenceOwnsMinuteQuantity() throws Exception {
        PayrollTruthEarning night =
                earning(
                        byPeriod(
                                read(),
                                "2026-03"
                        ),
                        "NIGHT_PREMIUM",
                        "11.03-12.03"
                );

        assertEquals(
                840L,
                night.resolvedQualifiedQuantity()
                        .value()
        );

        assertEquals(
                PayrollQuantityUnit.MINUTES,
                night.resolvedQualifiedQuantity()
                        .unit()
        );
    }

    @Test
    void holidayEvidenceDoesNotProveNativeHolidayOnlyMapping()
            throws Exception {

        PayrollTruthEarning holiday =
                earning(
                        byPeriod(
                                read(),
                                "2026-02"
                        ),
                        "HOLIDAY_PAY",
                        "февр. 2026"
                );

        assertEquals(
                480L,
                holiday.resolvedQualifiedQuantity()
                        .value()
        );

        assertEquals(
                PayrollQuantityUnit.MINUTES,
                holiday.resolvedQualifiedQuantity()
                        .unit()
        );

        /*
         * Source wording explicitly covers both public-holiday and weekend
         * work. The real payslip therefore does not prove that the current
         * native HOLIDAY flag alone is the complete quantity source.
         */
        assertTrue(
                holiday.sourceLabel()
                        .contains(
                                "праздничные и выходные"
                        )
        );
    }

    @Test
    void marchSplitDisprovesUniversalHarmfulEqualsBasePayMinutes()
            throws Exception {

        PayrollTruthCase march =
                byPeriod(
                        read(),
                        "2026-03"
                );

        PayrollTruthEarning base =
                earning(
                        march,
                        "BASE_PAY",
                        "01.03-10.03"
                );

        PayrollTruthEarning harmful =
                earning(
                        march,
                        "HARMFUL_CONDITIONS",
                        "01.03-10.03"
                );

        assertEquals(
                2880L,
                base.resolvedQualifiedQuantity()
                        .value()
        );

        assertEquals(
                2400L,
                harmful.resolvedQualifiedQuantity()
                        .value()
        );

        assertNotEquals(
                base.resolvedQualifiedQuantity(),
                harmful.resolvedQualifiedQuantity()
        );
    }

    private static PayrollTruthEarning earning(
            PayrollTruthCase truthCase,
            String semanticKey,
            String sourcePeriod
    ) {
        return truthCase.earnings()
                .stream()
                .filter(item ->
                        semanticKey.equals(
                                item.semanticKey()
                        )
                                && sourcePeriod.equals(
                                item.sourcePeriod()
                        )
                )
                .findFirst()
                .orElseThrow();
    }

    private static PayrollTruthCase byPeriod(
            PayrollTruthPack pack,
            String period
    ) {
        return pack.cases()
                .stream()
                .filter(item ->
                        period.equals(
                                item.period()
                        )
                )
                .findFirst()
                .orElseThrow();
    }

    private PayrollTruthPack read()
            throws Exception {

        try (
                InputStream input =
                        getClass()
                                .getResourceAsStream(
                                        RESOURCE
                                )
        ) {
            if (input == null) {
                throw new IllegalStateException(
                        "Missing truth resource: "
                                + RESOURCE
                );
            }

            return objectMapper.readValue(
                    input,
                    PayrollTruthPack.class
            );
        }
    }
}
