package ru.daniil.shifts.payroll.truth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayrollTruthPackTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void foundationFixtureLoadsAndPreservesUnknownExternalBase() throws Exception {
        PayrollTruthPack pack = read("/payroll/truth/foundation.json");

        assertDoesNotThrow(() -> PayrollTruthPackValidator.validate(pack));
        assertEquals("1", pack.schemaVersion());
        assertEquals(1, pack.cases().size());

        PayrollTruthCase truthCase = pack.cases().get(0);
        PayrollTruthEarning combination = truthCase.earnings().get(0);

        assertEquals("synthetic-external-base-boundary", truthCase.sourceId());
        assertEquals(PayrollTruthProvenance.FACT, combination.provenance());
        assertEquals(PayrollTruthReferenceBase.UNKNOWN, combination.referenceBase());
        assertEquals(123456L, combination.amountMinor());
        assertEquals(2500, combination.agreedRateBps());
    }

    @Test
    void provenFormulaCannotHideAnUnknownCalculationBase() {
        PayrollTruthEarning invalid = new PayrollTruthEarning(
                "COMBINATION",
                12_345L,
                null,
                2500,
                PayrollTruthReferenceBase.UNKNOWN,
                null,
                PayrollTruthProvenance.PROVEN,
                "Invalid on purpose"
        );

        PayrollTruthCase truthCase = new PayrollTruthCase(
                "invalid-proven-unknown-base",
                "2026-01",
                "RUB",
                List.of(),
                List.of(invalid),
                12_345L,
                List.of()
        );

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> PayrollTruthPackValidator.validate(
                        new PayrollTruthPack("1", List.of(truthCase))
                )
        );

        assertTrue(error.getMessage().contains("UNKNOWN reference base"));
    }

    @Test
    void unknownFactCannotContainAGuessedNumericValue() {
        PayrollTruthFact guessed = new PayrollTruthFact(
                "external_nominal_salary",
                493_824L,
                null,
                null,
                PayrollTruthProvenance.UNKNOWN,
                "Invalid guessed reconstruction"
        );

        PayrollTruthCase truthCase = new PayrollTruthCase(
                "invalid-guessed-fact",
                "2026-01",
                "RUB",
                List.of(guessed),
                List.of(),
                null,
                List.of()
        );

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> PayrollTruthPackValidator.validate(
                        new PayrollTruthPack("1", List.of(truthCase))
                )
        );

        assertTrue(error.getMessage().contains("UNKNOWN fact"));
    }

    @Test
    void explicitExternalReferenceAmountCanSupportAProvenFormula() {
        PayrollTruthEarning earning = new PayrollTruthEarning(
                "SYNTHETIC_EXTERNAL_BASE",
                25_000L,
                null,
                2500,
                PayrollTruthReferenceBase.EXTERNAL_REFERENCE_AMOUNT,
                100_000L,
                PayrollTruthProvenance.PROVEN,
                "Exact synthetic arithmetic"
        );

        PayrollTruthCase truthCase = new PayrollTruthCase(
                "synthetic-proven-external-base",
                "2026-01",
                "RUB",
                List.of(),
                List.of(earning),
                25_000L,
                List.of()
        );

        assertDoesNotThrow(
                () -> PayrollTruthPackValidator.validate(
                        new PayrollTruthPack("1", List.of(truthCase))
                )
        );
    }

    private PayrollTruthPack read(String resource) throws Exception {
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing test resource: " + resource);
            }
            return objectMapper.readValue(input, PayrollTruthPack.class);
        }
    }
}
