package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AverageEarningsParagraph7PreEventBonusWorkTimeAuthorityContractTest {
    private static final Path P7 = Path.of(
            "src/main/java/ru/daniil/shifts/service/" +
                    "AverageEarningsParagraph7PreEventBonusWorkTimeFactService.java"
    );
    private static final Path FREEZE = Path.of(
            "src/main/java/ru/daniil/shifts/service/PayrollP15ScheduledWorkFreezeService.java"
    );

    @Test
    void J3B6B3AUsesExactPreEventPayrollWindow() throws Exception {
        String source = Files.readString(P7);
        assertTrue(source.contains("YearMonth.from(eventDate).atDay(1)"));
        assertTrue(source.contains("cutoffExclusive = eventDate"));
        assertTrue(source.contains("eventDate.minusDays(1)"));
        assertTrue(source.contains("timeCompensation.payrollSource(user, periodFrom, periodTo)"));
        assertTrue(source.contains("SOURCE_WINDOW_MISMATCH"));
    }

    @Test
    void J3B6B3AReusesCanonicalP15PlanActualRelationAuthority() throws Exception {
        String p7 = Files.readString(P7);
        String freeze = Files.readString(FREEZE);
        assertTrue(p7.contains("scheduledWork.deriveRange(user, source)"));
        assertTrue(freeze.contains("public RangeResult deriveRange"));
        assertTrue(freeze.contains("explicitDraft("));
        assertTrue(freeze.contains("planDerivedDraft("));
        assertTrue(freeze.contains("relationEngine.compareDay("));
        assertTrue(freeze.contains("RANGE_SOURCE_IDENTITY_INCOMPLETE"));
        assertTrue(freeze.contains("RANGE_PREVIOUS_SOURCE_WINDOW_MISMATCH"));
    }

    @Test
    void J3B6B3AKeepsWorkedOutsidePlanOutOfCoefficient() throws Exception {
        String source = Files.readString(P7);
        assertTrue(source.contains("fact.scheduleMinutes()"));
        assertTrue(source.contains("fact.plannedAndWorkedMinutes()"));
        assertTrue(source.contains("Worked-outside-plan minutes are"));
        assertFalse(source.contains("fact.workedOutsidePlanMinutes())"));
    }

    @Test
    void J3B6B3ADailyPartialDayFailsClosedAndMixedModesDoNotMix() throws Exception {
        String source = Files.readString(P7);
        assertTrue(source.contains("DAILY_PARTIAL_DAY_UNRESOLVED"));
        assertTrue(source.contains("MIXED_ACCOUNTING_MODE"));
        assertTrue(source.contains("fact.plannedAndWorkedMinutes() == fact.scheduleMinutes()"));
        assertTrue(source.contains("fact.plannedAndWorkedMinutes() != 0"));
    }

    @Test
    void J3B6B3AStopsBeforeBonusMoneyOrParagraph8() throws Exception {
        String source = Files.readString(P7);
        assertFalse(source.contains("AverageEarningsBonusP15Formula"));
        assertFalse(source.contains("includedAmountMinor"));
        assertFalse(source.contains("BigInteger"));
        assertFalse(source.contains("BigDecimal"));
        assertFalse(source.contains("RoundingMode"));
        assertFalse(source.contains("PARAGRAPH_8"));
        assertFalse(source.contains("Paragraph8"));
    }
}
