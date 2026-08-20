package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.ActualWorkIntervalRequest;
import ru.daniil.shifts.dto.Dtos.OvertimeCreditCreateRequest;
import ru.daniil.shifts.dto.Dtos.OvertimeUsageCreateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.OvertimeAllocation;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.repo.OvertimeAllocationRepository;
import ru.daniil.shifts.repo.OvertimeCreditRepository;
import ru.daniil.shifts.repo.OvertimeCreditSliceRepository;
import ru.daniil.shifts.repo.OvertimeUsageRepository;
import ru.daniil.shifts.repo.ShiftTypeRepository;
import ru.daniil.shifts.repo.UserRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class OvertimeAllocationProvenanceIntersectionTest {

    @Autowired UserRepository users;
    @Autowired ShiftTypeRepository shifts;
    @Autowired DayEntryRepository days;
    @Autowired ActualWorkService actualWork;
    @Autowired OvertimeService overtime;
    @Autowired OvertimeUsageRepository usages;
    @Autowired OvertimeAllocationRepository allocations;
    @Autowired OvertimeCreditRepository credits;
    @Autowired OvertimeCreditSliceRepository slices;
    @Autowired OvertimeAllocationProvenanceService provenance;

    AppUser owner;
    ShiftType day;
    LocalDate date;

    @BeforeEach
    void setUp() {
        owner =
                users.save(
                        new AppUser(
                                "provenance-intersection-"
                                        + UUID.randomUUID()
                                        .toString()
                                        .substring(0, 10),
                                "{noop}unused"
                        )
                );

        day =
                shifts.save(
                        new ShiftType(
                                owner,
                                "День",
                                8.0,
                                "#123456",
                                false,
                                LocalTime.of(8, 30),
                                LocalTime.of(17, 0),
                                30,
                                8.0
                        )
                );

        date =
                LocalDate.parse(
                        "2026-08-18"
                );
    }

    @Test
    void partialAllocationCrossesTwoClassificationSlicesExactly() {
        DayEntry entry =
                new DayEntry(
                        owner,
                        date
                );

        entry.setShiftType(day);
        days.saveAndFlush(entry);

        /*
         * 12:30-23:00, break 30 consumed from earliest minutes
         * => net 13:00-23:00 = 600 min.
         *
         * Ordinary threshold 480:
         * 21:00-22:00 = OT
         * 22:00-23:00 = OT + NIGHT
         *
         * Credit provenance:
         * [0..60)   non-night OT
         * [60..120) night OT
         */
        var saved =
                actualWork.create(
                        owner,
                        new ActualWorkIntervalRequest(
                                date.toString(),
                                "12:30",
                                "23:00",
                                30,
                                "intersection proof"
                        )
                );

        var credit =
                credits
                        .findByOwnerAndWorkDateAndSourceKind(
                                owner,
                                date,
                                "SYSTEM_ACTUAL_WORK"
                        )
                        .orElseThrow();

        assertEquals(
                120,
                credit.getCreditedMinutes()
        );

        assertEquals(
                2,
                slices
                        .findByCreditOrderByOffsetStartMinutesAscIdAsc(
                                credit
                        )
                        .size()
        );

        /*
         * Consume first 30 minutes, then another 60.
         *
         * The second allocation therefore owns credit range [30..90):
         * 30 min from the first provenance slice,
         * 30 min from the NIGHT provenance slice.
         */
        overtime.createUsage(
                owner,
                new OvertimeUsageCreateRequest(
                        "2026-08-20",
                        0.5,
                        "consume first thirty"
                )
        );

        overtime.createUsage(
                owner,
                new OvertimeUsageCreateRequest(
                        "2026-08-21",
                        1.0,
                        "cross provenance boundary"
                )
        );

        var usageRows =
                usages
                        .findByOwnerOrderByUsageDateAscIdAsc(
                                owner
                        );

        assertEquals(
                2,
                usageRows.size()
        );

        var secondAllocations =
                allocations
                        .findByUsage(
                                usageRows.get(1)
                        );

        assertEquals(
                1,
                secondAllocations.size()
        );

        OvertimeAllocation allocation =
                secondAllocations.get(0);

        assertEquals(
                30,
                allocation.getCreditOffsetStartMinutes()
        );

        assertEquals(
                60,
                allocation.getAllocatedMinutes()
        );

        var resolved =
                provenance.resolve(
                        allocation
                );

        assertTrue(
                resolved.provenanceKnown()
        );

        assertEquals(
                60,
                resolved.coveredMinutes()
        );

        assertEquals(
                2,
                resolved.pieces().size()
        );

        var first =
                resolved.pieces().get(0);

        var second =
                resolved.pieces().get(1);

        assertEquals(
                30,
                first.creditOffsetStartMinutes()
        );

        assertEquals(
                30,
                first.minutes()
        );

        assertEquals(
                saved.id(),
                first.sourceActualWorkIntervalId()
        );

        assertFalse(first.night());
        assertFalse(first.holiday());
        assertTrue(first.exact());

        assertEquals(
                LocalTime.of(21, 30),
                first.sourceStartAt()
                        .toLocalTime()
        );

        assertEquals(
                LocalTime.of(22, 0),
                first.sourceEndAt()
                        .toLocalTime()
        );

        assertEquals(
                510,
                first.overtimeOrdinalStartMinutes()
        );

        assertEquals(
                60,
                second.creditOffsetStartMinutes()
        );

        assertEquals(
                30,
                second.minutes()
        );

        assertEquals(
                saved.id(),
                second.sourceActualWorkIntervalId()
        );

        assertTrue(second.night());
        assertFalse(second.holiday());
        assertTrue(second.exact());

        assertEquals(
                LocalTime.of(22, 0),
                second.sourceStartAt()
                        .toLocalTime()
        );

        assertEquals(
                LocalTime.of(22, 30),
                second.sourceEndAt()
                        .toLocalTime()
        );

        assertEquals(
                540,
                second.overtimeOrdinalStartMinutes()
        );

        assertEquals(
                60,
                resolved.pieces()
                        .stream()
                        .mapToInt(
                                item -> item.minutes()
                        )
                        .sum()
        );
    }

    @Test
    void manualCreditKeepsUnknownProvenanceInsteadOfInventingSource() {
        overtime.createCredit(
                owner,
                new OvertimeCreditCreateRequest(
                        date.toString(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        2.0,
                        "manual provenance is unknown"
                )
        );

        overtime.createUsage(
                owner,
                new OvertimeUsageCreateRequest(
                        date.plusDays(1).toString(),
                        1.0,
                        "consume manual credit"
                )
        );

        var usage =
                usages
                        .findByOwnerOrderByUsageDateAscIdAsc(
                                owner
                        )
                        .get(0);

        var allocation =
                allocations
                        .findByUsage(usage)
                        .get(0);

        var resolved =
                provenance.resolve(
                        allocation
                );

        assertFalse(
                resolved.provenanceKnown()
        );

        assertEquals(
                0,
                resolved.coveredMinutes()
        );

        assertTrue(
                resolved.pieces().isEmpty()
        );

        assertEquals(
                60,
                resolved.allocatedMinutes()
        );
    }

    @Test
    void partialStoredProvenanceFailsClosed() {
        DayEntry entry =
                new DayEntry(
                        owner,
                        date
                );

        entry.setShiftType(day);
        days.saveAndFlush(entry);

        actualWork.create(
                owner,
                new ActualWorkIntervalRequest(
                        date.toString(),
                        "12:30",
                        "23:00",
                        30,
                        "corruption proof"
                )
        );

        var credit =
                credits
                        .findByOwnerAndWorkDateAndSourceKind(
                                owner,
                                date,
                                "SYSTEM_ACTUAL_WORK"
                        )
                        .orElseThrow();

        var stored =
                slices
                        .findByCreditOrderByOffsetStartMinutesAscIdAsc(
                                credit
                        );

        assertEquals(
                2,
                stored.size()
        );

        /*
         * Simulate impossible partial storage:
         * credit still says 120, but only [60..120) remains.
         */
        slices.delete(
                stored.get(0)
        );
        slices.flush();

        overtime.createUsage(
                owner,
                new OvertimeUsageCreateRequest(
                        date.plusDays(1).toString(),
                        1.0,
                        "must fail closed when resolving"
                )
        );

        var usage =
                usages
                        .findByOwnerOrderByUsageDateAscIdAsc(
                                owner
                        )
                        .get(0);

        var allocation =
                allocations
                        .findByUsage(usage)
                        .get(0);

        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class,
                        () -> provenance.resolve(
                                allocation
                        )
                );

        assertTrue(
                error.getMessage()
                        .contains(
                                "covers"
                        )
        );
    }
}
