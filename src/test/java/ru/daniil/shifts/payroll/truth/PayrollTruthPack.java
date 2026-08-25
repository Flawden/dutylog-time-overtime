package ru.daniil.shifts.payroll.truth;

import ru.daniil.shifts.model.PayrollQualifiedQuantity;

import java.util.List;

record PayrollTruthPack(
        String schemaVersion,
        List<PayrollTruthCase> cases
) {
}

record PayrollTruthCase(
        String sourceId,
        String period,
        String currency,
        List<PayrollTruthFact> facts,
        List<PayrollTruthEarning> earnings,
        Long expectedGrossMinor,
        List<PayrollTruthAssertion> assertions
) {
}

record PayrollTruthFact(
        String key,
        Long amountMinor,
        Long minutes,
        Integer rateBps,
        PayrollTruthProvenance provenance,
        String note
) {
}

record PayrollTruthEarning(
        String semanticKey,
        Long amountMinor,

        /*
         * Legacy v1 evidence field.
         *
         * Existing real payroll cases were captured before the production
         * unit-aware quantity abstraction existed. Keep those golden files
         * immutable and interpret this field only as MINUTES.
         */
        Long qualifiedMinutes,

        /*
         * Native unit-aware quantity for new truth.
         *
         * Do not populate both fields with conflicting values.
         */
        PayrollQualifiedQuantity qualifiedQuantity,

        Integer agreedRateBps,
        PayrollTruthReferenceBase referenceBase,
        Long referenceAmountMinor,
        PayrollTruthProvenance provenance,
        String note,
        String sourceLabel,
        String sourcePeriod
) {

    /*
     * Source-compatible constructor for v1 tests and hand-built fixtures.
     */
    PayrollTruthEarning(
            String semanticKey,
            Long amountMinor,
            Long qualifiedMinutes,
            Integer agreedRateBps,
            PayrollTruthReferenceBase referenceBase,
            Long referenceAmountMinor,
            PayrollTruthProvenance provenance,
            String note,
            String sourceLabel,
            String sourcePeriod
    ) {
        this(
                semanticKey,
                amountMinor,
                qualifiedMinutes,
                null,
                agreedRateBps,
                referenceBase,
                referenceAmountMinor,
                provenance,
                note,
                sourceLabel,
                sourcePeriod
        );
    }

    /**
     * Canonical quantity view for both legacy and native truth.
     *
     * Legacy qualifiedMinutes is explicitly MINUTES; it must never become
     * a universal quantity convention.
     */
    PayrollQualifiedQuantity resolvedQualifiedQuantity() {
        if (qualifiedQuantity != null) {
            return qualifiedQuantity;
        }

        return qualifiedMinutes == null
                ? null
                : PayrollQualifiedQuantity.minutes(
                        qualifiedMinutes
                );
    }
}

record PayrollTruthAssertion(
        String key,
        PayrollTruthProvenance provenance,
        String statement
) {
}

enum PayrollTruthProvenance {
    FACT,
    PROVEN,
    INFERRED,
    UNKNOWN
}

enum PayrollTruthReferenceBase {
    OWN_NOMINAL_SALARY,
    OWN_EARNED_BASE,
    QUALIFIED_EARNINGS,
    EXTERNAL_REFERENCE_AMOUNT,
    UNKNOWN
}
