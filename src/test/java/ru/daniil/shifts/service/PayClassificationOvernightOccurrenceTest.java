package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.ActualWorkIntervalRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.repo.ShiftTypeRepository;
import ru.daniil.shifts.repo.UserRepository;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
class PayClassificationOvernightOccurrenceTest {

    @Autowired
    UserRepository users;

    @Autowired
    ShiftTypeRepository shifts;

    @Autowired
    DayEntryRepository days;

    @Autowired
    ActualWorkService actualWork;

    @Autowired
    OvertimeService overtime;

    @Autowired
    PayClassificationService classification;

    @Test
    void exactActualMatchingOneOvernightPlannedShiftMustStayEntirelyOrdinary() {
        AppUser owner =
                users.save(
                        new AppUser(
                                "overnight-occurrence-regression",
                                "{noop}unused"
                        )
                );

        ShiftType night =
                shifts.save(
                        new ShiftType(
                                owner,
                                "Night",
                                11.0,
                                "#123456",
                                false,
                                LocalTime.of(20, 0),
                                LocalTime.of(8, 0),
                                60,
                                11.0
                        )
                );

        LocalDate first =
                LocalDate.of(
                        2026,
                        8,
                        18
                );

        LocalDate second =
                first.plusDays(1);

        DayEntry entry =
                new DayEntry(
                        owner,
                        first
                );

        entry.setShiftType(
                night
        );

        days.saveAndFlush(
                entry
        );

        actualWork.create(
                owner,
                new ActualWorkIntervalRequest(
                        first.toString(),
                        second.toString(),
                        "20:00",
                        "08:00",
                        60,
                        "exact overnight plan"
                )
        );

        var firstDay =
                classification.classify(
                        owner,
                        first
                );

        var secondDay =
                classification.classify(
                        owner,
                        second
                );

        assertEquals(
                180,
                firstDay.workedMinutes()
        );

        assertEquals(
                180,
                firstDay.regularMinutes()
        );

        assertEquals(
                0,
                firstDay.overtimeMinutes()
        );

        assertEquals(
                480,
                secondDay.workedMinutes()
        );

        assertEquals(
                480,
                secondDay.regularMinutes(),
                "The after-midnight tail belongs to the same planned shift occurrence"
        );

        assertEquals(
                0,
                secondDay.overtimeMinutes()
        );

        assertEquals(
                0,
                overtime.balanceMinutes(owner),
                "Exact Actual Work matching one overnight shift must not create overtime"
        );
    }

    @Test
    void overtimeOrdinalContinuesAcrossMidnightWithinOneSourceWorkday() {
        AppUser owner =
                users.save(
                        new AppUser(
                                "overnight-source-workday-ordinal",
                                "{noop}unused"
                        )
                );

        ShiftType night =
                shifts.save(
                        new ShiftType(
                                owner,
                                "Night ordinal",
                                11.0,
                                "#123456",
                                false,
                                LocalTime.of(20, 0),
                                LocalTime.of(8, 0),
                                60,
                                11.0
                        )
                );

        LocalDate first =
                LocalDate.of(
                        2026,
                        8,
                        18
                );

        LocalDate second =
                first.plusDays(1);

        DayEntry entry =
                new DayEntry(
                        owner,
                        first
                );

        entry.setShiftType(
                night
        );

        days.saveAndFlush(
                entry
        );

        /*
         * 18:00 -> 08:00 = 840 raw minutes.
         * Early 60-minute unpaid break leaves 780 factual minutes.
         *
         * first date  = 300
         * second date = 480
         *
         * One source-workday threshold = 660:
         *
         * first  300 REGULAR
         * second 360 REGULAR + 120 OVERTIME
         */
        actualWork.create(
                owner,
                new ActualWorkIntervalRequest(
                        first.toString(),
                        second.toString(),
                        "18:00",
                        "08:00",
                        60,
                        "overnight ordinal continuation"
                )
        );

        var firstDay =
                classification.classify(
                        owner,
                        first
                );

        var secondDay =
                classification.classify(
                        owner,
                        second
                );

        assertEquals(
                300,
                firstDay.workedMinutes()
        );

        assertEquals(
                300,
                firstDay.regularMinutes()
        );

        assertEquals(
                0,
                firstDay.overtimeMinutes()
        );

        assertEquals(
                480,
                secondDay.workedMinutes()
        );

        assertEquals(
                360,
                secondDay.regularMinutes()
        );

        assertEquals(
                120,
                secondDay.overtimeMinutes()
        );

        var overtimeSlice =
                secondDay.slices()
                        .stream()
                        .filter(slice ->
                                slice.overtime()
                        )
                        .findFirst()
                        .orElseThrow();

        assertEquals(
                660,
                overtimeSlice.workedOrdinalStartMinutes(),
                "Overtime ordinal must continue across midnight inside one source workday"
        );

        assertEquals(
                120,
                overtime.balanceMinutes(owner),
                "Only factual minutes above the one source-workday threshold belong in Time Bank"
        );
    }

}
