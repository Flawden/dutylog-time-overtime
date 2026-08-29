package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayrollP15ScheduledWorkFreezeWiringContractTest {

    @Test
    void payrollPersistsScheduledWorkManifestBesideNewSnapshotBeforeSupersession() throws Exception {
        String payroll = Files.readString(
                Path.of("src/main/java/ru/daniil/shifts/service/PayrollService.java"),
                StandardCharsets.UTF_8
        );

        int create = payroll.indexOf("snapshots.saveAndFlush(new PayrollSnapshot(");
        int freeze = payroll.indexOf("freezeP15ScheduledWork(");
        int supersede = payroll.indexOf("previous.supersedeWith(created)");

        assertTrue(create >= 0);
        assertTrue(freeze > create);
        assertTrue(supersede > freeze);
        assertTrue(payroll.contains("p15ScheduledWorkFreeze.freeze("));
    }

    @Test
    void scheduledWorkFreezeStaysFactOnlyAndSeparateFromP13ActualHours() throws Exception {
        String freeze = Files.readString(
                Path.of("src/main/java/ru/daniil/shifts/service/PayrollP15ScheduledWorkFreezeService.java"),
                StandardCharsets.UTF_8
        );
        String formula = Files.readString(
                Path.of("src/main/java/ru/daniil/shifts/service/AverageEarningsBonusP15Formula.java"),
                StandardCharsets.UTF_8
        );
        String migration = Files.readString(
                Path.of("src/main/resources/db/migration/postgresql/V77__snapshot_p15_scheduled_work_fact_freeze.sql"),
                StandardCharsets.UTF_8
        );

        assertTrue(freeze.contains("PLANNED_AND_WORKED"));
        assertTrue(freeze.contains("WORKED_OUTSIDE_PLAN"));
        assertTrue(freeze.contains("paragraph-13"));
        assertFalse(formula.contains("PayrollP15ScheduledWorkFreezeService"));
        assertFalse(migration.toUpperCase().contains("INSERT INTO PAYROLL_SNAPSHOT_P15"));
        assertTrue(migration.contains("worked_outside_plan_minutes"));
    }
}
