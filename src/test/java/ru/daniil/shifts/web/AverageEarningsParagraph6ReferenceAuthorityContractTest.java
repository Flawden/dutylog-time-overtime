package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AverageEarningsParagraph6ReferenceAuthorityContractTest {

    private static final Path SERVICE = Path.of(
            "src/main/java/ru/daniil/shifts/service/",
            "AverageEarningsParagraph6ReferenceResolver.java"
    );

    @Test
    void paragraph6UsesRawWageAndWorkedTimeAuthorityInsteadOfFinalMoneyZero() throws Exception {
        String source = compact(Files.readString(SERVICE));

        assertTrue(source.contains("facts.ordinaryCandidateAmountMinor() > 0L"));
        assertTrue(source.contains("facts.premiumSpecialAmountMinor() > 0L"));
        assertTrue(source.contains("completeness.referenceWorkedTime()"));
        assertFalse(source.contains("numeratorAmountMinor()"));
    }

    @Test
    void paragraph6SelectsExactlyOnePrecedingEqualWindow() throws Exception {
        String source = compact(Files.readString(SERVICE));

        assertEquals(1, occurrences(source, "primary.precedingEqual()"));
        assertFalse(source.contains("while ("));
        assertFalse(source.contains("while("));
        assertFalse(source.contains("preceding.precedingEqual()"));
        assertFalse(source.contains("primary.precedingEqual().precedingEqual()"));
    }

    @Test
    void paragraph6PreservesLegalEventDateForPrecedingNumeratorAuthority() throws Exception {
        String source = compact(Files.readString(SERVICE));

        assertTrue(source.contains(
                "numerator.calculate( user, eventDate, preceding, discoveryThroughMonth, provenNoPayrollMonths )"
        ));
        assertFalse(source.contains("eventDate.minus"));
        assertFalse(source.contains("eventDate.plus"));
    }

    @Test
    void exhaustedParagraph6StopsBeforeParagraph7Or8() throws Exception {
        String source = Files.readString(SERVICE);

        assertTrue(source.contains("PARAGRAPH_6_EXHAUSTED"));
        assertFalse(source.contains("PARAGRAPH_7"));
        assertFalse(source.contains("PARAGRAPH_8"));
        assertFalse(source.contains("HistoricalCompensationRateService"));
        assertFalse(source.contains("VacationAveragePrimaryCalculationService"));
    }

    @Test
    void paragraph5WholePeriodEvidenceIsAuditProvenByCoverageNotPresenceAlone() throws Exception {
        String source = compact(Files.readString(SERVICE));

        assertTrue(source.contains("wholePeriodParagraph5Excluded("));
        assertTrue(source.contains("from.isAfter(coveredThrough.plusDays(1))"));
        assertFalse(source.contains("wholePeriodParagraph5Excluded = completeness.paragraph5ExcludedTimePresent()"));
    }

    private static String compact(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    private static int occurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
