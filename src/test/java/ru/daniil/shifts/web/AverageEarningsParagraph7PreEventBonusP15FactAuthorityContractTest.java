package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AverageEarningsParagraph7PreEventBonusP15FactAuthorityContractTest {
    private static final Path SERVICE = Path.of(
            "src/main/java/ru/daniil/shifts/service/",
            "AverageEarningsParagraph7PreEventBonusP15FactService.java"
    );

    @Test
    void J3B6AReadsExplicitAverageAndNatureFactAuthorities() throws Exception {
        String source = Files.readString(SERVICE);
        assertTrue(source.contains("averageFacts.resolveForBonusFacts"));
        assertTrue(source.contains("natureFacts.resolveForAverageFacts"));
    }

    @Test
    void J3B6AStartsOnlyFromAlreadyAdmittedBonusSourceFacts() throws Exception {
        String source = Files.readString(SERVICE);
        assertTrue(source.contains("SourceAuthority.BONUS_SOURCE"));
        assertFalse(source.contains("PayrollBonusSourceFactRepository"));
        assertFalse(source.contains("resolveMonth(user"));
    }

    @Test
    void J3B6ADoesNotRunParagraph15PolicyOrFormula() throws Exception {
        String source = Files.readString(SERVICE);
        assertFalse(source.contains("AverageEarningsBonusP15Policy"));
        assertFalse(source.contains("AverageEarningsBonusP15Formula"));
        assertFalse(source.contains("includedPremiumAmountMinor"));
    }

    @Test
    void J3B6ADoesNotInferRewardNatureFromNamesDatesOrMoney() throws Exception {
        String source = Files.readString(SERVICE);
        assertFalse(source.contains("getDisplayName"));
        assertFalse(source.contains("displayName"));
        assertFalse(source.contains("annualResult() ?"));
        assertFalse(source.contains("periodTo().isAfter(periodFrom().plusMonths"));
    }

    @Test
    void J3B6ADoesNotSelectParagraph8OrExposePartialBlockedAuthority() throws Exception {
        String source = Files.readString(SERVICE);
        assertFalse(source.contains("PARAGRAPH_8"));
        assertFalse(source.contains("HistoricalCompensationRateService"));
        assertTrue(source.contains("Blocked paragraph-7 bonus P15 FACTs cannot expose partial authority"));
    }
}
