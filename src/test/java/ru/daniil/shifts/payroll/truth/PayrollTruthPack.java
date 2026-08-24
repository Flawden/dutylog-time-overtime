package ru.daniil.shifts.payroll.truth;

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
        Long qualifiedMinutes,
        Integer agreedRateBps,
        PayrollTruthReferenceBase referenceBase,
        Long referenceAmountMinor,
        PayrollTruthProvenance provenance,
        String note,
        String sourceLabel,
        String sourcePeriod
) {
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
