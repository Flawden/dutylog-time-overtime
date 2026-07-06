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
    void ровныеСуткиДелятсяПополамМеждуДвумяДатами() {
        // 03.07 08:00 → 04.07 08:00 — особый случай: режем пополам,
        // чтобы в календаре были две понятные половины по 12 ч.
        OvertimeAccountDto acc = overtime.createCredit(user,
                interval("2026-07-03", "2026-07-03T08:00", "2026-07-04T08:00", 0, 0.0));

        assertEquals(2, acc.credits().size());
        assertEquals(12.0, rowByDate(acc, "2026-07-03").hours(), 0.001);
        assertEquals(12.0, rowByDate(acc, "2026-07-04").hours(), 0.001);
        assertEquals(24.0, acc.balanceHours(), 0.001);
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

        ApiException ex = assertThrows(ApiException.class, () -> overtime.createUsage(user,
                new OvertimeUsageCreateRequest("2026-07-23", 6.0, "жадный отгул")));
        assertTrue(ex.getMessage().contains("Недостаточно"), ex.getMessage());
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
}
