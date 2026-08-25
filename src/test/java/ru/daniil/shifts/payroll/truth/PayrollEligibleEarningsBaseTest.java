package ru.daniil.shifts.payroll.truth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.PayrollEarningBaseEligibility;
import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.service.PayrollEligibleEarningsBaseResolver;

import java.io.InputStream;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayrollEligibleEarningsBaseTest {

    private static final String RESOURCE =
            "/payroll/truth/real-payroll-truth-v1.json";

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    @Test
    void onlyProvenLocalBaseTargetsAreDefined() {
        assertEquals(
                Set.of(
                        PayrollEarningKind.HARMFUL_CONDITIONS,
                        PayrollEarningKind.MONTHLY_BONUS,
                        PayrollEarningKind.REGIONAL_COEFFICIENT
                ),
                PayrollEarningBaseEligibility
                        .definedTargets()
        );

        assertFalse(
                PayrollEarningBaseEligibility
                        .hasLocalBase(
                                PayrollEarningKind.COMBINATION
                        )
        );

        assertFalse(
                PayrollEarningBaseEligibility
                        .hasLocalBase(
                                PayrollEarningKind.ONE_TIME_BONUS
                        )
        );

        assertFalse(
                PayrollEarningBaseEligibility
                        .hasLocalBase(
                                PayrollEarningKind.MEDICAL_COMPENSATION
                        )
        );
    }

    @Test
    void exactMembershipMatchesRealPayrollEvidence() {
        assertEquals(
                Set.of(
                        PayrollEarningKind.BASE_PAY,
                        PayrollEarningKind.HOLIDAY_PAY
                ),
                PayrollEarningBaseEligibility
                        .eligibleKindsFor(
                                PayrollEarningKind.HARMFUL_CONDITIONS
                        )
        );

        assertEquals(
                Set.of(
                        PayrollEarningKind.BASE_PAY,
                        PayrollEarningKind.HOLIDAY_PAY,
                        PayrollEarningKind.HARMFUL_CONDITIONS,
                        PayrollEarningKind.NIGHT_PREMIUM,
                        PayrollEarningKind.COMBINATION
                ),
                PayrollEarningBaseEligibility
                        .eligibleKindsFor(
                                PayrollEarningKind.MONTHLY_BONUS
                        )
        );

        assertEquals(
                Set.of(
                        PayrollEarningKind.BASE_PAY,
                        PayrollEarningKind.HOLIDAY_PAY,
                        PayrollEarningKind.HARMFUL_CONDITIONS,
                        PayrollEarningKind.NIGHT_PREMIUM,
                        PayrollEarningKind.COMBINATION,
                        PayrollEarningKind.MONTHLY_BONUS,
                        PayrollEarningKind.ONE_TIME_BONUS
                ),
                PayrollEarningBaseEligibility
                        .eligibleKindsFor(
                                PayrollEarningKind.REGIONAL_COEFFICIENT
                        )
        );
    }

    @Test
    void everyEligibleSourceIsStrictlyUpstreamOfItsTarget() {
        for (PayrollEarningKind target
                : PayrollEarningBaseEligibility
                .definedTargets()) {

            for (PayrollEarningKind source
                    : PayrollEarningBaseEligibility
                    .eligibleKindsFor(
                            target
                    )) {

                assertTrue(
                        target.phase()
                                .canReadFrom(
                                        source.phase()
                                ),
                        source + " -> " + target
                );
            }
        }
    }

    @Test
    void upstreamPhaseDoesNotImplyEligibility() {
        assertTrue(
                PayrollEarningKind.HARMFUL_CONDITIONS
                        .phase()
                        .canReadFrom(
                                PayrollEarningKind.NIGHT_PREMIUM
                                        .phase()
                        )
        );

        assertFalse(
                PayrollEarningBaseEligibility
                        .isEligible(
                                PayrollEarningKind.HARMFUL_CONDITIONS,
                                PayrollEarningKind.NIGHT_PREMIUM
                        )
        );

        assertFalse(
                PayrollEarningBaseEligibility
                        .isEligible(
                                PayrollEarningKind.MONTHLY_BONUS,
                                PayrollEarningKind.ONE_TIME_BONUS
                        )
        );

        assertFalse(
                PayrollEarningBaseEligibility
                        .isEligible(
                                PayrollEarningKind.REGIONAL_COEFFICIENT,
                                PayrollEarningKind.MEDICAL_COMPENSATION
                        )
        );

        /*
         * Real vacation payroll reference:
         * vacation pay is a separate earning and does not feed any of the
         * observed current-month 4% / 40% / 15% bases.
         */
        assertFalse(
                PayrollEarningBaseEligibility
                        .isEligible(
                                PayrollEarningKind.HARMFUL_CONDITIONS,
                                PayrollEarningKind.VACATION_PAY
                        )
        );

        assertFalse(
                PayrollEarningBaseEligibility
                        .isEligible(
                                PayrollEarningKind.MONTHLY_BONUS,
                                PayrollEarningKind.VACATION_PAY
                        )
        );

        assertFalse(
                PayrollEarningBaseEligibility
                        .isEligible(
                                PayrollEarningKind.REGIONAL_COEFFICIENT,
                                PayrollEarningKind.VACATION_PAY
                        )
        );
    }

    @Test
    void combinationPayoutCanFeedDownstreamBaseWithoutLocalReferenceBase() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PayrollEarningBaseEligibility
                                .eligibleKindsFor(
                                        PayrollEarningKind.COMBINATION
                                )
        );

        PayrollEligibleEarningsBaseResolver.Result result =
                PayrollEligibleEarningsBaseResolver.resolve(
                        PayrollEarningKind.MONTHLY_BONUS,
                        List.of(
                                earning(
                                        PayrollEarningKind.BASE_PAY,
                                        6_054_800L
                                ),
                                earning(
                                        PayrollEarningKind.COMBINATION,
                                        801_960L
                                )
                        )
                );

        assertEquals(
                6_856_760L,
                result.totalAmountMinor()
        );

        assertEquals(
                List.of(
                        PayrollEarningKind.BASE_PAY,
                        PayrollEarningKind.COMBINATION
                ),
                result.includedEarnings()
                        .stream()
                        .map(
                                PayrollEligibleEarningsBaseResolver
                                        .Earning::kind
                        )
                        .toList()
        );
    }

    @Test
    void resolverPreservesSplitEligibleLinesAndExcludesOtherEarnings() {
        PayrollEligibleEarningsBaseResolver.Result result =
                PayrollEligibleEarningsBaseResolver.resolve(
                        PayrollEarningKind.HARMFUL_CONDITIONS,
                        List.of(
                                earning(
                                        PayrollEarningKind.BASE_PAY,
                                        100_000L
                                ),
                                earning(
                                        PayrollEarningKind.MEDICAL_COMPENSATION,
                                        999_999L
                                ),
                                earning(
                                        PayrollEarningKind.BASE_PAY,
                                        200_000L
                                ),
                                earning(
                                        PayrollEarningKind.HOLIDAY_PAY,
                                        50_000L
                                )
                        )
                );

        assertEquals(
                350_000L,
                result.totalAmountMinor()
        );

        assertEquals(
                List.of(
                        100_000L,
                        200_000L,
                        50_000L
                ),
                result.includedEarnings()
                        .stream()
                        .map(
                                PayrollEligibleEarningsBaseResolver
                                        .Earning::amountMinor
                        )
                        .toList()
        );
    }

    @Test
    void nineRealPayslipsResolveAllProvenEligibleBasesExactly()
            throws Exception {

        PayrollTruthPack pack = read();

        assertEquals(
                9,
                pack.cases().size()
        );

        for (PayrollTruthCase truthCase
                : pack.cases()) {

            List<PayrollEligibleEarningsBaseResolver.Earning>
                    earnings =
                    semanticEarnings(
                            truthCase
                    );

            long harmfulBase =
                    PayrollEligibleEarningsBaseResolver
                            .resolve(
                                    PayrollEarningKind.HARMFUL_CONDITIONS,
                                    earnings
                            )
                            .totalAmountMinor();

            long monthlyBase =
                    PayrollEligibleEarningsBaseResolver
                            .resolve(
                                    PayrollEarningKind.MONTHLY_BONUS,
                                    earnings
                            )
                            .totalAmountMinor();

            long regionalBase =
                    PayrollEligibleEarningsBaseResolver
                            .resolve(
                                    PayrollEarningKind.REGIONAL_COEFFICIENT,
                                    earnings
                            )
                            .totalAmountMinor();

            assertEquals(
                    percentHalfUp(
                            harmfulBase,
                            400
                    ),
                    sum(
                            truthCase,
                            PayrollEarningKind.HARMFUL_CONDITIONS
                    ),
                    truthCase.period()
                            + " harmful"
            );

            assertEquals(
                    percentHalfUp(
                            monthlyBase,
                            4000
                    ),
                    sum(
                            truthCase,
                            PayrollEarningKind.MONTHLY_BONUS
                    ),
                    truthCase.period()
                            + " monthly"
            );

            assertEquals(
                    percentHalfUp(
                            regionalBase,
                            1500
                    ),
                    sum(
                            truthCase,
                            PayrollEarningKind.REGIONAL_COEFFICIENT
                    ),
                    truthCase.period()
                            + " regional"
            );
        }
    }

    @Test
    void unsupportedTargetsNegativeMoneyAndOverflowFailClosed() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PayrollEligibleEarningsBaseResolver
                                .resolve(
                                        PayrollEarningKind.COMBINATION,
                                        List.of()
                                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        earning(
                                PayrollEarningKind.BASE_PAY,
                                -1L
                        )
        );

        assertThrows(
                ArithmeticException.class,
                () ->
                        PayrollEligibleEarningsBaseResolver
                                .resolve(
                                        PayrollEarningKind.HARMFUL_CONDITIONS,
                                        List.of(
                                                earning(
                                                        PayrollEarningKind.BASE_PAY,
                                                        Long.MAX_VALUE
                                                ),
                                                earning(
                                                        PayrollEarningKind.HOLIDAY_PAY,
                                                        1L
                                                )
                                        )
                                )
        );
    }

    private static PayrollEligibleEarningsBaseResolver.Earning earning(
            PayrollEarningKind kind,
            long amountMinor
    ) {
        return new PayrollEligibleEarningsBaseResolver.Earning(
                kind,
                amountMinor
        );
    }

    private static List<
            PayrollEligibleEarningsBaseResolver.Earning
            > semanticEarnings(
            PayrollTruthCase truthCase
    ) {
        return truthCase.earnings()
                .stream()
                .map(line ->
                        earning(
                                PayrollEarningKind.valueOf(
                                        line.semanticKey()
                                ),
                                line.amountMinor()
                        )
                )
                .toList();
    }

    private static long sum(
            PayrollTruthCase truthCase,
            PayrollEarningKind kind
    ) {
        return truthCase.earnings()
                .stream()
                .filter(line ->
                        kind.name()
                                .equals(
                                        line.semanticKey()
                                )
                )
                .mapToLong(
                        PayrollTruthEarning::amountMinor
                )
                .sum();
    }

    private static long percentHalfUp(
            long baseMinor,
            int rateBps
    ) {
        return Math.floorDiv(
                Math.addExact(
                        Math.multiplyExact(
                                baseMinor,
                                rateBps
                        ),
                        5_000L
                ),
                10_000L
        );
    }

    private PayrollTruthPack read()
            throws Exception {

        try (InputStream input =
                     getClass()
                             .getResourceAsStream(
                                     RESOURCE
                             )) {

            if (input == null) {
                throw new IllegalStateException(
                        "Missing test resource: "
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
