package ru.daniil.shifts.payroll.truth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.model.PayrollEarningPhase;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PayrollEarningTaxonomyTest {

    private static final String REAL_TRUTH_RESOURCE =
            "/payroll/truth/real-payroll-truth-v1.json";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void orderedPhaseContractIsExplicitAndStable() {
        assertEquals(
                List.of(
                        PayrollEarningPhase.BASE_PAY,
                        PayrollEarningPhase.TIME_PREMIUM,
                        PayrollEarningPhase.WORK_ALLOWANCE,
                        PayrollEarningPhase.EXTERNAL_EPISODIC_ALLOWANCE,
                        PayrollEarningPhase.PERFORMANCE_BONUS,
                        PayrollEarningPhase.GROSS_COEFFICIENT,
                        PayrollEarningPhase.OTHER_EARNING
                ),
                List.of(PayrollEarningPhase.values())
        );
    }

    @Test
    void semanticKindsHaveExplicitMachineOwnedPhases() {
        Map<PayrollEarningKind, PayrollEarningPhase> expected =
                Map.ofEntries(
                        Map.entry(
                                PayrollEarningKind.BASE_PAY,
                                PayrollEarningPhase.BASE_PAY
                        ),
                        Map.entry(
                                PayrollEarningKind.HOLIDAY_PAY,
                                PayrollEarningPhase.BASE_PAY
                        ),
                        Map.entry(
                                PayrollEarningKind.NIGHT_PREMIUM,
                                PayrollEarningPhase.TIME_PREMIUM
                        ),
                        Map.entry(
                                PayrollEarningKind.HARMFUL_CONDITIONS,
                                PayrollEarningPhase.WORK_ALLOWANCE
                        ),
                        Map.entry(
                                PayrollEarningKind.COMBINATION,
                                PayrollEarningPhase.EXTERNAL_EPISODIC_ALLOWANCE
                        ),
                        Map.entry(
                                PayrollEarningKind.MONTHLY_BONUS,
                                PayrollEarningPhase.PERFORMANCE_BONUS
                        ),
                        Map.entry(
                                PayrollEarningKind.ONE_TIME_BONUS,
                                PayrollEarningPhase.PERFORMANCE_BONUS
                        ),
                        Map.entry(
                                PayrollEarningKind.REGIONAL_COEFFICIENT,
                                PayrollEarningPhase.GROSS_COEFFICIENT
                        ),
                        Map.entry(
                                PayrollEarningKind.MEDICAL_COMPENSATION,
                                PayrollEarningPhase.OTHER_EARNING
                        )
                );

        assertEquals(
                PayrollEarningKind.values().length,
                expected.size()
        );

        for (PayrollEarningKind kind : PayrollEarningKind.values()) {
            assertEquals(
                    expected.get(kind),
                    kind.phase(),
                    kind.name()
            );
        }

        assertEquals(
                PayrollEarningKind.BASE_PAY.phase(),
                PayrollEarningKind.HOLIDAY_PAY.phase()
        );
        assertNotEquals(
                PayrollEarningKind.BASE_PAY,
                PayrollEarningKind.HOLIDAY_PAY
        );

        assertEquals(
                PayrollEarningKind.MONTHLY_BONUS.phase(),
                PayrollEarningKind.ONE_TIME_BONUS.phase()
        );
        assertNotEquals(
                PayrollEarningKind.MONTHLY_BONUS,
                PayrollEarningKind.ONE_TIME_BONUS
        );
    }

    @Test
    void dependencyContractAllowsOnlyStrictlyUpstreamPhases() {
        for (PayrollEarningPhase target
                : PayrollEarningPhase.values()) {

            for (PayrollEarningPhase source
                    : PayrollEarningPhase.values()) {

                assertEquals(
                        source.ordinal() < target.ordinal(),
                        target.canReadFrom(source),
                        source + " -> " + target
                );
            }

            assertFalse(target.canReadFrom(null));
        }
    }

    @Test
    void realTruthSemanticKeysAreCoveredByMachineOwnedKinds()
            throws Exception {

        PayrollTruthPack pack;

        try (InputStream input = getClass()
                .getResourceAsStream(REAL_TRUTH_RESOURCE)) {

            if (input == null) {
                throw new IllegalStateException(
                        "Missing truth resource "
                                + REAL_TRUTH_RESOURCE
                );
            }

            pack = objectMapper.readValue(
                    input,
                    PayrollTruthPack.class
            );
        }

        Set<String> truthKeys = Set.copyOf(
                pack.cases()
                        .stream()
                        .flatMap(
                                truthCase ->
                                        truthCase.earnings().stream()
                        )
                        .map(PayrollTruthEarning::semanticKey)
                        .toList()
        );

        Set<String> machineKeys = Set.copyOf(
                Arrays.stream(PayrollEarningKind.values())
                        .map(Enum::name)
                        .toList()
        );

        assertEquals(machineKeys, truthKeys);
    }
}
