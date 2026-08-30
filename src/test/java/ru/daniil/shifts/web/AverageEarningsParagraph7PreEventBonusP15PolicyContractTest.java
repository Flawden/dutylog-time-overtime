package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AverageEarningsParagraph7PreEventBonusP15PolicyContractTest {
    private static final Path POLICY = Path.of(
            "src/main/java/ru/daniil/shifts/service/" +
                    "AverageEarningsParagraph7PreEventBonusP15Policy.java"
    );

    @Test
    void J3B6B2UsesExactParagraph7PreEventWindowInsteadOfTwelveMonthPolicy() throws Exception {
        String source = Files.readString(POLICY);
        assertTrue(source.contains("YearMonth.from(eventDate).atDay(1)"));
        assertTrue(source.contains("cutoffExclusive = eventDate"));
        assertTrue(source.contains("[eventMonthStart,eventDate)"));
        assertFalse(source.contains("AverageEarningsReferenceWindow.of"));
        assertFalse(source.contains("AverageEarningsBonusP15Policy.resolve"));
    }

    @Test
    void J3B6B2OrdinaryBonusesRequireEventMonthAccrual() throws Exception {
        String source = Files.readString(POLICY);
        assertTrue(source.contains("EXCLUDE_NOT_ACCRUED_IN_P7_EVENT_MONTH"));
        assertTrue(source.contains("PP_540_P15_MONTHLY"));
        assertTrue(source.contains("PP_540_P15_WORK_PERIOD"));
        assertTrue(source.contains("MONTHLY_PART_FOR_PRE_EVENT_MONTH"));
        assertTrue(source.contains("MONTHLY_DUPLICATE"));
    }

    @Test
    void J3B6B2AnnualAndServiceRemainBoundToPreviousEventCalendarYear() throws Exception {
        String source = Files.readString(POLICY);
        assertTrue(source.contains("PP_540_P15_PREVIOUS_CALENDAR_YEAR"));
        assertTrue(source.contains("eventDate.getYear() - 1"));
        assertTrue(source.contains("PayrollBonusP15Nature.ANNUAL_RESULT"));
        assertTrue(source.contains("PayrollBonusP15Nature.SERVICE_LENGTH"));
        assertTrue(source.contains("EXCLUDE_NOT_PREVIOUS_EVENT_CALENDAR_YEAR"));
    }

    @Test
    void J3B6B2DefersIncompleteBasisRatioToB6B3() throws Exception {
        String source = Files.readString(POLICY);
        assertTrue(source.contains("IncompletePreEventTreatment"));
        assertTrue(source.contains("PROPORTIONAL_TO_PRE_EVENT_WORKED_TIME"));
        assertTrue(source.contains("REQUIRE_EXPLICIT_ACTUAL_WORK_ACCRUAL_FACT"));
        assertTrue(source.contains("NO_ADJUSTMENT_ALREADY_ACCRUED_FOR_ACTUAL_PRE_EVENT_TIME"));
        assertFalse(source.contains("ReferenceWorkedTimeFact"));
        assertFalse(source.contains("ProductionCalendarService"));
        assertFalse(source.contains("TimeCompensationService"));
    }

    @Test
    void J3B6B2DoesNotCalculateBonusMoneyOrSelectParagraph8() throws Exception {
        String source = Files.readString(POLICY);
        assertFalse(source.contains("AverageEarningsBonusP15Formula"));
        assertFalse(source.contains("BigInteger"));
        assertFalse(source.contains("BigDecimal"));
        assertFalse(source.contains("RoundingMode"));
        assertFalse(source.contains("PARAGRAPH_8"));
        assertFalse(source.contains("Paragraph8"));
    }
}
