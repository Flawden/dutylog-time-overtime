package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AverageEarningsParagraph7PreEventBonusP15FormulaContractTest {
    private static final Path FORMULA = Path.of(
            "src/main/java/ru/daniil/shifts/service/" +
                    "AverageEarningsParagraph7PreEventBonusP15Formula.java"
    );

    @Test
    void J3B6B3BConsumesB6B3AWithB6B2PolicyProvenance() throws Exception {
        String source = Files.readString(FORMULA);
        assertTrue(source.contains(
                "AverageEarningsParagraph7PreEventBonusWorkTimeFactService.Resolution"
        ));
        assertTrue(source.contains("workTimeAuthority.policy()"));
        assertTrue(source.contains("AUTHORITY_POLICY_CONTRADICTION"));
        assertTrue(source.contains("workTimeAuthority.workTimeRequired() != expectedWorkTime"));
    }

    @Test
    void J3B6B3BUsesOneMonthlyPartAndOneFinalHalfUpBoundary() throws Exception {
        String source = Files.readString(FORMULA);
        assertTrue(source.contains("MONTHLY_PART_FOR_PRE_EVENT_MONTH"));
        assertTrue(source.contains("denominator.multiply(BigInteger.valueOf(awardMonthCount))"));
        assertTrue(source.contains("roundHalfUpToLong(numerator, denominator)"));
        assertTrue(source.contains("isWholeCalendarMonthPeriod"));
        assertFalse(source.contains("BigDecimal"));
        assertFalse(source.contains("RoundingMode"));
    }

    @Test
    void J3B6B3BUsesOnlyProvenPreEventWorkTimeRatio() throws Exception {
        String source = Files.readString(FORMULA);
        assertTrue(source.contains("workTimeAuthority.scheduleFullyWorked()"));
        assertTrue(source.contains("workTimeAuthority.workedUnits()"));
        assertTrue(source.contains("workTimeAuthority.normUnits()"));
        assertTrue(source.contains("PROPORTIONAL_TO_PRE_EVENT_WORKED_TIME"));
        assertTrue(source.contains("AppliedWorkedTimeFact"));
    }

    @Test
    void J3B6B3BUnknownActualAccrualFailsClosedOnlyAfterIncompleteBasisIsProven() throws Exception {
        String source = Files.readString(FORMULA);
        assertTrue(source.contains("REQUIRE_EXPLICIT_ACTUAL_WORK_ACCRUAL_FACT"));
        assertTrue(source.contains("ACTUAL_WORK_TIME_ACCRUAL_FACT_REQUIRED"));
        assertTrue(source.contains("NONE_PRE_EVENT_SCHEDULE_FULLY_WORKED"));
        assertTrue(source.indexOf("scheduleFullyWorked()")
                < source.indexOf("REQUIRE_EXPLICIT_ACTUAL_WORK_ACCRUAL_FACT"));
    }

    @Test
    void J3B6B3BStopsAtBonusMoneyWithoutFullParagraph7OrLaterFallback() throws Exception {
        String source = Files.readString(FORMULA);
        assertTrue(source.contains("includedPremiumAmountMinor"));
        assertTrue(source.contains("INCLUDED_CURRENCY_MISMATCH"));
        assertFalse(source.contains("AverageEarningsParagraph7PreEventBasePayFormula"));
        assertFalse(source.contains("AverageEarningsParagraph7PreEventOrdinaryPremiumService"));
        assertFalse(source.contains("AverageEarningsParagraph7PreEventHarmfulCompensationService"));
        assertFalse(source.contains("Paragraph8"));
        assertFalse(source.contains("PARAGRAPH_8"));
    }
}
