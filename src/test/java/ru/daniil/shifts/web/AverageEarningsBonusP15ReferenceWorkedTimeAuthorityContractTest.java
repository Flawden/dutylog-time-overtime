package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AverageEarningsBonusP15ReferenceWorkedTimeAuthorityContractTest {

    @Test
    void resolverConsumesOnlyImmutableScheduledWorkAuthorityAndNeverAggregatePayrollTime() throws Exception {
        String service = Files.readString(
                Path.of(
                        "src/main/java/ru/daniil/shifts/service/"
                                + "AverageEarningsBonusP15ReferenceWorkedTimeFactService.java"
                ),
                StandardCharsets.UTF_8
        );

        assertTrue(service.contains("PayrollSnapshotP15WorkTimeManifestRepository"));
        assertTrue(service.contains("PayrollSnapshotP15ScheduledWorkFactRepository"));
        assertTrue(service.contains("getPlannedAndWorkedMinutes()"));
        assertTrue(service.contains("getScheduleMinutes()"));
        assertTrue(service.contains("DAILY_PARTIAL_DAY_UNRESOLVED"));
        assertTrue(service.contains("MIXED_ACCOUNTING_MODE"));

        assertFalse(service.contains("WorkTimeAccountingHistoryService"));
        assertFalse(service.contains("snapshot.getWorkedMinutes()"));
        assertFalse(service.contains("snapshot.getPlannedMinutes()"));
        assertFalse(service.contains("getProductionNormMinutes()"));
        assertFalse(service.contains("getSalaryCoveredMinutes()"));
    }

    @Test
    void f3f3DoesNotLeakIntoPolicyFormulaOrParagraph13ActualHoursSemantics() throws Exception {
        String service = Files.readString(
                Path.of(
                        "src/main/java/ru/daniil/shifts/service/"
                                + "AverageEarningsBonusP15ReferenceWorkedTimeFactService.java"
                ),
                StandardCharsets.UTF_8
        );
        String policy = Files.readString(
                Path.of(
                        "src/main/java/ru/daniil/shifts/service/"
                                + "AverageEarningsBonusP15Policy.java"
                ),
                StandardCharsets.UTF_8
        );
        String formula = Files.readString(
                Path.of(
                        "src/main/java/ru/daniil/shifts/service/"
                                + "AverageEarningsBonusP15Formula.java"
                ),
                StandardCharsets.UTF_8
        );

        assertTrue(service.contains("scheduleFullyWorked"));
        assertTrue(service.contains("paragraph-5 excluded time remains a"));
        assertTrue(service.contains("P13 actual worked hours are"));
        assertTrue(service.contains("WORKING_DAYS"));
        assertTrue(service.contains("WORKING_MINUTES"));

        assertFalse(policy.contains("AverageEarningsBonusP15ReferenceWorkedTimeFactService"));
        assertFalse(formula.contains("AverageEarningsBonusP15ReferenceWorkedTimeFactService"));
    }
}
