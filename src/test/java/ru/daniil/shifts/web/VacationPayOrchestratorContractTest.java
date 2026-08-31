package ru.daniil.shifts.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class VacationPayOrchestratorContractTest {

    private static final Path SOURCE = Path.of(
            "src/main/java/ru/daniil/shifts/service/VacationPayOrchestrator.java"
    );

    @Test
    void NIsOneSpringOrchestrationServiceOverLKM() throws Exception {
        String source = source();
        assertTrue(source.contains("@Service"));
        assertTrue(source.contains("@Autowired\n    public VacationPayOrchestrator("));
        assertTrue(source.contains("VacationPayableDaysFactService payableDays"));
        assertTrue(source.contains("VacationAverageUnifiedDailyResolver::resolve"));
        assertTrue(source.contains("VacationPayMoneyFormula::calculate"));
    }

    @Test
    void NCallsLBeforeAnyKResolution() throws Exception {
        String source = source();
        int l = source.indexOf("payableDays.resolve(user, eventDate, absencePeriodId)");
        int k = source.indexOf("dailyResolver.resolve(");
        assertTrue(l >= 0);
        assertTrue(k > l);
    }

    @Test
    void NLBlockedGuardPrecedesKInputValidationAndKCall() throws Exception {
        String source = source();
        int lGuard = source.indexOf("if (!payableDaysAuthority.ready())");
        int orderedRequired = source.indexOf(
                "Vacation pay orchestration requires already-selected J5 resolution"
        );
        int k = source.indexOf("dailyResolver.resolve(");
        assertTrue(lGuard >= 0);
        assertTrue(orderedRequired > lGuard);
        assertTrue(k > orderedRequired);
    }

    @Test
    void NKBlockedGuardPrecedesMCall() throws Exception {
        String source = source();
        int kGuard = source.indexOf("if (!dailyAuthority.ready())");
        int m = source.indexOf("moneyCalculator.calculate(dailyAuthority, payableDaysAuthority)");
        assertTrue(kGuard >= 0);
        assertTrue(m > kGuard);
    }

    @Test
    void NDoesNotChooseJ5FallbackBranch() throws Exception {
        String source = source();
        assertFalse(source.contains("switch ("));
        assertFalse(source.contains("PRIMARY_REFERENCE_PERIOD"));
        assertFalse(source.contains("PARAGRAPH_6_PRECEDING_REFERENCE_PERIOD"));
        assertFalse(source.contains("PARAGRAPH_7_PRE_EVENT_ACCRUED_WAGE"));
        assertFalse(source.contains("PARAGRAPH_8_ESTABLISHED_TARIFF_OR_SALARY"));
    }

    @Test
    void NContainsNoMoneyArithmeticOrRoundingPolicy() throws Exception {
        String source = source();
        for (String forbidden : new String[]{
                "BigInteger",
                "BigDecimal",
                "RoundingMode",
                "divideAndRemainder",
                ".multiply(",
                "HALF_UP"
        }) {
            assertFalse(source.contains(forbidden), forbidden);
        }
    }

    @Test
    void NContainsNoCalendarHolidayOrRepositoryPolicy() throws Exception {
        String source = source();
        for (String forbidden : new String[]{
                "ProductionCalendarService",
                "AbsencePeriodRepository",
                "AnnualPaidVacationHolidayPolicy",
                "nonWorkingHoliday",
                "VACATION_DAYS",
                "VACATION_ALLOWANCE"
        }) {
            assertFalse(source.contains(forbidden), forbidden);
        }
    }

    @Test
    void NBlockedFacadeNeverExposesPartialMoney() throws Exception {
        String source = source();
        assertTrue(source.contains(
                "return ready ? moneyAuthority.currencyCode() : null;"
        ));
        assertTrue(source.contains(
                "return ready ? moneyAuthority.vacationPayMinor() : null;"
        ));
        assertTrue(source.contains(
                "return ready ? payableDaysAuthority.payableCalendarDays() : 0;"
        ));
    }

    private static String source() throws Exception {
        return Files.readString(SOURCE, StandardCharsets.UTF_8);
    }
}
