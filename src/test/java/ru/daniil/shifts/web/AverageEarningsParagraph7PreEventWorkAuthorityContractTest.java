package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AverageEarningsParagraph7PreEventWorkAuthorityContractTest {

    private static final Path SERVICE = Path.of(
            "src/main/java/ru/daniil/shifts/service/",
            "AverageEarningsParagraph7PreEventWorkFactService.java"
    );

    @Test
    void paragraph7ReadsOnlyMonthStartThroughDayBeforeEvent() throws Exception {
        String source = compact(Files.readString(SERVICE));

        assertTrue(source.contains("YearMonth eventMonth = YearMonth.from(eventDate)"));
        assertTrue(source.contains("LocalDate periodFrom = eventMonth.atDay(1)"));
        assertTrue(source.contains("LocalDate periodTo = eventDate.minusDays(1)"));
        assertTrue(source.contains("timeCompensation.payrollSource( user, periodFrom, periodTo )"));
    }

    @Test
    void eventDateAndFutureDaysCannotEnterParagraph7Facts() throws Exception {
        String source = compact(Files.readString(SERVICE));

        assertTrue(source.contains("!date.isBefore(cutoffExclusive)"));
        assertTrue(source.contains("cutoffExclusive.equals(eventDate)"));
        assertFalse(source.contains("eventDate.plusDays"));
        assertFalse(source.contains("atEndOfMonth"));
    }

    @Test
    void paragraph7WorkFactLayerDoesNotPriceWagesOrAverageEarnings() throws Exception {
        String source = Files.readString(SERVICE);

        assertFalse(source.contains("CompensationCalculationService"));
        assertFalse(source.contains("HistoricalCompensationRateService"));
        assertFalse(source.contains("PayrollHistoricalSemanticEarningsService"));
        assertFalse(source.contains("amountMinor"));
        assertFalse(source.contains("averageDaily"));
        assertFalse(source.contains("vacationPay"));
    }

    @Test
    void paragraph7WorkFactLayerDoesNotSelectParagraph8() throws Exception {
        String source = Files.readString(SERVICE);

        assertFalse(source.contains("PARAGRAPH_8"));
        assertFalse(source.contains("Paragraph8"));
        assertFalse(source.contains("HistoricalCompensationRateService"));
    }

    @Test
    void paidAbsenceMinutesCannotMasqueradeAsActuallyWorkedTime() throws Exception {
        String source = compact(Files.readString(SERVICE));

        assertTrue(source.contains("if (day.workedMinutes() > 0)"));
        assertFalse(source.contains("day.vacationMinutes() > 0"));
        assertFalse(source.contains("day.sickMinutes() > 0"));
        assertTrue(source.contains("workedMinutes != source.workedMinutes()"));
    }

    private static String compact(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }
}
