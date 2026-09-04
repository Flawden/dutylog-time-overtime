package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RuFederalStatutoryHolidayPolicyTest {

    @Test
    void article112FederalDatesAreClassifiedWithStableIdentity() {
        Map<LocalDate, RuFederalStatutoryHolidayPolicy.HolidayCode> expected =
                Map.ofEntries(
                        Map.entry(LocalDate.of(2026, 1, 1),
                                RuFederalStatutoryHolidayPolicy.HolidayCode.NEW_YEAR_HOLIDAYS),
                        Map.entry(LocalDate.of(2026, 1, 2),
                                RuFederalStatutoryHolidayPolicy.HolidayCode.NEW_YEAR_HOLIDAYS),
                        Map.entry(LocalDate.of(2026, 1, 3),
                                RuFederalStatutoryHolidayPolicy.HolidayCode.NEW_YEAR_HOLIDAYS),
                        Map.entry(LocalDate.of(2026, 1, 4),
                                RuFederalStatutoryHolidayPolicy.HolidayCode.NEW_YEAR_HOLIDAYS),
                        Map.entry(LocalDate.of(2026, 1, 5),
                                RuFederalStatutoryHolidayPolicy.HolidayCode.NEW_YEAR_HOLIDAYS),
                        Map.entry(LocalDate.of(2026, 1, 6),
                                RuFederalStatutoryHolidayPolicy.HolidayCode.NEW_YEAR_HOLIDAYS),
                        Map.entry(LocalDate.of(2026, 1, 8),
                                RuFederalStatutoryHolidayPolicy.HolidayCode.NEW_YEAR_HOLIDAYS),
                        Map.entry(LocalDate.of(2026, 1, 7),
                                RuFederalStatutoryHolidayPolicy.HolidayCode.ORTHODOX_CHRISTMAS),
                        Map.entry(LocalDate.of(2026, 2, 23),
                                RuFederalStatutoryHolidayPolicy.HolidayCode.DEFENDER_OF_THE_FATHERLAND_DAY),
                        Map.entry(LocalDate.of(2026, 3, 8),
                                RuFederalStatutoryHolidayPolicy.HolidayCode.INTERNATIONAL_WOMENS_DAY),
                        Map.entry(LocalDate.of(2026, 5, 1),
                                RuFederalStatutoryHolidayPolicy.HolidayCode.SPRING_AND_LABOUR_DAY),
                        Map.entry(LocalDate.of(2026, 5, 9),
                                RuFederalStatutoryHolidayPolicy.HolidayCode.VICTORY_DAY),
                        Map.entry(LocalDate.of(2026, 6, 12),
                                RuFederalStatutoryHolidayPolicy.HolidayCode.RUSSIA_DAY),
                        Map.entry(LocalDate.of(2026, 11, 4),
                                RuFederalStatutoryHolidayPolicy.HolidayCode.NATIONAL_UNITY_DAY)
                );

        expected.forEach((date, code) -> {
            var decision =
                    RuFederalStatutoryHolidayPolicy.classify(date);

            assertTrue(decision.federalNonWorkingPublicHoliday());
            assertEquals(code, decision.holidayCode());
            assertEquals(
                    RuFederalStatutoryHolidayPolicy.LEGAL_REGIME,
                    decision.legalRegime()
            );
            assertEquals(
                    RuFederalStatutoryHolidayPolicy.LEGAL_BASIS,
                    decision.legalBasis()
            );
        });
    }

    @Test
    void ordinary2026DateIsNotFederalHoliday() {
        var decision =
                RuFederalStatutoryHolidayPolicy.classify(
                        LocalDate.of(2026, 7, 15)
                );

        assertFalse(decision.federalNonWorkingPublicHoliday());
        assertNull(decision.holidayCode());
    }

    @Test
    void transferredRestDaysAreNotStatutoryFederalHolidays() {
        var januaryTransfer =
                RuFederalStatutoryHolidayPolicy.classify(
                        LocalDate.of(2026, 1, 9)
                );
        var decemberTransfer =
                RuFederalStatutoryHolidayPolicy.classify(
                        LocalDate.of(2026, 12, 31)
                );

        assertFalse(januaryTransfer.federalNonWorkingPublicHoliday());
        assertFalse(decemberTransfer.federalNonWorkingPublicHoliday());
    }

    @Test
    void unsupportedPastYearFailsClosed() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> RuFederalStatutoryHolidayPolicy.classify(
                        LocalDate.of(2025, 12, 31)
                )
        );
    }

    @Test
    void unsupportedFutureYearFailsClosed() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> RuFederalStatutoryHolidayPolicy.classify(
                        LocalDate.of(2027, 1, 1)
                )
        );
    }

    @Test
    void nullDateIsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> RuFederalStatutoryHolidayPolicy.classify(null)
        );
    }
}
