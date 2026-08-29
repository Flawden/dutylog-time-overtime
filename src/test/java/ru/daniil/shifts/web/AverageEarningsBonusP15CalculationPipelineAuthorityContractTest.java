package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AverageEarningsBonusP15CalculationPipelineAuthorityContractTest {

    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/ru/daniil/shifts/service", relative));
    }

    @Test
    void pipelinePreservesFactPolicyFormulaMoneyOrderWithoutImplementingItsOwnRatio() throws Exception {
        String text = source("AverageEarningsBonusP15CalculationPipelineService.java");

        int discovery = text.indexOf("discovery.resolve(");
        int completeness = text.indexOf("completeness.resolve(");
        int policy = text.indexOf("AverageEarningsBonusP15Policy.resolve(");
        int formula = text.indexOf("AverageEarningsBonusP15Formula.calculate(");

        assertTrue(discovery >= 0 && discovery < completeness && completeness < policy && policy < formula);
        assertFalse(text.contains("BigDecimal"));
        assertFalse(text.contains("BigInteger"));
        assertFalse(text.contains("RoundingMode"));
    }

    @Test
    void completenessUsesF3F3AndParagraph5PolicyWithoutReadingPayrollAggregates() throws Exception {
        String text = source("AverageEarningsBonusP15ReferenceCompletenessService.java");

        assertTrue(text.contains("workedTime.resolve("));
        assertTrue(text.contains("referenceFacts.resolve("));
        assertTrue(text.contains("AverageEarningsLegalPolicy.classifyAbsence"));
        assertFalse(text.contains("PayrollSnapshotRepository"));
        assertFalse(text.contains("getWorkedMinutes()"));
        assertFalse(text.contains("getPlannedMinutes()"));
        assertFalse(text.contains("getProductionNormMinutes()"));
    }

    @Test
    void noPayrollReferenceMonthCannotSilentlyBecomeRatioOne() throws Exception {
        String pipeline = source("AverageEarningsBonusP15CalculationPipelineService.java");
        String completeness = source("AverageEarningsBonusP15ReferenceCompletenessService.java");

        assertTrue(pipeline.contains("PP_540_P15_REFERENCE_NO_PAYROLL_NORM_AUTHORITY_UNRESOLVED"));
        assertTrue(pipeline.contains("proportionalNormAuthorityComplete"));
        assertTrue(completeness.contains("noPayrollMonths.isEmpty()"));
        assertFalse(pipeline.contains("Math.min"));
    }

    @Test
    void pureF3CAndF3DRemainFreeOfOrchestratorDependency() throws Exception {
        String policy = source("AverageEarningsBonusP15Policy.java");
        String formula = source("AverageEarningsBonusP15Formula.java");

        assertFalse(policy.contains("AverageEarningsBonusP15CalculationPipelineService"));
        assertFalse(policy.contains("AverageEarningsBonusP15ReferenceCompletenessService"));
        assertFalse(formula.contains("AverageEarningsBonusP15CalculationPipelineService"));
        assertFalse(formula.contains("AverageEarningsBonusP15ReferenceCompletenessService"));
    }
}
