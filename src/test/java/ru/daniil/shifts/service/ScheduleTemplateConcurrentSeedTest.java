package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.ScheduleTemplateRepository;
import ru.daniil.shifts.repo.UserRepository;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class ScheduleTemplateConcurrentSeedTest {

    @Autowired ScheduleTemplateService templates;
    @Autowired ScheduleTemplateRepository templateRepository;
    @Autowired UserRepository users;

    @Test
    void concurrentFirstListsSeedFivePresetsExactlyOnce() throws Exception {
        AppUser owner = users.saveAndFlush(new AppUser(
                "schedule-seed-race-" + System.nanoTime(), "{noop}unused"));
        Long ownerId = owner.getId();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<List<ru.daniil.shifts.dto.Dtos.ScheduleTemplateDto>> call = () -> {
                AppUser concurrentOwner = users.findById(ownerId).orElseThrow();
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return templates.list(concurrentOwner);
            };

            Future<List<ru.daniil.shifts.dto.Dtos.ScheduleTemplateDto>> first = pool.submit(call);
            Future<List<ru.daniil.shifts.dto.Dtos.ScheduleTemplateDto>> second = pool.submit(call);
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();

            assertEquals(5, first.get(10, TimeUnit.SECONDS).size());
            assertEquals(5, second.get(10, TimeUnit.SECONDS).size());
            AppUser persistedOwner = users.findById(ownerId).orElseThrow();
            assertEquals(5, templateRepository.findByOwnerOrderBySortOrderAscIdAsc(persistedOwner).size());
        } finally {
            pool.shutdownNow();
        }
    }
}
