package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkTimeAccountingAuthorityContractTest {

    @Test
    void migrationCreatesExplicitEffectiveDatedAuthorityWithoutSyntheticBaseline()
            throws Exception {
        String sql = Files.readString(
                Path.of(
                        "src/main/resources/db/migration/postgresql/"
                                + "V76__work_time_accounting_regime_fact_authority.sql"
                ),
                StandardCharsets.UTF_8
        );

        assertTrue(sql.contains("CREATE TABLE work_time_accounting_terms"));
        assertTrue(sql.contains("accounting_mode IN ('DAILY', 'SUMMARIZED')"));
        assertTrue(sql.contains("UNIQUE (user_id, effective_from)"));
        assertFalse(sql.contains("INSERT INTO work_time_accounting_terms"));
    }

    @Test
    void authorityRemainsSeparateFromPayModeAndParagraph15FormulaInference()
            throws Exception {
        String service = Files.readString(
                Path.of(
                        "src/main/java/ru/daniil/shifts/service/"
                                + "WorkTimeAccountingHistoryService.java"
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

        assertTrue(service.contains("WORK_TIME_ACCOUNTING_MODE_FACT_MISSING"));
        assertTrue(service.contains("WorkTimeAccountingMode"));
        assertFalse(service.contains("getPayMode()"));
        assertFalse(service.contains("TimeAccountingPeriod"));

        assertTrue(formula.contains("WORKING_DAYS"));
        assertTrue(formula.contains("WORKING_MINUTES"));
        assertTrue(formula.contains("never\n     * inferred here"));
        assertFalse(formula.contains("WorkTimeAccountingHistoryService"));
    }
}
