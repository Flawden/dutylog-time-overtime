package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.EmploymentPeriod;
import ru.daniil.shifts.repo.EmploymentPeriodRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmploymentHistoryServiceTest {

    private EmploymentPeriodRepository periods;
    private EmploymentHistoryService service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        periods =
                mock(
                        EmploymentPeriodRepository.class
                );

        user =
                mock(
                        AppUser.class
                );

        service =
                new EmploymentHistoryService(
                        periods
                );
    }

    @Test
    void emptyHistoryIsUnconfiguredAndDoesNotInferEmployment() {
        when(
                periods
                        .findByOwnerOrderByStartDateAscIdAsc(
                                user
                        )
        ).thenReturn(
                List.of()
        );

        var result =
                service.resolve(
                        user,
                        LocalDate.of(
                                2025,
                                8,
                                1
                        ),
                        LocalDate.of(
                                2026,
                                7,
                                31
                        )
                );

        assertFalse(
                result.configured()
        );

        assertFalse(
                result.ready()
        );

        assertTrue(
                result.slices().isEmpty()
        );
    }

    @Test
    void configuredHistoryClipsClosedAndOpenEmploymentPeriods() {
        EmploymentPeriod first =
                period(
                        10L,
                        LocalDate.of(
                                2025,
                                7,
                                15
                        ),
                        LocalDate.of(
                                2025,
                                12,
                                31
                        )
                );

        EmploymentPeriod second =
                period(
                        11L,
                        LocalDate.of(
                                2026,
                                2,
                                10
                        ),
                        null
                );

        when(
                periods
                        .findByOwnerOrderByStartDateAscIdAsc(
                                user
                        )
        ).thenReturn(
                List.of(
                        first,
                        second
                )
        );

        var result =
                service.resolve(
                        user,
                        LocalDate.of(
                                2025,
                                8,
                                1
                        ),
                        LocalDate.of(
                                2026,
                                7,
                                31
                        )
                );

        assertTrue(
                result.ready()
        );

        assertEquals(
                2,
                result.slices().size()
        );

        var firstSlice =
                result.slices().get(0);

        assertEquals(
                10L,
                firstSlice.periodId()
        );

        assertEquals(
                LocalDate.of(
                        2025,
                        7,
                        15
                ),
                firstSlice.sourceFrom()
        );

        assertEquals(
                LocalDate.of(
                        2025,
                        8,
                        1
                ),
                firstSlice.overlapFrom()
        );

        assertEquals(
                LocalDate.of(
                        2025,
                        12,
                        31
                ),
                firstSlice.overlapTo()
        );

        var secondSlice =
                result.slices().get(1);

        assertNull(
                secondSlice.sourceTo()
        );

        assertEquals(
                LocalDate.of(
                        2026,
                        2,
                        10
                ),
                secondSlice.overlapFrom()
        );

        assertEquals(
                LocalDate.of(
                        2026,
                        7,
                        31
                ),
                secondSlice.overlapTo()
        );
    }

    @Test
    void overlappingPersistedHistoryFailsClosed() {
        EmploymentPeriod first =
                period(
                        20L,
                        LocalDate.of(
                                2025,
                                1,
                                1
                        ),
                        LocalDate.of(
                                2025,
                                12,
                                31
                        )
                );

        EmploymentPeriod second =
                period(
                        21L,
                        LocalDate.of(
                                2025,
                                12,
                                31
                        ),
                        null
                );

        when(
                periods
                        .findByOwnerOrderByStartDateAscIdAsc(
                                user
                        )
        ).thenReturn(
                List.of(
                        first,
                        second
                )
        );

        assertThrows(
                IllegalStateException.class,
                () ->
                        service.resolve(
                                user,
                                LocalDate.of(
                                        2025,
                                        1,
                                        1
                                ),
                                LocalDate.of(
                                        2026,
                                        1,
                                        1
                                )
                        )
        );
    }

    @Test
    void createRejectsOverlapButAllowsAdjacentPeriod() {
        EmploymentPeriod existing =
                period(
                        30L,
                        LocalDate.of(
                                2025,
                                1,
                                1
                        ),
                        LocalDate.of(
                                2025,
                                1,
                                31
                        )
                );

        when(
                periods
                        .findByOwnerOrderByStartDateAscIdAsc(
                                user
                        )
        ).thenReturn(
                List.of(
                        existing
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.create(
                                user,
                                LocalDate.of(
                                        2025,
                                        1,
                                        31
                                ),
                                LocalDate.of(
                                        2025,
                                        2,
                                        10
                                )
                        )
        );

        when(
                periods.saveAndFlush(
                        any(
                                EmploymentPeriod.class
                        )
                )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(
                                0
                        )
        );

        EmploymentPeriod created =
                service.create(
                        user,
                        LocalDate.of(
                                2025,
                                2,
                                1
                        ),
                        LocalDate.of(
                                2025,
                                3,
                                1
                        )
                );

        assertEquals(
                LocalDate.of(
                        2025,
                        2,
                        1
                ),
                created.getStartDate()
        );
    }

    @Test
    void updateExcludesOwnIdentityButRejectsAnotherPeriod() {
        EmploymentPeriod target =
                period(
                        40L,
                        LocalDate.of(
                                2025,
                                1,
                                1
                        ),
                        LocalDate.of(
                                2025,
                                1,
                                31
                        )
                );

        EmploymentPeriod other =
                period(
                        41L,
                        LocalDate.of(
                                2025,
                                3,
                                1
                        ),
                        null
                );

        when(
                periods.findByOwnerAndId(
                        user,
                        40L
                )
        ).thenReturn(
                Optional.of(
                        target
                )
        );

        when(
                periods
                        .findByOwnerOrderByStartDateAscIdAsc(
                                user
                        )
        ).thenReturn(
                List.of(
                        target,
                        other
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.update(
                                user,
                                40L,
                                LocalDate.of(
                                        2025,
                                        1,
                                        1
                                ),
                                LocalDate.of(
                                        2025,
                                        3,
                                        1
                                )
                        )
        );

        verify(
                target,
                never()
        ).update(
                any(),
                any()
        );
    }

    @Test
    void deleteRequiresOwnedEmploymentPeriod() {
        when(
                periods.findByOwnerAndId(
                        user,
                        50L
                )
        ).thenReturn(
                Optional.empty()
        );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.delete(
                                user,
                                50L
                        )
        );

        verify(
                periods,
                never()
        ).delete(
                any()
        );
    }

    @Test
    void invalidRangesAndEntityDatesFailClosed() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.resolve(
                                user,
                                LocalDate.of(
                                        2026,
                                        2,
                                        1
                                ),
                                LocalDate.of(
                                        2026,
                                        1,
                                        1
                                )
                        )
        );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new EmploymentPeriod(
                                user,
                                LocalDate.of(
                                        2026,
                                        2,
                                        1
                                ),
                                LocalDate.of(
                                        2026,
                                        1,
                                        31
                                )
                        )
        );
    }

    private EmploymentPeriod period(
            long id,
            LocalDate startDate,
            LocalDate endDate
    ) {
        EmploymentPeriod period =
                mock(
                        EmploymentPeriod.class
                );

        when(
                period.getId()
        ).thenReturn(
                id
        );

        when(
                period.getStartDate()
        ).thenReturn(
                startDate
        );

        when(
                period.getEndDate()
        ).thenReturn(
                endDate
        );

        return period;
    }
}
