package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AverageEarningsParagraph7PreEventAccruedWageAuthorityContractTest {
    private static final Path AUTHORITY = Path.of(
            "src/main/java/ru/daniil/shifts/service/" +
                    "AverageEarningsParagraph7PreEventAccruedWageAuthority.java"
    );

    @Test
    void J3B6CConsumesAllProvenParagraph7MoneyAuthorities() throws Exception {
        String source = Files.readString(AUTHORITY);
        assertTrue(source.contains("AverageEarningsParagraph7PreEventHarmfulCompensationService.Resolution"));
        assertTrue(source.contains("AverageEarningsParagraph7PreEventBonusP15Formula.Calculation"));
        assertTrue(source.contains("basePay.basePayAmountMinor()"));
        assertTrue(source.contains("ordinary.ordinaryPremiumAmountMinor()"));
        assertTrue(source.contains("harmful.harmfulAmountMinor()"));
        assertTrue(source.contains("combinationMinor"));
        assertTrue(source.contains("regionalMinor"));
        assertTrue(source.contains("bonus.includedPremiumAmountMinor()"));
    }

    @Test
    void J3B6CRawBonusFactsAreProvenanceOnlyAndCannotDoubleCountP15Money() throws Exception {
        String source = Files.readString(AUTHORITY);
        assertTrue(source.contains("case BONUS_SOURCE"));
        assertTrue(source.contains("Paragraph 15 owns inclusion and amount treatment"));
        assertFalse(source.contains("case BONUS_SOURCE -> Math.addExact"));
        assertTrue(source.contains("bonusP15AmountMinor"));
    }

    @Test
    void J3B6CRequiresOneExactWindowProvenanceAndCurrency() throws Exception {
        String source = Files.readString(AUTHORITY);
        assertTrue(source.contains("AUTHORITY_WINDOW_MISMATCH"));
        assertTrue(source.contains("PROVENANCE_MISMATCH"));
        assertTrue(source.contains("semantic.equals(bonusSemantic)"));
        assertTrue(source.contains("CURRENCY_MISMATCH"));
        assertTrue(source.contains("mergeCurrency"));
    }

    @Test
    void J3B6CFailsClosedWithoutPartialMoneyOrOverflowWrapping() throws Exception {
        String source = Files.readString(AUTHORITY);
        assertTrue(source.contains("TOTAL_OVERFLOW"));
        assertTrue(source.contains("Math.addExact"));
        assertTrue(source.contains("Blocked paragraph-7 accrued-wage authority cannot expose partial money"));
        assertTrue(source.contains("0L"));
    }

    @Test
    void J3B6CStopsBeforeFallbackAndKeepsWorkedVsAccruedFactsSeparate() throws Exception {
        String source = Files.readString(AUTHORITY);
        assertTrue(source.contains("workedTimePresent()"));
        assertTrue(source.contains("accruedWagePresent()"));
        assertTrue(source.contains("workedDayCount()"));
        assertFalse(source.contains("AverageEarningsParagraph8"));
        assertFalse(source.contains("PARAGRAPH_8"));
        assertFalse(source.contains("Repository"));
        assertFalse(source.contains("@Service"));
        assertFalse(source.contains("@Transactional"));
    }
}
