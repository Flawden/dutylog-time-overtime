package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.OvertimeCreditCreateRequest;
import ru.daniil.shifts.dto.Dtos.OvertimeUsageCreateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.OvertimeAllocation;
import ru.daniil.shifts.repo.OvertimeAllocationRepository;
import ru.daniil.shifts.repo.OvertimeUsageRepository;
import ru.daniil.shifts.repo.UserRepository;

import java.util.Comparator;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class OvertimeAllocationCreditOffsetTest {

    @Autowired UserRepository users;
    @Autowired OvertimeService overtime;
    @Autowired OvertimeUsageRepository usages;
    @Autowired OvertimeAllocationRepository allocations;

    AppUser owner;

    @BeforeEach
    void setUp() {
        owner =
                users.save(
                        new AppUser(
                                "allocation-offset-"
                                        + UUID.randomUUID()
                                        .toString()
                                        .substring(0, 10),
                                "{noop}unused"
                        )
                );
    }

    @Test
    void fifoMaterializesConsumedOffsetInsideOneCreditAcrossMultipleUsages() {
        overtime.createCredit(
                owner,
                manualCredit(
                        "2026-08-01",
                        2.0
                )
        );

        overtime.createUsage(
                owner,
                new OvertimeUsageCreateRequest(
                        "2026-08-05",
                        0.5,
                        "первые 30 минут"
                )
        );

        overtime.createUsage(
                owner,
                new OvertimeUsageCreateRequest(
                        "2026-08-06",
                        1.0,
                        "следующие 60 минут"
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

        var firstAllocations =
                allocations
                        .findByUsage(
                                usageRows.get(0)
                        )
                        .stream()
                        .sorted(
                                Comparator.comparingLong(
                                        item -> item.getId()
                                )
                        )
                        .toList();

        var secondAllocations =
                allocations
                        .findByUsage(
                                usageRows.get(1)
                        )
                        .stream()
                        .sorted(
                                Comparator.comparingLong(
                                        item -> item.getId()
                                )
                        )
                        .toList();

        assertEquals(
                1,
                firstAllocations.size()
        );

        assertEquals(
                1,
                secondAllocations.size()
        );

        OvertimeAllocation first =
                firstAllocations.get(0);

        OvertimeAllocation second =
                secondAllocations.get(0);

        assertEquals(
                0,
                first.getCreditOffsetStartMinutes()
        );

        assertEquals(
                30,
                first.getAllocatedMinutes()
        );

        assertEquals(
                30,
                second.getCreditOffsetStartMinutes()
        );

        assertEquals(
                60,
                second.getAllocatedMinutes()
        );

        assertEquals(
                first.getCredit().getId(),
                second.getCredit().getId()
        );

        assertEquals(
                90,
                second.getCreditOffsetStartMinutes()
                        + second.getAllocatedMinutes()
        );
    }

    @Test
    void deletingEarlierUsageRebuildsOffsetsFromZeroWithoutChangingFifoRules() {
        overtime.createCredit(
                owner,
                manualCredit(
                        "2026-08-01",
                        2.0
                )
        );

        var firstAccount =
                overtime.createUsage(
                        owner,
                        new OvertimeUsageCreateRequest(
                                "2026-08-05",
                                0.5,
                                "первое списание"
                        )
                );

        overtime.createUsage(
                owner,
                new OvertimeUsageCreateRequest(
                        "2026-08-06",
                        1.0,
                        "второе списание"
                )
        );

        Long firstUsageId =
                firstAccount
                        .usages()
                        .get(0)
                        .id();

        overtime.deleteUsage(
                owner,
                firstUsageId
        );

        var survivingUsage =
                usages
                        .findByOwnerOrderByUsageDateAscIdAsc(
                                owner
                        )
                        .get(0);

        var rebuilt =
                allocations
                        .findByUsage(
                                survivingUsage
                        );

        assertEquals(
                1,
                rebuilt.size()
        );

        assertEquals(
                0,
                rebuilt.get(0)
                        .getCreditOffsetStartMinutes(),
                "replacement FIFO plan must rebase surviving consumption to credit offset zero"
        );

        assertEquals(
                60,
                rebuilt.get(0)
                        .getAllocatedMinutes()
        );
    }

    @Test
    void crossingTwoCreditsRestartsOffsetForTheSecondCredit() {
        overtime.createCredit(
                owner,
                manualCredit(
                        "2026-08-01",
                        1.0
                )
        );

        overtime.createCredit(
                owner,
                manualCredit(
                        "2026-08-02",
                        2.0
                )
        );

        overtime.createUsage(
                owner,
                new OvertimeUsageCreateRequest(
                        "2026-08-05",
                        1.5,
                        "пересекает границу начислений"
                )
        );

        var usage =
                usages
                        .findByOwnerOrderByUsageDateAscIdAsc(
                                owner
                        )
                        .get(0);

        var rows =
                allocations
                        .findByUsage(usage)
                        .stream()
                        .sorted(
                                Comparator.comparing(
                                        item ->
                                                item.getCredit()
                                                        .getWorkDate()
                                )
                        )
                        .toList();

        assertEquals(
                2,
                rows.size()
        );

        assertEquals(
                0,
                rows.get(0)
                        .getCreditOffsetStartMinutes()
        );

        assertEquals(
                60,
                rows.get(0)
                        .getAllocatedMinutes()
        );

        assertEquals(
                0,
                rows.get(1)
                        .getCreditOffsetStartMinutes(),
                "offset belongs to each credit independently"
        );

        assertEquals(
                30,
                rows.get(1)
                        .getAllocatedMinutes()
        );
    }

    private OvertimeCreditCreateRequest manualCredit(
            String date,
            double hours
    ) {
        return new OvertimeCreditCreateRequest(
                date,
                null,
                null,
                null,
                null,
                null,
                hours,
                "offset test"
        );
    }
}
