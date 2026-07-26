package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.OvertimeAccountDto;
import ru.daniil.shifts.dto.Dtos.OvertimeCreditCreateRequest;
import ru.daniil.shifts.dto.Dtos.OvertimeCreditRowDto;
import ru.daniil.shifts.dto.Dtos.OvertimeCreditUpdateRequest;
import ru.daniil.shifts.dto.Dtos.OvertimeUsageCreateRequest;
import ru.daniil.shifts.dto.Dtos.OvertimeUsageUpdateRequest;
import ru.daniil.shifts.dto.Dtos.LegacyOvertimeMigrationRequest;
import ru.daniil.shifts.model.OvertimeCredit;
import ru.daniil.shifts.repo.OvertimeCreditRepository;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты бухгалтерии переработок — самой хрупкой логики проекта:
 * разбивка интервалов по датам, защита от пересечений, FIFO-списание.
 *
 * Каждый тест защищает конкретное обещание системы. Если тест упал —
 * сломано обещание, а не «какой-то тест».
 */
@SpringBootTest
@Transactional // каждый тест откатывается, база между тестами чистая
class OvertimeServiceTest {

    @Autowired OvertimeService overtime;
    @Autowired UserRepository users;
    @Autowired OvertimeCreditRepository credits;

    AppUser user;

    @BeforeEach
    void setUp() {
        user = users.save(new AppUser("test-user", "{noop}irrelevant"));
    }

    /* ── Помощники: короткие фабрики запросов ── */

    private OvertimeCreditCreateRequest manual(String date, double hours) {
        return new OvertimeCreditCreateRequest(date, null, null, null, null, null, hours, "тест");
    }

    private OvertimeCreditCreateRequest interval(String date, String start, String end, int breakMin, double planned) {
        return new OvertimeCreditCreateRequest(date, null, start, end, breakMin, planned, null, "тест");
    }

