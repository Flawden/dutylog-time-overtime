package ru.daniil.shifts.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class VacationPayApplicationServiceContractTest {

    private static final Path SOURCE = Path.of(
            "src/main/java/ru/daniil/shifts/service/VacationPayApplicationService.java"
    );

    @Test
    void OIsSpringApplicationBoundaryOverP6J5AndN() throws Exception {
        String source = source();
        assertTrue(source.contains("@Service"));
        assertTrue(source.contains("RULE_ID = \"DUTYLOG_VACATION_PAY_APPLICATION\""));
        assertTrue(source.contains("AverageEarningsParagraph6ReferenceResolver paragraph6"));
        assertTrue(source.contains("AverageEarningsOrderedFallbackResolver::resolve"));
        assertTrue(source.contains("VacationPayOrchestrator vacationPay"));
    }

    @Test
    void OHasExplicitAutowiredProductionConstructor() throws Exception {
        String source = source();
        assertTrue(source.contains("@Autowired\n    public VacationPayApplicationService("));
    }

    @Test
    void OResolvesP6BeforeJ5BeforeN() throws Exception {
        String source = source();
        int p6 = source.indexOf("paragraph6Resolver.resolve(");
        int j5 = source.indexOf("orderedFallbackResolver.resolve(");
        int n = source.indexOf("vacationResolver.resolve(");
        assertTrue(p6 >= 0);
        assertTrue(j5 > p6);
        assertTrue(n > j5);
    }

    @Test
    void ORecalculatesOnlySelectedJ5ReferenceWindow() throws Exception {
        String source = source();
        int selected = source.indexOf("orderedFallback.selectedReferenceWindow()");
        int calculation = source.indexOf("referenceCalculator.calculate(");
        assertTrue(selected >= 0);
        assertTrue(calculation > selected);
        assertTrue(source.contains("selected,\n                                    discoveryThroughMonth"));
    }

    @Test
    void OPassesKBasisSuppliersThroughWithoutApplicationPolicy() throws Exception {
        String source = source();
        assertTrue(source.contains("paragraph7CalendarBasisSupplier,\n                                paragraph8FormulaBasisSupplier"));
        assertFalse(source.contains("Paragraph7CalendarBasis.of("));
        assertFalse(source.contains("Paragraph8FormulaBasis.monthlySalary("));
        assertFalse(source.contains("Paragraph8FormulaBasis.hourlyTariff("));
    }

    @Test
    void OContainsNoVacationFormulaCalendarOrRepositoryPolicy() throws Exception {
        String source = source();
        for (String forbidden : new String[]{
                "BigInteger",
                "BigDecimal",
                "RoundingMode",
                "ProductionCalendarService",
                "AbsencePeriodRepository",
                "AnnualPaidVacationHolidayPolicy",
                "VacationAverageDailyEarningsFormula.calculate",
                "VacationPayMoneyFormula.calculate"
        }) {
            assertFalse(source.contains(forbidden), forbidden);
        }
    }

    @Test
    void ODoesNotMutatePayrollSnapshotOrTotalPay() throws Exception {
        String source = source();
        for (String forbidden : new String[]{
                "PayrollService",
                "PayrollSnapshot",
                "totalPayMinor",
                "PayrollEarningKind.VACATION_PAY",
                "saveAndFlush",
                "@Transactional\n    public"
        }) {
            assertFalse(source.contains(forbidden), forbidden);
        }
    }

    @Test
    void OExposesP6J5AndNProvenanceInOneResult() throws Exception {
        String source = source();
        assertTrue(source.contains(
                "AverageEarningsParagraph6ReferenceResolver.Resolution paragraph6Authority"
        ));
        assertTrue(source.contains(
                "AverageEarningsOrderedFallbackResolver.Resolution orderedFallback"
        ));
        assertTrue(source.contains(
                "VacationPayOrchestrator.Resolution vacationPay"
        ));
        assertTrue(source.contains("return vacationPay.vacationPayMinor();"));
    }

    private static String source() throws Exception {
        return Files.readString(SOURCE, StandardCharsets.UTF_8);
    }
}
