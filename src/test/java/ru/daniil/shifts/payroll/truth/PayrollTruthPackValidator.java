package ru.daniil.shifts.payroll.truth;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class PayrollTruthPackValidator {

    private PayrollTruthPackValidator() {
    }

    static void validate(PayrollTruthPack pack) {
        require(pack != null, "Truth pack is required");
        require("1".equals(pack.schemaVersion()), "Unsupported truth-pack schema version");
        require(pack.cases() != null && !pack.cases().isEmpty(), "Truth pack requires cases");

        Set<String> sourceIds = new HashSet<>();
        for (PayrollTruthCase truthCase : pack.cases()) {
            validateCase(truthCase);
            require(sourceIds.add(truthCase.sourceId()), "Duplicate sourceId: " + truthCase.sourceId());
        }
    }

    private static void validateCase(PayrollTruthCase truthCase) {
        require(truthCase != null, "Truth case is required");
        require(nonBlank(truthCase.sourceId()), "sourceId is required");
        require(nonBlank(truthCase.period()), "period is required");
        require(nonBlank(truthCase.currency()), "currency is required");
        require(truthCase.currency().matches("[A-Z]{3}"), "currency must be ISO-like uppercase code");

        List<PayrollTruthFact> facts = truthCase.facts() == null ? List.of() : truthCase.facts();
        List<PayrollTruthEarning> earnings =
                truthCase.earnings() == null ? List.of() : truthCase.earnings();
        List<PayrollTruthAssertion> assertions =
                truthCase.assertions() == null ? List.of() : truthCase.assertions();

        require(!facts.isEmpty() || !earnings.isEmpty(),
                "Truth case requires at least one fact or earning");

        for (PayrollTruthFact fact : facts) {
            validateFact(fact);
        }
        for (PayrollTruthEarning earning : earnings) {
            validateEarning(earning);
        }
        for (PayrollTruthAssertion assertion : assertions) {
            validateAssertion(assertion);
        }

        if (truthCase.expectedGrossMinor() != null) {
            require(truthCase.expectedGrossMinor() >= 0, "expectedGrossMinor cannot be negative");
        }
    }

    private static void validateFact(PayrollTruthFact fact) {
        require(fact != null, "Fact is required");
        require(nonBlank(fact.key()), "Fact key is required");
        require(fact.provenance() != null, "Fact provenance is required");

        if (fact.amountMinor() != null) {
            require(fact.amountMinor() >= 0, "Fact amountMinor cannot be negative");
        }
        if (fact.minutes() != null) {
            require(fact.minutes() >= 0, "Fact minutes cannot be negative");
        }
        if (fact.rateBps() != null) {
            require(fact.rateBps() >= 0, "Fact rateBps cannot be negative");
        }

        /*
         * UNKNOWN means the value itself is not known. It may carry a note
         * describing the missing fact, but must not smuggle a guessed number
         * into the golden dataset.
         */
        if (fact.provenance() == PayrollTruthProvenance.UNKNOWN) {
            require(
                    fact.amountMinor() == null
                            && fact.minutes() == null
                            && fact.rateBps() == null,
                    "UNKNOWN fact cannot expose numeric truth"
            );
        }
    }

    private static void validateEarning(PayrollTruthEarning earning) {
        require(earning != null, "Earning is required");
        require(nonBlank(earning.semanticKey()), "Earning semanticKey is required");
        require(earning.provenance() != null, "Earning provenance is required");
        require(earning.amountMinor() != null, "Earning amountMinor is required");
        require(earning.amountMinor() >= 0, "Earning amountMinor cannot be negative");

        if (earning.qualifiedMinutes() != null) {
            require(earning.qualifiedMinutes() >= 0, "qualifiedMinutes cannot be negative");
        }
        if (earning.agreedRateBps() != null) {
            require(earning.agreedRateBps() >= 0, "agreedRateBps cannot be negative");
        }

        PayrollTruthReferenceBase base = earning.referenceBase();

        if (base == PayrollTruthReferenceBase.EXTERNAL_REFERENCE_AMOUNT) {
            require(
                    earning.referenceAmountMinor() != null,
                    "EXTERNAL_REFERENCE_AMOUNT requires referenceAmountMinor"
            );
        }

        if (earning.referenceAmountMinor() != null) {
            require(earning.referenceAmountMinor() >= 0,
                    "referenceAmountMinor cannot be negative");
            require(
                    base == PayrollTruthReferenceBase.EXTERNAL_REFERENCE_AMOUNT,
                    "referenceAmountMinor is only valid for EXTERNAL_REFERENCE_AMOUNT"
            );
        }

        /*
         * A formula cannot be PROVEN while its calculation base is UNKNOWN.
         * The observed earning amount may still be FACT even when the base
         * behind that amount is unavailable.
         */
        if (earning.provenance() == PayrollTruthProvenance.PROVEN) {
            require(base != null && base != PayrollTruthReferenceBase.UNKNOWN,
                    "PROVEN earning cannot use UNKNOWN reference base");
        }
    }

    private static void validateAssertion(PayrollTruthAssertion assertion) {
        require(assertion != null, "Assertion is required");
        require(nonBlank(assertion.key()), "Assertion key is required");
        require(assertion.provenance() != null, "Assertion provenance is required");
        require(nonBlank(assertion.statement()), "Assertion statement is required");
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
