package ru.daniil.shifts.payroll.truth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealPayrollTruthPackTest {

    private static final String RESOURCE =
            "/payroll/truth/real-payroll-truth-v1.json";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void nineRealPayslipsLoadAsValidTruthCases() throws Exception {
        PayrollTruthPack pack = read();

        PayrollTruthPackValidator.validate(pack);

        assertEquals("1", pack.schemaVersion());
        assertEquals(9, pack.cases().size());

        assertEquals(
                List.of(
                        "2025-11",
                        "2025-12",
                        "2026-01",
                        "2026-02",
                        "2026-03",
                        "2026-04",
                        "2026-05",
                        "2026-06",
                        "2026-07"
                ),
                pack.cases()
                        .stream()
                        .map(PayrollTruthCase::period)
                        .toList()
        );
    }

    @Test
    void everyMonthReconcilesSourceGrossToTheKopeck() throws Exception {
        for (PayrollTruthCase truthCase : read().cases()) {
            long lineTotal = truthCase.earnings()
                    .stream()
                    .mapToLong(PayrollTruthEarning::amountMinor)
                    .sum();

            assertEquals(
                    truthCase.expectedGrossMinor().longValue(),
                    lineTotal,
                    truthCase.period()
            );
        }
    }

    @Test
    void harmfulConditionsMatchFourPercentOfObservedEligibleBase()
            throws Exception {

        for (PayrollTruthCase truthCase : read().cases()) {
            long base =
                    sum(truthCase, "BASE_PAY")
                            + sum(truthCase, "HOLIDAY_PAY");

            assertEquals(
                    percentHalfUp(base, 400),
                    sum(truthCase, "HARMFUL_CONDITIONS"),
                    truthCase.period()
            );
        }
    }

    @Test
    void monthlyBonusMatchesFortyPercentOfObservedEligibleBase()
            throws Exception {

        Set<String> eligible = Set.of(
                "BASE_PAY",
                "HOLIDAY_PAY",
                "HARMFUL_CONDITIONS",
                "NIGHT_PREMIUM",
                "COMBINATION"
        );

        for (PayrollTruthCase truthCase : read().cases()) {
            long base = truthCase.earnings()
                    .stream()
                    .filter(line -> eligible.contains(line.semanticKey()))
                    .mapToLong(PayrollTruthEarning::amountMinor)
                    .sum();

            assertEquals(
                    percentHalfUp(base, 4000),
                    sum(truthCase, "MONTHLY_BONUS"),
                    truthCase.period()
            );
        }
    }

    @Test
    void regionalCoefficientMatchesFifteenPercentOfObservedEligibleBase()
            throws Exception {

        Set<String> preBonusEligible = Set.of(
                "BASE_PAY",
                "HOLIDAY_PAY",
                "HARMFUL_CONDITIONS",
                "NIGHT_PREMIUM",
                "COMBINATION"
        );

        for (PayrollTruthCase truthCase : read().cases()) {
            long base = truthCase.earnings()
                    .stream()
                    .filter(line ->
                            preBonusEligible.contains(line.semanticKey())
                                    || "MONTHLY_BONUS".equals(line.semanticKey())
                                    || "ONE_TIME_BONUS".equals(line.semanticKey())
                    )
                    .mapToLong(PayrollTruthEarning::amountMinor)
                    .sum();

            assertEquals(
                    percentHalfUp(base, 1500),
                    sum(truthCase, "REGIONAL_COEFFICIENT"),
                    truthCase.period()
            );
        }
    }

    @Test
    void regionalSourcePeriodIsExplicitFactAndNotARewriteOfEligibleBaseSplits()
            throws Exception {

        PayrollTruthPack pack = read();
        PayrollTruthCase november = byPeriod(pack, "2025-11");
        PayrollTruthCase march = byPeriod(pack, "2026-03");

        List<PayrollTruthEarning> novemberRegional = november.earnings()
                .stream()
                .filter(line -> "REGIONAL_COEFFICIENT".equals(line.semanticKey()))
                .toList();

        List<PayrollTruthEarning> marchRegional = march.earnings()
                .stream()
                .filter(line -> "REGIONAL_COEFFICIENT".equals(line.semanticKey()))
                .toList();

        assertEquals(1, novemberRegional.size());
        assertEquals("19.11-30.11", novemberRegional.get(0).sourcePeriod());
        assertEquals(PayrollTruthProvenance.FACT, novemberRegional.get(0).provenance());

        assertEquals(3, count(march, "BASE_PAY"),
                "March BASE_PAY has three explicit source splits");
        assertEquals(1, marchRegional.size(),
                "Observed REGIONAL remains one source line, not three synthetic splits");
        assertEquals("март 2026", marchRegional.get(0).sourcePeriod());
        assertEquals(PayrollTruthProvenance.FACT, marchRegional.get(0).provenance());
    }

    @Test
    void negativeEligibilityCasesRemainExplicit() throws Exception {
        PayrollTruthPack pack = read();

        PayrollTruthCase november = byPeriod(pack, "2025-11");
        PayrollTruthCase february = byPeriod(pack, "2026-02");
        PayrollTruthCase july = byPeriod(pack, "2026-07");

        assertEquals(
                157800L,
                sum(november, "MEDICAL_COMPENSATION")
        );

        long novemberRegionalBase =
                sum(november, "BASE_PAY")
                        + sum(november, "HARMFUL_CONDITIONS")
                        + sum(november, "MONTHLY_BONUS");

        assertEquals(
                sum(november, "REGIONAL_COEFFICIENT"),
                percentHalfUp(novemberRegionalBase, 1500)
        );

        assertTrue(
                hasAssertion(
                        november,
                        "medical_excluded_from_regional_base"
                )
        );

        assertEquals(962700L, sum(february, "ONE_TIME_BONUS"));
        assertEquals(1905000L, sum(july, "ONE_TIME_BONUS"));

        assertTrue(
                hasAssertion(
                        february,
                        "one_time_bonus_excluded_from_monthly_bonus_base"
                )
        );

        assertTrue(
                hasAssertion(
                        february,
                        "one_time_bonus_included_in_regional_base"
                )
        );

        assertTrue(
                hasAssertion(
                        july,
                        "one_time_bonus_excluded_from_monthly_bonus_base"
                )
        );

        assertTrue(
                hasAssertion(
                        july,
                        "one_time_bonus_included_in_regional_base"
                )
        );
    }

    @Test
    void combinationEpisodesPreserveKnownFactsAndUnknownExternalBase()
            throws Exception {

        PayrollTruthPack pack = read();

        List<PayrollTruthEarning> episodes = pack.cases()
                .stream()
                .flatMap(truthCase -> truthCase.earnings().stream())
                .filter(line -> "COMBINATION".equals(line.semanticKey()))
                .toList();

        assertEquals(4, episodes.size());

        for (PayrollTruthEarning episode : episodes) {
            assertEquals(
                    PayrollTruthProvenance.FACT,
                    episode.provenance()
            );

            assertEquals(
                    2500,
                    episode.agreedRateBps()
            );

            assertEquals(
                    PayrollTruthReferenceBase.UNKNOWN,
                    episode.referenceBase()
            );

            assertNull(episode.referenceAmountMinor());
            assertNotNull(
                    episode.qualifiedMinutes()
            );

            assertTrue(
                    episode.qualifiedMinutes() > 0
            );

            var qualifiedQuantity =
                    episode.resolvedQualifiedQuantity();

            assertNotNull(
                    qualifiedQuantity
            );

            assertEquals(
                    ru.daniil.shifts.model.PayrollQuantityUnit.MINUTES,
                    qualifiedQuantity.unit()
            );

            assertEquals(
                    episode.qualifiedMinutes().longValue(),
                    qualifiedQuantity.value()
            );
            assertNotNull(episode.sourcePeriod());
            assertNotNull(episode.sourceLabel());
        }

        PayrollTruthCase may = byPeriod(pack, "2026-05");
        PayrollTruthCase june = byPeriod(pack, "2026-06");
        PayrollTruthCase july = byPeriod(pack, "2026-07");

        assertEquals(1, count(may, "COMBINATION"));
        assertEquals(2, count(june, "COMBINATION"));
        assertEquals(1, count(july, "COMBINATION"));

        assertTrue(
                june.facts()
                        .stream()
                        .anyMatch(fact ->
                                "combination_external_nominal_salary"
                                        .equals(fact.key())
                                        && fact.provenance()
                                        == PayrollTruthProvenance.UNKNOWN
                                        && fact.amountMinor() == null
                                        && fact.rateBps() == null
                                        && fact.minutes() == null
                        )
        );
    }

    @Test
    void sourceLineSplitsAreNotCollapsedIntoMonthlyTotals()
            throws Exception {

        PayrollTruthPack pack = read();

        PayrollTruthCase march = byPeriod(pack, "2026-03");
        PayrollTruthCase june = byPeriod(pack, "2026-06");

        assertEquals(3, count(march, "BASE_PAY"));
        assertEquals(3, count(march, "HARMFUL_CONDITIONS"));

        assertEquals(
                List.of(
                        "01.03-10.03",
                        "11.03-12.03",
                        "14.03-31.03"
                ),
                march.earnings()
                        .stream()
                        .filter(line -> "BASE_PAY".equals(line.semanticKey()))
                        .map(PayrollTruthEarning::sourcePeriod)
                        .toList()
        );

        assertEquals(
                List.of(
                        "01.06-15.06",
                        "29.06-30.06"
                ),
                june.earnings()
                        .stream()
                        .filter(line -> "COMBINATION".equals(line.semanticKey()))
                        .map(PayrollTruthEarning::sourcePeriod)
                        .toList()
        );
    }

    private static long percentHalfUp(long baseMinor, int rateBps) {
        return Math.floorDiv(
                baseMinor * rateBps + 5000L,
                10000L
        );
    }

    private static long sum(
            PayrollTruthCase truthCase,
            String semanticKey
    ) {
        return truthCase.earnings()
                .stream()
                .filter(line -> semanticKey.equals(line.semanticKey()))
                .mapToLong(PayrollTruthEarning::amountMinor)
                .sum();
    }

    private static long count(
            PayrollTruthCase truthCase,
            String semanticKey
    ) {
        return truthCase.earnings()
                .stream()
                .filter(line -> semanticKey.equals(line.semanticKey()))
                .count();
    }

    private static boolean hasAssertion(
            PayrollTruthCase truthCase,
            String key
    ) {
        return truthCase.assertions()
                .stream()
                .anyMatch(assertion -> key.equals(assertion.key()));
    }

    private static PayrollTruthCase byPeriod(
            PayrollTruthPack pack,
            String period
    ) {
        return pack.cases()
                .stream()
                .filter(truthCase -> period.equals(truthCase.period()))
                .findFirst()
                .orElseThrow();
    }

    private PayrollTruthPack read() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException(
                        "Missing test resource: " + RESOURCE
                );
            }

            return objectMapper.readValue(
                    input,
                    PayrollTruthPack.class
            );
        }
    }
}
