package ru.daniil.shifts.service;

import ru.daniil.shifts.dto.Dtos.ProductionCalendarDayDto;
import ru.daniil.shifts.model.AppUser;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Objects;
import java.util.Set;

/**
 * Shared Article-120 holiday classification for annual paid vacation.
 *
 * <p>Federal Article-112 non-working holidays are legal baseline truth and
 * cannot be erased by a local production-calendar override. Other dates are
 * classified through the effective production calendar, which is the explicit
 * extension point for officially configured regional/non-federal holidays.
 * Transferred days off are deliberately not holidays.</p>
 */
public final class AnnualPaidVacationHolidayPolicy {
    public static final String RULE_ID = "TK_RF_ARTICLE_120";
    public static final String FEDERAL_ARTICLE_112_AUTHORITY =
            "TK_RF_ARTICLE_112_FEDERAL_HOLIDAY";
    public static final String PRODUCTION_CALENDAR_HOLIDAY_AUTHORITY =
            "PRODUCTION_CALENDAR_NON_WORKING_HOLIDAY";
    public static final String PRODUCTION_CALENDAR_NON_HOLIDAY_AUTHORITY =
            "PRODUCTION_CALENDAR_NON_HOLIDAY";

    private static final String HOLIDAY = "HOLIDAY";
    private static final Set<MonthDay> FEDERAL_NON_WORKING_HOLIDAYS = Set.of(
            MonthDay.of(1, 1),
            MonthDay.of(1, 2),
            MonthDay.of(1, 3),
            MonthDay.of(1, 4),
            MonthDay.of(1, 5),
            MonthDay.of(1, 6),
            MonthDay.of(1, 7),
            MonthDay.of(1, 8),
            MonthDay.of(2, 23),
            MonthDay.of(3, 8),
            MonthDay.of(5, 1),
            MonthDay.of(5, 9),
            MonthDay.of(6, 12),
            MonthDay.of(11, 4)
    );

    private AnnualPaidVacationHolidayPolicy() {
    }

    public static HolidayFact classify(
            ProductionCalendarService productionCalendar,
            AppUser user,
            LocalDate date
    ) {
        Objects.requireNonNull(
                productionCalendar,
                "Annual paid vacation holiday policy requires production calendar"
        );
        Objects.requireNonNull(
                user,
                "Annual paid vacation holiday policy requires user"
        );
        Objects.requireNonNull(
                date,
                "Annual paid vacation holiday policy requires date"
        );

        if (isFederalNonWorkingHoliday(date)) {
            return new HolidayFact(
                    date,
                    true,
                    FEDERAL_ARTICLE_112_AUTHORITY,
                    HOLIDAY
            );
        }

        ProductionCalendarDayDto production =
                productionCalendar.resolvedDay(user, date);
        if (production == null || production.dayKind() == null
                || production.dayKind().isBlank()) {
            throw new IllegalStateException(
                    "Annual paid vacation holiday classification is unavailable for "
                            + date
            );
        }

        boolean holiday = HOLIDAY.equals(production.dayKind());
        return new HolidayFact(
                date,
                holiday,
                holiday
                        ? PRODUCTION_CALENDAR_HOLIDAY_AUTHORITY
                        : PRODUCTION_CALENDAR_NON_HOLIDAY_AUTHORITY,
                production.dayKind()
        );
    }

    static boolean isFederalNonWorkingHoliday(LocalDate date) {
        Objects.requireNonNull(
                date,
                "Federal holiday classification requires date"
        );
        return FEDERAL_NON_WORKING_HOLIDAYS.contains(MonthDay.from(date));
    }

    public record HolidayFact(
            LocalDate date,
            boolean nonWorkingHoliday,
            String authorityCode,
            String dayKind
    ) {
        public HolidayFact {
            Objects.requireNonNull(date, "Holiday fact requires date");
            if (authorityCode == null || authorityCode.isBlank()) {
                throw new IllegalArgumentException(
                        "Holiday fact requires authority code"
                );
            }
            if (dayKind == null || dayKind.isBlank()) {
                throw new IllegalArgumentException(
                        "Holiday fact requires day kind"
                );
            }
            if (nonWorkingHoliday && !HOLIDAY.equals(dayKind)) {
                throw new IllegalArgumentException(
                        "Non-working holiday fact must expose HOLIDAY day kind"
                );
            }
            if (!nonWorkingHoliday && HOLIDAY.equals(dayKind)) {
                throw new IllegalArgumentException(
                        "Non-holiday fact cannot expose HOLIDAY day kind"
                );
            }
        }
    }
}
