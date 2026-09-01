package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AverageEarningsVacationBasisAuthoritiesContractTest {

    private static final Path FACTS = Path.of(
            "src/main/java/ru/daniil/shifts/service/AverageEarningsReferenceFactsService.java"
    );
    private static final Path P7 = Path.of(
            "src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph7CalendarBasisAuthorityService.java"
    );
    private static final Path P8 = Path.of(
            "src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph8VacationFormulaBasisAuthorityService.java"
    );

    @Test
    void paragraph7AuthorityUsesExactPreEventCalendarRange() throws Exception {
        String source = source(P7);
        assertTrue(source.contains("@Service"));
        assertTrue(source.contains("RULE_ID = \"PP_540_P7_P10_CALENDAR_BASIS\""));
        assertTrue(source.contains("eventMonth.atDay(1)"));
        assertTrue(source.contains("eventDate.minusDays(1)"));
        assertTrue(source.contains("referenceFacts.resolveRange("));
        assertTrue(source.contains("employment.resolve("));
    }

    @Test
    void paragraph7AuthorityReusesLegalAndVacationHolidayClassification() throws Exception {
        String source = source(P7);
        assertTrue(source.contains("AverageEarningsLegalPolicy.classifyAbsence("));
        assertTrue(source.contains("AnnualPaidVacationHolidayPolicy.classify("));
        assertTrue(source.contains("FULL_DAY"));
        assertTrue(source.contains("Paragraph7CalendarBasis.of("));
        assertFalse(source.contains("workedDayCount"));
        assertFalse(source.contains("workedMinutes"));
    }

    @Test
    void factualAbsenceSourceSupportsExactRangeWithoutFakeReferenceWindow() throws Exception {
        String source = source(FACTS);
        assertTrue(source.contains("public ReferenceFacts resolveRange("));
        assertTrue(source.contains("referenceWindow.referenceFromDate()"));
        assertTrue(source.contains("referenceWindow.referenceToDate()"));
        assertTrue(source.contains("Average earnings factual range is invalid"));
    }

    @Test
    void paragraph8MonthlySalaryDoesNotReadProductionCalendar() throws Exception {
        String source = source(P8);
        int monthlyBranch = source.indexOf("EstablishedBasis.MONTHLY_OFFICIAL_SALARY");
        int monthlyBasis = source.indexOf(".monthlySalary(");
        int annualLoop = source.indexOf("for (int monthNumber = 1; monthNumber <= 12; monthNumber++)");
        assertTrue(monthlyBranch >= 0);
        assertTrue(monthlyBasis > monthlyBranch);
        assertTrue(annualLoop > monthlyBasis);
    }

    @Test
    void paragraph8HourlyAuthorityRequiresTwelveCompleteProductionNormMonths() throws Exception {
        String source = source(P8);
        assertTrue(source.contains("monthNumber <= 12"));
        assertTrue(source.contains("productionCalendar.month("));
        assertTrue(source.contains("calendar.scheduleCoverageComplete()"));
        assertTrue(source.contains("calendar.scheduleCoverageDays() != month.lengthOfMonth()"));
        assertTrue(source.contains("calendar.productionNormMinutes()"));
        assertTrue(source.contains("Math.addExact("));
        assertTrue(source.contains(".hourlyTariff("));
        assertFalse(source.contains("baseNormMinutes()"));
    }

    @Test
    void basisAuthoritiesContainNoMoneyCalculationOrPayrollMutation() throws Exception {
        String joined = source(P7) + "\n" + source(P8);
        for (String forbidden : new String[]{
                "BigDecimal",
                "RoundingMode",
                "PayrollService",
                "PayrollSnapshot",
                "totalPayMinor",
                "VacationAverageDailyEarningsFormula.calculate",
                "VacationPayMoneyFormula.calculate",
                "saveAndFlush"
        }) {
            assertFalse(joined.contains(forbidden), forbidden);
        }
    }

    private static String source(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