    private OvertimeCreditRowDto rowByDate(OvertimeAccountDto acc, String date) {
        return acc.credits().stream()
                .filter(c -> date.equals(c.workedDate()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("нет начисления на " + date));
    }

    /* ── Разбивка интервалов ── */

    @Test
    void ночнойИнтервалРежетсяПоПолуночиИОбедВычитаетсяИзРаннихМинут() {
        // 03.07 20:00 → 04.07 08:00, обед 60 мин.
        // До полуночи 4 часа, обед снимается оттуда: 3 ч на 03.07 и 8 ч на 04.07.
        OvertimeAccountDto acc = overtime.createCredit(user,
                interval("2026-07-03", "2026-07-03T20:00", "2026-07-04T08:00", 60, 0.0));

        assertEquals(2, acc.credits().size(), "ночь должна разложиться на две даты");
        assertEquals(3.0, rowByDate(acc, "2026-07-03").hours(), 0.001);
        assertEquals(8.0, rowByDate(acc, "2026-07-04").hours(), 0.001);
        assertEquals(11.0, acc.balanceHours(), 0.001);
    }

    @Test
    void ровныеСуткиХранятсяПополамНоПроецируютсяПоКалендарнымДням() {
        // Историческая storage-модель сохраняет точные сутки двумя source-credit по 12 часов.
        // v27.12 поверх них строит пользовательскую проекцию по реальной локальной полуночи:
        // 03.07 08:00 → 24:00 = 16 ч, 04.07 00:00 → 08:00 = 8 ч.
        OvertimeAccountDto acc = overtime.createCredit(user,
                interval("2026-07-03", "2026-07-03T08:00", "2026-07-04T08:00", 0, 0.0));

        List<OvertimeCredit> sourceCredits = credits.findByOwnerOrderByWorkDateAscIdAsc(user);
        assertEquals(2, sourceCredits.size(), "source-credit по-прежнему должны храниться двумя половинами");
        assertTrue(sourceCredits.stream().allMatch(credit -> credit.getCreditedMinutes() == 12 * 60));

        double firstDay = acc.credits().stream()
                .filter(row -> "2026-07-03".equals(row.workedDate()))
                .mapToDouble(OvertimeCreditRowDto::hours)
                .sum();
        double secondDay = acc.credits().stream()
                .filter(row -> "2026-07-04".equals(row.workedDate()))
                .mapToDouble(OvertimeCreditRowDto::hours)
                .sum();

        assertEquals(16.0, firstDay, 0.001);
        assertEquals(8.0, secondDay, 0.001);
        assertEquals(24.0, acc.credits().stream().mapToDouble(OvertimeCreditRowDto::hours).sum(), 0.001);
        assertEquals(24.0, acc.balanceHours(), 0.001);
    }


    @Test
    void dailyProjectionRedistributesExactMinutesWithoutMovingFifo() {
        user.setWorkTimezone("Europe/Moscow");
        users.save(user);

        OvertimeAccountDto created = overtime.createCredit(user,
                interval("2026-07-03", "2026-07-03T22:00", "2026-07-04T02:00", 0, 0.0));
        assertEquals(2.0, created.credits().stream()
                .filter(row -> "2026-07-03".equals(row.workedDate()))
                .mapToDouble(OvertimeCreditRowDto::hours).sum(), 0.001);
        assertEquals(2.0, created.credits().stream()
                .filter(row -> "2026-07-04".equals(row.workedDate()))
                .mapToDouble(OvertimeCreditRowDto::hours).sum(), 0.001);

        OvertimeAccountDto withUsage = overtime.createUsage(user,
                new OvertimeUsageCreateRequest("2026-07-05", 3.0, "projection FIFO"));
        List<String> absoluteBefore = withUsage.usages().get(0).allocations().stream()
                .map(allocation -> allocation.startInstant() + "/" + allocation.endInstant())
                .toList();
        assertEquals(180, withUsage.usages().get(0).allocations().stream()
                .mapToInt(allocation -> allocation.minutes()).sum());

        user.setWorkTimezone("Europe/Samara");
        users.save(user);
        OvertimeAccountDto plusOne = overtime.account(user);
        assertEquals(1.0, plusOne.credits().stream()
                .filter(row -> "2026-07-03".equals(row.workedDate()))
                .mapToDouble(OvertimeCreditRowDto::hours).sum(), 0.001);
        assertEquals(3.0, plusOne.credits().stream()
                .filter(row -> "2026-07-04".equals(row.workedDate()))
                .mapToDouble(OvertimeCreditRowDto::hours).sum(), 0.001);
        assertEquals(1.0, plusOne.credits().stream()
                .filter(row -> "2026-07-03".equals(row.workedDate()))
                .mapToDouble(OvertimeCreditRowDto::usedHours).sum(), 0.001);
        assertEquals(2.0, plusOne.credits().stream()
                .filter(row -> "2026-07-04".equals(row.workedDate()))
                .mapToDouble(OvertimeCreditRowDto::usedHours).sum(), 0.001);
        assertTrue(plusOne.credits().stream()
                .filter(row -> "2026-07-04".equals(row.workedDate()))
                .allMatch(row -> Math.abs(row.projection().dayEarnedHours() - 3.0) < 0.001));
        assertEquals(4.0, plusOne.totalEarnedHours(), 0.001);
        assertEquals(3.0, plusOne.totalUsedHours(), 0.001);
        assertEquals(1.0, plusOne.balanceHours(), 0.001);

        user.setWorkTimezone("Asia/Yekaterinburg");
        users.save(user);
        OvertimeAccountDto plusTwo = overtime.account(user);
        assertTrue(plusTwo.credits().stream().noneMatch(row -> "2026-07-03".equals(row.workedDate())));
        assertEquals(4.0, plusTwo.credits().stream()
                .filter(row -> "2026-07-04".equals(row.workedDate()))
                .mapToDouble(OvertimeCreditRowDto::hours).sum(), 0.001);
        assertEquals(3.0, plusTwo.credits().stream()
                .filter(row -> "2026-07-04".equals(row.workedDate()))
                .mapToDouble(OvertimeCreditRowDto::usedHours).sum(), 0.001);
        assertEquals(180, plusTwo.usages().get(0).allocations().stream()
                .mapToInt(allocation -> allocation.minutes()).sum());
        assertEquals(absoluteBefore.get(0).split("/")[0],
                plusTwo.usages().get(0).allocations().get(0).startInstant(),
                "timezone projection must not move the first FIFO minute");
        assertEquals(1.0, plusTwo.balanceHours(), 0.001);

        user.setWorkTimezone("Europe/Moscow");
        users.save(user);
        OvertimeAccountDto projectedBack = overtime.account(user);
        assertEquals(2.0, projectedBack.credits().stream()
                .filter(row -> "2026-07-03".equals(row.workedDate()))
                .mapToDouble(OvertimeCreditRowDto::hours).sum(), 0.001);
        assertEquals(2.0, projectedBack.credits().stream()
                .filter(row -> "2026-07-04".equals(row.workedDate()))
                .mapToDouble(OvertimeCreditRowDto::hours).sum(), 0.001);
        assertEquals(180, projectedBack.usages().get(0).allocations().stream()
                .mapToInt(allocation -> allocation.minutes()).sum());
        assertEquals(1.0, projectedBack.balanceHours(), 0.001);
    }

    @Test
    void accountPageFiltersByProjectedCalendarDate() {
        user.setWorkTimezone("Europe/Moscow");
        users.save(user);
        overtime.createCredit(user,
                interval("2026-07-03", "2026-07-03T22:00", "2026-07-04T02:00", 0, 0.0));

        user.setWorkTimezone("Asia/Yekaterinburg");
        users.save(user);

        var julyThird = overtime.accountPage(user, "2026-07-03", "2026-07-03", "all", "", 0, 50);
        assertEquals(0, julyThird.credits().total());

        var julyFourth = overtime.accountPage(user, "2026-07-04", "2026-07-04", "all", "", 0, 50);
        assertTrue(julyFourth.credits().total() >= 1);
        assertEquals(4.0, julyFourth.credits().items().stream()
                .mapToDouble(OvertimeCreditRowDto::hours).sum(), 0.001);
    }

    @Test
    void calculatedCreditPersistsAbsoluteIdentityAndDisplayProjection() {
        user.setWorkTimezone("Asia/Yekaterinburg");
        users.save(user);

        OvertimeAccountDto acc = overtime.createCredit(user,
                interval("2026-07-25", "2026-07-25T17:00", "2026-07-25T20:00", 0, 0.0));

        OvertimeCreditRowDto row = acc.credits().get(0);
        assertEquals("2026-07-25T12:00:00Z", row.startInstant());
        assertEquals("2026-07-25T17:00", row.displayStart());
        assertEquals("2026-07-25T20:00", row.displayEnd());
        assertEquals("Asia/Yekaterinburg", row.sourceTimezone());
        assertEquals("Asia/Yekaterinburg", row.displayTimezone());
    }

    @Test
    void savingUnchangedCalculatedCreditDoesNotMoveItsInstantAfterWorkTimezoneChange() {
        user.setWorkTimezone("Asia/Yekaterinburg");
        users.save(user);

        OvertimeAccountDto created = overtime.createCredit(user,
                interval("2026-07-25", "2026-07-25T17:00", "2026-07-25T20:00", 0, 0.0));
        OvertimeCreditRowDto original = created.credits().get(0);

        user.setWorkTimezone("Europe/Moscow");
        users.save(user);

        OvertimeAccountDto updated = overtime.updateCredit(user, original.id(),
                new OvertimeCreditUpdateRequest(
                        "2026-07-25", "17:00–20:00",
                        "2026-07-25T17:00", "2026-07-25T20:00",
                        0, 0.0, 3.0, "updated reason"));

        OvertimeCreditRowDto row = updated.credits().get(0);
        assertEquals(original.startInstant(), row.startInstant());
        assertEquals(original.endInstant(), row.endInstant());
        assertEquals("Asia/Yekaterinburg", row.sourceTimezone());
        assertEquals("updated reason", row.reason());
    }

    @Test
    void editingCalculatedIntervalKeepsItsOriginalSourceTimezone() {
        user.setWorkTimezone("Asia/Yekaterinburg");
        users.save(user);

        OvertimeAccountDto created = overtime.createCredit(user,
                interval("2026-07-25", "2026-07-25T17:00", "2026-07-25T20:00", 0, 0.0));
        long id = created.credits().get(0).id();

        user.setWorkTimezone("Europe/Moscow");
        users.save(user);

        OvertimeAccountDto updated = overtime.updateCredit(user, id,
                new OvertimeCreditUpdateRequest(
                        "2026-07-25", "17:00–21:00",
                        "2026-07-25T17:00", "2026-07-25T21:00",
                        0, 0.0, 4.0, "extended"));

        OvertimeCreditRowDto row = updated.credits().get(0);
        assertEquals("2026-07-25T12:00:00Z", row.startInstant());
        assertEquals("2026-07-25T16:00:00Z", row.endInstant());
        assertEquals("Asia/Yekaterinburg", row.sourceTimezone());
        assertEquals("2026-07-25T15:00", row.displayStart());
        assertEquals("2026-07-25T19:00", row.displayEnd());
    }

    @Test
    void calculatedCreditUsesActualDstElapsedMinutes() {
        user.setWorkTimezone("Europe/Berlin");
        users.save(user);

        OvertimeAccountDto acc = overtime.createCredit(user,
                interval("2026-03-29", "2026-03-29T00:00", "2026-03-29T08:00", 0, 0.0));

        assertEquals(7.0, acc.balanceHours(), 0.001,
                "spring-forward wall clock 00:00–08:00 contains seven actual hours");
    }

    /* ── Защита от дублей ── */

    @Test
    void пересекающийсяПериодОтклоняется() {
        overtime.createCredit(user,
                interval("2026-07-03", "2026-07-03T20:00", "2026-07-04T08:00", 0, 0.0));

        ApiException ex = assertThrows(ApiException.class, () -> overtime.createCredit(user,
                interval("2026-07-03", "2026-07-03T22:00", "2026-07-04T02:00", 0, 0.0)));
        assertTrue(ex.getMessage().contains("пересекается"), "ожидали отказ по пересечению, а не: " + ex.getMessage());
    }

    /* ── FIFO ── */

    @Test
    void списаниеБерётЧасыИзСамыхСтарыхНачислений() {
        // Сценарий из README: 20-е +2 ч, 21-е +3 ч, 23-го списываем 4 ч.
        overtime.createCredit(user, manual("2026-07-20", 2.0));
        overtime.createCredit(user, manual("2026-07-21", 3.0));

        OvertimeAccountDto acc = overtime.createUsage(user,
                new OvertimeUsageCreateRequest("2026-07-23", 4.0, "отгул"));

        OvertimeCreditRowDto old = rowByDate(acc, "2026-07-20");
        OvertimeCreditRowDto recent = rowByDate(acc, "2026-07-21");
        assertEquals(2.0, old.usedHours(), 0.001, "старое начисление должно быть исчерпано первым");
        assertEquals(0.0, old.remainingHours(), 0.001);
        assertEquals(2.0, recent.usedHours(), 0.001);
        assertEquals(1.0, recent.remainingHours(), 0.001);
        assertEquals(1.0, acc.balanceHours(), 0.001);

        // и само списание знает, из каких двух начислений взяло часы
        assertEquals(2, acc.usages().get(0).allocations().size());
    }

    @Test
    void нельзяСписатьБольшеДоступногоОстатка() {
        overtime.createCredit(user, manual("2026-07-20", 2.0));
        overtime.createCredit(user, manual("2026-07-21", 3.0));
        OvertimeAccountDto before = overtime.createUsage(user,
                new OvertimeUsageCreateRequest("2026-07-22", 1.0, "существующий отгул"));

        ApiException ex = assertThrows(ApiException.class, () -> overtime.createUsage(user,
                new OvertimeUsageCreateRequest("2026-07-23", 6.0, "жадный отгул")));
        assertTrue(ex.getMessage().contains("Недостаточно"), ex.getMessage());

        OvertimeAccountDto after = overtime.account(user);
        assertEquals(before.credits().stream().map(OvertimeCreditRowDto::id).toList(),
                after.credits().stream().map(OvertimeCreditRowDto::id).toList(),
                "неуспешная пересборка не должна удалять начисления");
        assertEquals(1, after.usages().size(), "старый отгул должен остаться");
        assertEquals(1, after.usages().get(0).allocations().size(), "старый FIFO provenance должен остаться");
    }

    @Test
    void неуспешноеРедактированиеОтгулаНеМеняетСтаруюЗапись() {
        overtime.createCredit(user, manual("2026-07-20", 5.0));
        OvertimeAccountDto before = overtime.createUsage(user,
                new OvertimeUsageCreateRequest("2026-07-22", 1.0, "старый отгул"));
        long usageId = before.usages().get(0).id();

        ApiException ex = assertThrows(ApiException.class, () -> overtime.updateUsage(user, usageId,
                new OvertimeUsageUpdateRequest(null, 6.0, "недопустимое изменение")));
        assertTrue(ex.getMessage().contains("Недостаточно"), ex.getMessage());

        OvertimeAccountDto after = overtime.account(user);
        assertEquals(1, after.usages().size());
        assertEquals(usageId, after.usages().get(0).id());
        assertEquals(1.0, after.usages().get(0).hours(), 0.001);
        assertEquals("старый отгул", after.usages().get(0).reason());
        assertEquals(1, after.usages().get(0).allocations().size());
    }

    /* ── Редактирование под защитой ── */

    @Test
    void нельзяУжатьНачислениеНижеУжеСписанного() {
        OvertimeAccountDto acc = overtime.createCredit(user, manual("2026-07-20", 5.0));
        long creditId = acc.credits().get(0).id();
        overtime.createUsage(user, new OvertimeUsageCreateRequest("2026-07-22", 4.0, "отгул"));

        // из 5 часов списано 4 — ужать начисление до 3 нельзя
        OvertimeCreditUpdateRequest shrink = new OvertimeCreditUpdateRequest(
                null, null, null, null, null, null, 3.0, null);
        ApiException ex = assertThrows(ApiException.class,
                () -> overtime.updateCredit(user, creditId, shrink));
        assertTrue(ex.getMessage().contains("уже списано"), ex.getMessage());
    }

    @Test
    void удалениеСписанияВозвращаетЧасыВОстаток() {
        overtime.createCredit(user, manual("2026-07-20", 5.0));
        OvertimeAccountDto afterUsage = overtime.createUsage(user,
                new OvertimeUsageCreateRequest("2026-07-22", 4.0, "отгул"));
        assertEquals(1.0, afterUsage.balanceHours(), 0.001);

        long usageId = afterUsage.usages().get(0).id();
        OvertimeAccountDto afterDelete = overtime.deleteUsage(user, usageId);

        assertEquals(5.0, afterDelete.balanceHours(), 0.001, "часы должны вернуться после удаления списания");
        assertEquals(0.0, rowByDate(afterDelete, "2026-07-20").usedHours(), 0.001);
    }


    @Test
    void deletingOneSplitUsageKeepsBothCreditsAndTheOtherUsage() {
        user.setWorkTimezone("UTC");
        users.save(user);

        OvertimeAccountDto firstCredit = overtime.createCredit(user,
                interval("2026-07-20", "2026-07-20T17:00", "2026-07-20T20:00", 0, 0.0));
        long firstCreditId = firstCredit.credits().get(0).id();
        OvertimeAccountDto secondCredit = overtime.createCredit(user,
                interval("2026-07-21", "2026-07-21T17:00", "2026-07-21T22:00", 0, 0.0));
        long secondCreditId = secondCredit.credits().stream()
                .filter(row -> !row.id().equals(firstCreditId))
                .findFirst().orElseThrow().id();

        OvertimeAccountDto firstUsage = overtime.createUsage(user,
                new OvertimeUsageCreateRequest("2026-07-22", 4.0, "первый отгул"));
        long firstUsageId = firstUsage.usages().get(0).id();
        OvertimeAccountDto secondUsage = overtime.createUsage(user,
                new OvertimeUsageCreateRequest("2026-07-23", 3.0, "второй отгул"));
        long secondUsageId = secondUsage.usages().stream()
                .filter(row -> !row.id().equals(firstUsageId))
                .findFirst().orElseThrow().id();

        assertEquals(2, secondUsage.usages().stream()
                .filter(row -> row.id().equals(firstUsageId))
                .findFirst().orElseThrow().allocations().size(),
                "первый отгул должен состоять из двух FIFO-частей");
        var firstPartRef = secondUsage.credits().stream()
                .filter(row -> row.id().equals(firstCreditId))
                .flatMap(row -> row.usages().stream())
                .filter(row -> row.usageId().equals(firstUsageId))
                .findFirst().orElseThrow();
        var secondPartRef = secondUsage.credits().stream()
                .filter(row -> row.id().equals(secondCreditId))
                .flatMap(row -> row.usages().stream())
                .filter(row -> row.usageId().equals(firstUsageId))
                .findFirst().orElseThrow();
        assertEquals(1, firstPartRef.allocationPartIndex());
        assertEquals(2, firstPartRef.allocationPartCount());
        assertEquals(2, secondPartRef.allocationPartIndex());
        assertEquals(2, secondPartRef.allocationPartCount());

        OvertimeAccountDto rebuilt = overtime.deleteUsage(user, firstUsageId);

        assertEquals(List.of(firstCreditId, secondCreditId),
                rebuilt.credits().stream().map(OvertimeCreditRowDto::id).toList(),
                "удаление отгула не должно удалять начисления");
        assertEquals(1, rebuilt.usages().size());
        assertEquals(secondUsageId, rebuilt.usages().get(0).id());
        assertEquals(1, rebuilt.usages().get(0).allocations().size());
        assertEquals(firstCreditId, rebuilt.usages().get(0).allocations().get(0).creditId(),
                "оставшийся отгул должен заново занять самые старые минуты");
        assertEquals(3.0, rebuilt.credits().get(0).usedHours(), 0.001);
        assertEquals(0.0, rebuilt.credits().get(1).usedHours(), 0.001);
        assertEquals(5.0, rebuilt.balanceHours(), 0.001);
    }

    @Test
    void exactFifoShowsWhichSourceMinutesWereUsedAndReprojectsAfterTimezoneMove() {
        user.setWorkTimezone("Asia/Yekaterinburg");
        users.save(user);

        overtime.createCredit(user,
                interval("2026-07-25", "2026-07-25T17:00", "2026-07-25T20:00", 0, 0.0));
        OvertimeAccountDto afterUsage = overtime.createUsage(user,
                new OvertimeUsageCreateRequest("2026-07-26", 2.0, "отгул"));

        var allocation = afterUsage.usages().get(0).allocations().get(0);
        assertTrue(allocation.exact());
        assertEquals(120, allocation.minutes());
        assertEquals("2026-07-25T12:00:00Z", allocation.startInstant());
        assertEquals("2026-07-25T14:00:00Z", allocation.endInstant());
        assertEquals("2026-07-25T17:00", allocation.displayStart());
        assertEquals("2026-07-25T19:00", allocation.displayEnd());

        user.setWorkTimezone("Europe/Moscow");
        users.save(user);
        OvertimeAccountDto reprojected = overtime.account(user);
        var moved = reprojected.usages().get(0).allocations().get(0);
        assertEquals(allocation.startInstant(), moved.startInstant(), "absolute provenance must not move");
        assertEquals("2026-07-25T15:00", moved.displayStart());
        assertEquals("2026-07-25T17:00", moved.displayEnd());
    }

    @Test
    void deletingEarlierUsageRestoresTheSameMinutesAndRebuildsLaterFifo() {
        user.setWorkTimezone("UTC");
        users.save(user);
        overtime.createCredit(user,
                interval("2026-07-20", "2026-07-20T17:00", "2026-07-20T22:00", 0, 0.0));

        OvertimeAccountDto first = overtime.createUsage(user,
                new OvertimeUsageCreateRequest("2026-07-21", 2.0, "первый"));
        long firstId = first.usages().get(0).id();
        OvertimeAccountDto second = overtime.createUsage(user,
                new OvertimeUsageCreateRequest("2026-07-22", 2.0, "второй"));
        assertEquals("2026-07-20T19:00", second.usages().get(1).allocations().get(0).displayStart());

        OvertimeAccountDto rebuilt = overtime.deleteUsage(user, firstId);
        assertEquals(1, rebuilt.usages().size());
        var allocation = rebuilt.usages().get(0).allocations().get(0);
        assertEquals("2026-07-20T17:00", allocation.displayStart());
        assertEquals("2026-07-20T19:00", allocation.displayEnd());
    }

    @Test
    void legacyMigrationRequiresAnExplicitSelectionEvenThoughPreviewMayListAllRows() {
        overtime.createCredit(user,
                interval("2026-07-19", "2026-07-19T17:00", "2026-07-19T20:00", 0, 0.0));
        OvertimeCredit legacy = credits.findByOwnerOrderByWorkDateAscIdAsc(user).get(0);
        legacy.setStartAtInstant(null);
        legacy.setEndAtInstant(null);
        legacy.setCreditedStartAtInstant(null);
        legacy.setCreditedEndAtInstant(null);
        legacy.setSourceTimezone(null);
        credits.saveAndFlush(legacy);

        var preview = overtime.previewLegacyCredits(user,
                new LegacyOvertimeMigrationRequest(List.of(), "Europe/Moscow"));
        assertEquals(1, preview.requestedCount(), "empty preview selection intentionally lists all legacy rows");

        ApiException error = assertThrows(ApiException.class, () -> overtime.migrateLegacyCredits(user,
                new LegacyOvertimeMigrationRequest(List.of(), "Europe/Moscow")));
        assertTrue(error.getMessage().contains("Выбери хотя бы одну"));
        assertNull(credits.findByOwnerAndId(user, legacy.getId()).orElseThrow().getCreditedStartAtInstant());
    }

    @Test
    void legacyMigrationAttachesChosenZoneAndReconstructsExistingAllocation() {
        user.setWorkTimezone("Europe/Moscow");
        users.save(user);
        OvertimeAccountDto created = overtime.createCredit(user,
                interval("2026-07-20", "2026-07-20T17:00", "2026-07-20T20:00", 0, 0.0));
        long creditId = created.credits().get(0).id();

        OvertimeCredit legacy = credits.findByOwnerAndId(user, creditId).orElseThrow();
        legacy.setStartAtInstant(null);
        legacy.setEndAtInstant(null);
        legacy.setCreditedStartAtInstant(null);
        legacy.setCreditedEndAtInstant(null);
        legacy.setSourceTimezone(null);
        legacy.setMigratedFromLegacy(false);
        credits.saveAndFlush(legacy);

        overtime.createUsage(user, new OvertimeUsageCreateRequest("2026-07-21", 1.0, "отгул"));
        var preview = overtime.previewLegacyCredits(user,
                new LegacyOvertimeMigrationRequest(List.of(creditId), "Asia/Yekaterinburg"));
        assertEquals(1, preview.migratableCount());
        assertEquals("2026-07-20T15:00", preview.credits().get(0).projectedStart());

        var migrated = overtime.migrateLegacyCredits(user,
                new LegacyOvertimeMigrationRequest(List.of(creditId), "Asia/Yekaterinburg"));
        assertEquals(1, migrated.migratedCount());
        var row = migrated.account().credits().get(0);
        assertTrue(row.migratedFromLegacy());
        assertFalse(row.legacyTimezoneRequired());
        var allocation = migrated.account().usages().get(0).allocations().get(0);
        assertTrue(allocation.exact());
        assertTrue(allocation.reconstructed());
        assertEquals("Asia/Yekaterinburg", allocation.sourceTimezone());
    }

}
