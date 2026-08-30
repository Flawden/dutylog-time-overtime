package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AverageEarningsParagraph7PreEventSemanticWageAuthorityContractTest {
    private static final Path SERVICE = Path.of(
            "src/main/java/ru/daniil/shifts/service/",
            "AverageEarningsParagraph7PreEventSemanticWageFactService.java"
    );

    @Test
    void J3B3ReadsOnlyExplicitDatedSemanticSourceAuthorities() throws Exception {
        String source = Files.readString(SERVICE);
        assertTrue(source.contains("combinationFacts.resolveMonth(user, eventMonth)"));
        assertTrue(source.contains("regionalFacts.resolveMonth(user, eventMonth)"));
        assertTrue(source.contains("bonusFacts.resolveMonth(user, eventMonth)"));
    }

    @Test
    void eventDateIsExclusiveAndCrossingFactsAreNeverProrated() throws Exception {
        String source = Files.readString(SERVICE);
        assertTrue(source.contains("!factFrom.isBefore(eventDate)"));
        assertTrue(source.contains("!factTo.isBefore(eventDate)"));
        assertTrue(source.contains("SOURCE_PERIOD_CROSSES_EVENT"));
        assertFalse(source.contains("prorate("));
    }

    @Test
    void J3B3DoesNotUseMonthlyPayrollPreviewOrFrozenSnapshotMoney() throws Exception {
        String source = Files.readString(SERVICE);
        assertFalse(source.contains("PayrollService"));
        assertFalse(source.contains("PayrollSnapshot"));
        assertFalse(source.contains("PayrollHistoricalSemanticEarningsService"));
        assertFalse(source.contains("PayrollOrdinaryPremiumPreviewService"));
    }

    @Test
    void explicitFactLayerDoesNotProduceAggregateParagraph7Money() throws Exception {
        String source = Files.readString(SERVICE);
        assertFalse(source.contains("totalAmountMinor()"));
        assertFalse(source.contains("numeratorAmountMinor"));
        assertFalse(source.contains("AverageEarningsParagraph5MoneyPolicy"));
    }

    @Test
    void J3B3DoesNotSelectParagraph8OrTreatBasePayZeroAsNoWage() throws Exception {
        String source = Files.readString(SERVICE);
        assertFalse(source.contains("AverageEarningsParagraph8"));
        assertFalse(source.contains("basePayAmountMinor() == 0"));
        assertFalse(source.contains("basePayAmountMinor() > 0"));
    }
}
