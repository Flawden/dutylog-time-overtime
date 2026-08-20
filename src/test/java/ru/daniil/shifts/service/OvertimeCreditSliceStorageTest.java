package ru.daniil.shifts.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.ActualWorkInterval;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.OvertimeCredit;
import ru.daniil.shifts.model.OvertimeCreditSlice;
import ru.daniil.shifts.repo.ActualWorkIntervalRepository;
import ru.daniil.shifts.repo.OvertimeCreditRepository;
import ru.daniil.shifts.repo.OvertimeCreditSliceRepository;
import ru.daniil.shifts.repo.UserRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class OvertimeCreditSliceStorageTest {

    @Autowired UserRepository users;
    @Autowired ActualWorkIntervalRepository actualWork;
    @Autowired OvertimeCreditRepository credits;
    @Autowired OvertimeCreditSliceRepository slices;
    @Autowired EntityManager entityManager;

    @Test
    void storesOrderedHomogeneousExactProvenanceSlices() {
        Fixture fixture = fixture();

        slices.save(
                exactSlice(
                        fixture,
                        0,
                        60,
                        "2026-08-18T21:00",
                        "2026-08-18T22:00",
                        "2026-08-18T18:00:00Z",
                        "2026-08-18T19:00:00Z",
                        false,
                        true,
                        480
                )
        );

        slices.saveAndFlush(
                exactSlice(
                        fixture,
                        60,
                        60,
                        "2026-08-18T22:00",
                        "2026-08-18T23:00",
                        "2026-08-18T19:00:00Z",
                        "2026-08-18T20:00:00Z",
                        true,
                        true,
                        540
                )
        );

        var stored =
                slices.findByCreditOrderByOffsetStartMinutesAscIdAsc(
                        fixture.credit()
                );

        assertEquals(2, stored.size());

        assertEquals(
                120,
                stored.stream()
                        .mapToInt(
                                OvertimeCreditSlice::getMinutes
                        )
                        .sum()
        );

        assertEquals(
                0,
                stored.get(0).getOffsetStartMinutes()
        );

        assertEquals(
                60,
                stored.get(1).getOffsetStartMinutes()
        );

        assertFalse(stored.get(0).isNight());
        assertTrue(stored.get(1).isNight());

        assertTrue(stored.get(0).isHoliday());
        assertTrue(stored.get(1).isHoliday());

        assertEquals(
                480,
                stored.get(0)
                        .getOvertimeOrdinalStartMinutes()
        );

        assertEquals(
                540,
                stored.get(1)
                        .getOvertimeOrdinalStartMinutes()
        );

        assertTrue(
                stored.stream()
                        .allMatch(
                                OvertimeCreditSlice::exact
                        )
        );

        assertTrue(
                stored.stream().allMatch(
                        slice ->
                                fixture.actual().getId().equals(
                                        slice
                                                .getSourceActualWorkInterval()
                                                .getId()
                                )
                )
        );
    }

    @Test
    void rejectsPartialAbsoluteIdentity() {
        Fixture fixture = fixture();

        assertThrows(
                IllegalArgumentException.class,
                () -> new OvertimeCreditSlice(
                        fixture.credit(),
                        0,
                        60,
                        fixture.actual(),
                        fixture.date(),
                        LocalDateTime.parse(
                                "2026-08-18T21:00"
                        ),
                        LocalDateTime.parse(
                                "2026-08-18T22:00"
                        ),
                        Instant.parse(
                                "2026-08-18T18:00:00Z"
                        ),
                        null,
                        "Europe/Moscow",
                        false,
                        true,
                        480
                )
        );
    }

    @Test
    void deletingCreditCascadesItsSlices() {
        Fixture fixture = fixture();

        slices.saveAndFlush(
                exactSlice(
                        fixture,
                        0,
                        120,
                        "2026-08-18T21:00",
                        "2026-08-18T23:00",
                        "2026-08-18T18:00:00Z",
                        "2026-08-18T20:00:00Z",
                        true,
                        true,
                        480
                )
        );

        Long creditId = fixture.credit().getId();

        assertEquals(
                1,
                slices.countByCreditId(creditId)
        );

        credits.delete(fixture.credit());
        credits.flush();
        entityManager.clear();

        assertEquals(
                0,
                slices.countByCreditId(creditId)
        );
    }

    @Test
    void deletingSourceFactCascadesSlicesButKeepsCredit() {
        Fixture fixture = fixture();

        slices.saveAndFlush(
                exactSlice(
                        fixture,
                        0,
                        120,
                        "2026-08-18T21:00",
                        "2026-08-18T23:00",
                        "2026-08-18T18:00:00Z",
                        "2026-08-18T20:00:00Z",
                        true,
                        true,
                        480
                )
        );

        Long actualId = fixture.actual().getId();
        Long creditId = fixture.credit().getId();

        assertEquals(
                1,
                slices.countBySourceActualWorkIntervalId(
                        actualId
                )
        );

        actualWork.delete(fixture.actual());
        actualWork.flush();
        entityManager.clear();

        assertEquals(
                0,
                slices.countBySourceActualWorkIntervalId(
                        actualId
                )
        );

        assertTrue(
                credits.findById(creditId).isPresent(),
                "source cascade must remove provenance only; "
                        + "credit reconciliation owns credit lifecycle"
        );
    }

    private Fixture fixture() {
        LocalDate date =
                LocalDate.of(2026, 8, 18);

        AppUser owner =
                users.save(
                        new AppUser(
                                "slice-"
                                        + UUID.randomUUID()
                                        .toString()
                                        .substring(0, 12),
                                "{noop}unused"
                        )
                );

        ActualWorkInterval actual =
                new ActualWorkInterval(owner);

        actual.setWorkDate(date);
        actual.setEndDate(date);
        actual.setStartTime(
                LocalTime.of(21, 0)
        );
        actual.setEndTime(
                LocalTime.of(23, 0)
        );
        actual.setWorkedMinutes(120);
        actual.setBreakMinutes(0);

        actual.setSourceTimezone(
                "Europe/Moscow"
        );
        actual.setStartInstant(
                Instant.parse(
                        "2026-08-18T18:00:00Z"
                )
        );
        actual.setEndInstant(
                Instant.parse(
                        "2026-08-18T20:00:00Z"
                )
        );
        actual.setIdentityReconstructed(false);

        actual =
                actualWork.saveAndFlush(actual);

        OvertimeCredit credit =
                new OvertimeCredit(
                        owner,
                        date,
                        "Факт дня",
                        2.0,
                        "provenance storage proof"
                );

        credit.setSourceKind(
                "SYSTEM_ACTUAL_WORK"
        );
        credit.setCreditedMinutes(120);
        credit.setPlannedHours(8.0);
        credit.setCalculated(false);

        credit =
                credits.saveAndFlush(credit);

        return new Fixture(
                date,
                actual,
                credit
        );
    }

    private OvertimeCreditSlice exactSlice(
            Fixture fixture,
            int offset,
            int minutes,
            String localStart,
            String localEnd,
            String instantStart,
            String instantEnd,
            boolean night,
            boolean holiday,
            int overtimeOrdinal
    ) {
        return new OvertimeCreditSlice(
                fixture.credit(),
                offset,
                minutes,
                fixture.actual(),
                fixture.date(),
                LocalDateTime.parse(localStart),
                LocalDateTime.parse(localEnd),
                Instant.parse(instantStart),
                Instant.parse(instantEnd),
                "Europe/Moscow",
                night,
                holiday,
                overtimeOrdinal
        );
    }

    private record Fixture(
            LocalDate date,
            ActualWorkInterval actual,
            OvertimeCredit credit
    ) {}
}
