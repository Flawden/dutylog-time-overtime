package ru.daniil.shifts.service;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Map;
import java.util.Objects;

/**
 * Source-locked federal statutory public-holiday policy for RU calendar 2026.
 *
 * <p>This is a legal identity classifier only. It does not classify employee
 * rest days, transferred rest days, regional holidays or payroll money.</p>
 *
 * <p>The policy is deliberately bounded to calendar year 2026. Dates outside
 * that source-locked window fail closed instead of silently reusing a stale
 * legal calendar.</p>
 */
public final class RuFederalStatutoryHolidayPolicy {
    public static final String LEGAL_REGIME =
            "RU_TK_RF_ARTICLE_112_CALENDAR_2026_V1";

    public static final String LEGAL_BASIS =
            "TK_RF_ARTICLE_112";

    public static final String SOURCE_REVISION =
            "TK_RF_197_FZ_RED_2026_05_25";

    public static final String SOURCE_REFERENCE =
            "CONSULTANT_TK_RF_ARTICLE_112";

    public static final LocalDate SUPPORTED_FROM =
            LocalDate.of(2026, 1, 1);

    public static final LocalDate SUPPORTED_TO_EXCLUSIVE =
            LocalDate.of(2027, 1, 1);

    private static final Map<MonthDay, HolidayCode> FEDERAL_HOLIDAYS =
            Map.ofEntries(
                    Map.entry(MonthDay.of(1, 1), HolidayCode.NEW_YEAR_HOLIDAYS),
                    Map.entry(MonthDay.of(1, 2), HolidayCode.NEW_YEAR_HOLIDAYS),
                    Map.entry(MonthDay.of(1, 3), HolidayCode.NEW_YEAR_HOLIDAYS),
                    Map.entry(MonthDay.of(1, 4), HolidayCode.NEW_YEAR_HOLIDAYS),
                    Map.entry(MonthDay.of(1, 5), HolidayCode.NEW_YEAR_HOLIDAYS),
                    Map.entry(MonthDay.of(1, 6), HolidayCode.NEW_YEAR_HOLIDAYS),
                    Map.entry(MonthDay.of(1, 8), HolidayCode.NEW_YEAR_HOLIDAYS),
                    Map.entry(MonthDay.of(1, 7), HolidayCode.ORTHODOX_CHRISTMAS),
                    Map.entry(MonthDay.of(2, 23), HolidayCode.DEFENDER_OF_THE_FATHERLAND_DAY),
                    Map.entry(MonthDay.of(3, 8), HolidayCode.INTERNATIONAL_WOMENS_DAY),
                    Map.entry(MonthDay.of(5, 1), HolidayCode.SPRING_AND_LABOUR_DAY),
                    Map.entry(MonthDay.of(5, 9), HolidayCode.VICTORY_DAY),
                    Map.entry(MonthDay.of(6, 12), HolidayCode.RUSSIA_DAY),
                    Map.entry(MonthDay.of(11, 4), HolidayCode.NATIONAL_UNITY_DAY)
            );

    private RuFederalStatutoryHolidayPolicy() {
    }

    public static Decision classify(
            LocalDate date
    ) {
        Objects.requireNonNull(
                date,
                "RU federal statutory holiday policy requires date"
        );

        if (date.isBefore(SUPPORTED_FROM)
                || !date.isBefore(SUPPORTED_TO_EXCLUSIVE)) {
            throw new UnsupportedOperationException(
                    "RU statutory holiday legal regime is not source-locked for "
                            + date
            );
        }

        HolidayCode holidayCode =
                FEDERAL_HOLIDAYS.get(
                        MonthDay.from(date)
                );

        return new Decision(
                date,
                LEGAL_REGIME,
                LEGAL_BASIS,
                SOURCE_REVISION,
                SOURCE_REFERENCE,
                holidayCode != null,
                holidayCode
        );
    }

    public enum HolidayCode {
        NEW_YEAR_HOLIDAYS,
        ORTHODOX_CHRISTMAS,
        DEFENDER_OF_THE_FATHERLAND_DAY,
        INTERNATIONAL_WOMENS_DAY,
        SPRING_AND_LABOUR_DAY,
        VICTORY_DAY,
        RUSSIA_DAY,
        NATIONAL_UNITY_DAY
    }

    public record Decision(
            LocalDate date,
            String legalRegime,
            String legalBasis,
            String sourceRevision,
            String sourceReference,
            boolean federalNonWorkingPublicHoliday,
            HolidayCode holidayCode
    ) {
        public Decision {
            Objects.requireNonNull(date, "Federal holiday decision requires date");
            requireText(legalRegime, "Federal holiday legal regime is required");
            requireText(legalBasis, "Federal holiday legal basis is required");
            requireText(sourceRevision, "Federal holiday source revision is required");
            requireText(sourceReference, "Federal holiday source reference is required");

            if (federalNonWorkingPublicHoliday != (holidayCode != null)) {
                throw new IllegalArgumentException(
                        "Federal holiday decision identity is inconsistent"
                );
            }
        }

        private static void requireText(
                String value,
                String message
        ) {
            if (value == null
                    || value.isBlank()) {
                throw new IllegalArgumentException(message);
            }
        }
    }
}
