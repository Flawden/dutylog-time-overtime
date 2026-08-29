package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AverageEarningsNumeratorCalculationAuthorityContractTest {

    private static final Path NUMERATOR = Path.of(
            "src/main/java/ru/daniil/shifts/service/AverageEarningsNumeratorCalculationService.java"
    );
    private static final Path P5 = Path.of(
            "src/main/java/ru/daniil/shifts/service/AverageEarningsParagraph5MoneyPolicy.java"
    );
    private static final Path FACTS = Path.of(
            "src/main/java/ru/daniil/shifts/service/AverageEarningsNumeratorFactsService.java"
    );

    @Test
    void numeratorAssemblyOrderIsFactsThenP15ThenReconciliationThenParagraph5ThenMoney() throws IOException {
        String text = Files.readString(NUMERATOR);
        int facts = text.indexOf("numeratorFacts.resolve(");
        int p15 = text.indexOf("p15.calculate(");
        int reconcile = text.indexOf("reconcileReferencePremiumFacts(");
        int paragraph5 = text.indexOf("AverageEarningsParagraph5MoneyPolicy.resolve(");
        int money = text.indexOf("long total = Math.addExact(");

        assertTrue(facts >= 0 && facts < p15);
        assertTrue(p15 < reconcile);
        assertTrue(reconcile < paragraph5);
        assertTrue(paragraph5 < money);
    }

    @Test
    void rawPremiumSpecialBucketCannotBeDirectlyAddedToFinalNumerator() throws IOException {
        String text = Files.readString(NUMERATOR);
        assertTrue(text.contains("facts.premiumSpecialAmountMinor()"));
        assertFalse(text.contains("Math.addExact(\n                facts.premiumSpecialAmountMinor()"));
        assertTrue(text.contains("premium.calculation().includedPremiumAmountMinor()"));
    }

    @Test
    void paragraph5MoneyPolicyContainsNoRatioOrPostingMonthBacksolve() throws IOException {
        String text = Files.readString(P5);
        assertFalse(text.contains("BigDecimal"));
        assertFalse(text.contains("RoundingMode"));
        assertFalse(text.contains("ChronoUnit"));
        assertFalse(text.contains(".divide("));
        assertFalse(text.contains("postingMonth.atDay"));
        assertTrue(text.contains("TIME_AUTHORITY_MISSING"));
        assertTrue(text.contains("PARTIAL_OVERLAP_UNRESOLVED"));
    }

    @Test
    void existingNumeratorFactsBoundaryRemainsFactOnlyAndDoesNotDependOnNewMoneyLayer() throws IOException {
        String text = Files.readString(FACTS);
        assertTrue(text.contains("paragraph-15 premium allocation, paragraph-5 money allocation"));
        assertFalse(text.contains("AverageEarningsNumeratorCalculationService"));
        assertFalse(text.contains("AverageEarningsParagraph5MoneyPolicy"));
    }

    @Test
    void finalNumeratorLayerDoesNotCalculateAverageDailyHourlyOrVacationMoney() throws IOException {
        String text = Files.readString(NUMERATOR);
        assertFalse(text.contains("VacationAverageCalendarDenominator"));
        assertFalse(text.contains("averageDaily"));
        assertFalse(text.contains("averageHourly"));
        assertFalse(text.contains("vacationPay"));
        assertTrue(text.contains("stops before average daily/hourly earnings"));
    }
}
