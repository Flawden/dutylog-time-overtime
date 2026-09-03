package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.model.PayrollQualifiedQuantity;
import ru.daniil.shifts.model.PayrollQuantityUnit;
import ru.daniil.shifts.service.OrdinaryWorkPremiumSourceService.OrdinaryPremiumSource;
import ru.daniil.shifts.service.OrdinaryWorkPremiumSourceService.SourceKind;
import ru.daniil.shifts.service.OrdinaryWorkPremiumSourceService.SourcePiece;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PayrollNativeQualifiedQuantityServiceTest {

    private final OrdinaryWorkPremiumSourceService source =
            mock(
                    OrdinaryWorkPremiumSourceService.class
            );

    private final AppUser user =
            new AppUser(
                    "qualified-night-owner",
                    "{noop}unused"
            );

    private final PayrollNativeQualifiedQuantityService service =
            new PayrollNativeQualifiedQuantityService(
                    source
            );

    private final AtomicLong sourceId =
            new AtomicLong(
                    100
            );

    private static final YearMonth MONTH =
            YearMonth.of(
                    2026,
                    8
            );

    @BeforeEach
    void defaultEveryDayToReadyZero() {
        when(
                source.project(
                        eq(user),
                        any(LocalDate.class)
                )
        ).thenAnswer(invocation ->
                ready(
                        invocation.getArgument(1),
                        List.of()
                )
        );
    }

    @Test
    void supportRegistryIsExactlyNightPremium() {
        assertEquals(
                Set.of(PayrollEarningKind.NIGHT_PREMIUM),
                service.supportedKinds()
        );
    }

    @Test
    void supportProbeFailsClosedForNullAndUnprovenKinds() {
        assertTrue(service.supports(PayrollEarningKind.NIGHT_PREMIUM));
        assertFalse(service.supports(PayrollEarningKind.HOLIDAY_PAY));
        assertFalse(service.supports(PayrollEarningKind.HARMFUL_CONDITIONS));
        assertFalse(service.supports(null));
    }

    @Test
    void nightPremiumSumsOnlyNativeNightOrdinaryMinutes() {
        LocalDate first =
                LocalDate.of(
                        2026,
                        8,
                        10
                );

        LocalDate second =
                LocalDate.of(
                        2026,
                        8,
                        11
                );

        when(
                source.project(
                        user,
                        first
                )
        ).thenReturn(
                ready(
                        first,
                        List.of(
                                piece(
                                        first,
                                        60,
                                        true,
                                        false
                                ),
                                piece(
                                        first,
                                        60,
                                        false,
                                        false
                                )
                        )
                )
        );

        when(
                source.project(
                        user,
                        second
                )
        ).thenReturn(
                ready(
                        second,
                        List.of(
                                piece(
                                        second,
                                        60,
                                        true,
                                        false
                                )
                        )
                )
        );

        var result =
                service.resolve(
                        user,
                        MONTH,
                        PayrollEarningKind.NIGHT_PREMIUM
                );

        assertTrue(
                result.ready()
        );

        assertEquals(
                PayrollQualifiedQuantity.minutes(
                        120
                ),
                result.quantity()
        );

        assertTrue(
                result.blockers()
                        .isEmpty()
        );
    }

    @Test
    void nightAndHolidayOverlapStillCountsNightExactlyOnce() {
        LocalDate date =
                LocalDate.of(
                        2026,
                        8,
                        12
                );

        when(
                source.project(
                        user,
                        date
                )
        ).thenReturn(
                ready(
                        date,
                        List.of(
                                piece(
                                        date,
                                        60,
                                        true,
                                        true
                                )
                        )
                )
        );

        var result =
                service.resolve(
                        user,
                        MONTH,
                        PayrollEarningKind.NIGHT_PREMIUM
                );

        assertEquals(
                60L,
                result.quantity()
                        .value()
        );

        assertEquals(
                PayrollQuantityUnit.MINUTES,
                result.quantity()
                        .unit()
        );
    }

    @Test
    void nonNightOrdinaryMinutesDoNotQualifyForNightPremium() {
        LocalDate date =
                LocalDate.of(
                        2026,
                        8,
                        13
                );

        when(
                source.project(
                        user,
                        date
                )
        ).thenReturn(
                ready(
                        date,
                        List.of(
                                piece(
                                        date,
                                        60,
                                        false,
                                        false
                                ),
                                piece(
                                        date,
                                        60,
                                        false,
                                        true
                                )
                        )
                )
        );

        var result =
                service.resolve(
                        user,
                        MONTH,
                        PayrollEarningKind.NIGHT_PREMIUM
                );

        assertEquals(
                PayrollQualifiedQuantity.minutes(
                        0
                ),
                result.quantity()
        );
    }

    @Test
    void blockedSourcePreventsPartialMonthQuantity() {
        LocalDate readyDate =
                LocalDate.of(
                        2026,
                        8,
                        14
                );

        LocalDate blockedDate =
                LocalDate.of(
                        2026,
                        8,
                        15
                );

        when(
                source.project(
                        user,
                        readyDate
                )
        ).thenReturn(
                ready(
                        readyDate,
                        List.of(
                                piece(
                                        readyDate,
                                        60,
                                        true,
                                        false
                                )
                        )
                )
        );

        when(
                source.project(
                        user,
                        blockedDate
                )
        ).thenReturn(
                new OrdinaryPremiumSource(
                        blockedDate,
                        SourceKind.PLAN_DERIVED,
                        480,
                        false,
                        OrdinaryWorkPremiumSourceService.BLOCK_CLOCK_QUANTITY,
                        List.of()
                )
        );

        var result =
                service.resolve(
                        user,
                        MONTH,
                        PayrollEarningKind.NIGHT_PREMIUM
                );

        assertFalse(
                result.ready()
        );

        assertNull(
                result.quantity()
        );

        assertEquals(
                1,
                result.blockers()
                        .size()
        );

        assertEquals(
                blockedDate,
                result.blockers()
                        .get(0)
                        .date()
        );

        assertEquals(
                OrdinaryWorkPremiumSourceService.BLOCK_CLOCK_QUANTITY,
                result.blockers()
                        .get(0)
                        .reason()
        );
    }

    @Test
    void holidayPayRemainsUnsupportedUntilWeekendSemanticsAreProven() {
        IllegalArgumentException error =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                service.resolve(
                                        user,
                                        MONTH,
                                        PayrollEarningKind.HOLIDAY_PAY
                                )
                );

        assertTrue(
                error.getMessage()
                        .contains(
                                "not proven"
                        )
        );
    }

    @Test
    void harmfulConditionsRemainUnsupportedUntilTheirOwnTimeSourceIsProven() {
        IllegalArgumentException error =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                service.resolve(
                                        user,
                                        MONTH,
                                        PayrollEarningKind.HARMFUL_CONDITIONS
                                )
                );

        assertTrue(
                error.getMessage()
                        .contains(
                                "not proven"
                        )
        );
    }

    private OrdinaryPremiumSource ready(
            LocalDate date,
            List<SourcePiece> pieces
    ) {
        int minutes =
                pieces.stream()
                        .mapToInt(
                                SourcePiece::minutes
                        )
                        .sum();

        return new OrdinaryPremiumSource(
                date,
                SourceKind.EXPLICIT,
                minutes,
                true,
                null,
                pieces
        );
    }

    private SourcePiece piece(
            LocalDate date,
            int minutes,
            boolean night,
            boolean holiday
    ) {
        long id =
                sourceId.incrementAndGet();

        Instant start =
                date.atTime(
                                20,
                                0
                        )
                        .toInstant(
                                java.time.ZoneOffset.UTC
                        );

        Instant end =
                start.plus(
                        Duration.ofMinutes(
                                minutes
                        )
                );

        return new SourcePiece(
                date,
                SourceKind.EXPLICIT,
                id,
                null,
                start,
                end,
                "UTC",
                minutes,
                night,
                holiday
        );
    }

    @Test
    void combinationRemainsUnsupportedUntilExternalEpisodeAndReferenceSemanticsAreProven() {
        IllegalArgumentException error =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                service.resolve(
                                        user,
                                        YearMonth.of(2026, 7),
                                        PayrollEarningKind.COMBINATION
                                )
                );

        assertEquals(
                "Native qualified quantity is not proven for COMBINATION",
                error.getMessage()
        );
    }

}
