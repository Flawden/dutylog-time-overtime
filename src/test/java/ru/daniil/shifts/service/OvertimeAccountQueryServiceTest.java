package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.OvertimeAccountDto;
import ru.daniil.shifts.dto.Dtos.OvertimeAccountPageDto;
import ru.daniil.shifts.dto.Dtos.OvertimeCreditCreateRequest;
import ru.daniil.shifts.dto.Dtos.OvertimeUsageCreateRequest;
import ru.daniil.shifts.dto.Dtos.OvertimeUsageUpdateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Query, export and mutation coverage around the FIFO overtime account. */
@SpringBootTest
@Transactional
class OvertimeAccountQueryServiceTest {

    @Autowired OvertimeService overtimeService;
    @Autowired UserRepository users;

    AppUser owner;
    AppUser other;

    @BeforeEach
    void setUp() {
        owner = users.save(new AppUser("overtime-query-owner", "{noop}unused"));
        other = users.save(new AppUser("overtime-query-other", "{noop}unused"));
    }

    @Test
    void accountPageFiltersOpenPartialClosedDateAndSearch() {
        createThreeStatusRows();

        OvertimeAccountPageDto all = overtimeService.accountPage(owner, null, null, "all", "", 0, 50);
        assertEquals(3, all.credits().total());
        assertEquals(9.0, all.totalEarnedHours());
        assertEquals(4.0, all.totalUsedHours());
        assertEquals(5.0, all.balanceHours());

        OvertimeAccountPageDto closed = overtimeService.accountPage(owner, null, null, "closed", "", 0, 50);
        assertEquals(1, closed.credits().total());
        assertEquals("2026-07-01", closed.credits().items().get(0).workedDate());

        OvertimeAccountPageDto partial = overtimeService.accountPage(owner, null, null, "partial", "", 0, 50);
        assertEquals(1, partial.credits().total());
        assertEquals("2026-07-02", partial.credits().items().get(0).workedDate());

        OvertimeAccountPageDto open = overtimeService.accountPage(owner, "2026-07-03", "2026-07-31", "open", "ппр", 0, 50);
        assertEquals(1, open.credits().total());
        assertEquals("2026-07-03", open.credits().items().get(0).workedDate());
        assertEquals("ППР; резерв", open.credits().items().get(0).reason());
    }

    @Test
    void accountPageUsesSafePageAndSizeBounds() {
        for (int i = 1; i <= 12; i++) {
            overtimeService.createCredit(owner, manual("2026-08-%02d".formatted(i), 1.0, "строка " + i));
        }

        OvertimeAccountPageDto first = overtimeService.accountPage(owner, null, null, "all", "", -5, 1);
        assertEquals(0, first.credits().page());
        assertEquals(10, first.credits().size());
        assertEquals(10, first.credits().items().size());
        assertEquals(12, first.credits().total());
        assertEquals(2, first.credits().totalPages());
        assertFalse(first.credits().hasPrevious());
        assertTrue(first.credits().hasNext());

        OvertimeAccountPageDto second = overtimeService.accountPage(owner, null, null, "all", "", 1, 500);
        assertEquals(100, second.credits().size());
        assertEquals(0, second.credits().items().size());
        assertTrue(second.credits().hasPrevious());
        assertFalse(second.credits().hasNext());
    }

    @Test
    void csvExportKeepsBomFiltersRowsAndEscapesSpreadsheetCells() {
        overtimeService.createCredit(owner, manual("2026-07-01", 2.0, "обычная"));
        overtimeService.createCredit(owner, manual("2026-07-02", 3.0, "ППР; \"ночь\"\nвторая строка"));

        byte[] bytes = overtimeService.exportAccountCsv(owner, "2026-07-02", "2026-07-02", "open", "ппр");
        String csv = new String(bytes, StandardCharsets.UTF_8);

        assertTrue(csv.startsWith("\ufeff"));
        assertTrue(csv.contains("День переработки"));
        assertTrue(csv.contains("2026-07-02"));
        assertFalse(csv.contains("2026-07-01"));
        assertTrue(csv.contains("\"ППР; \"\"ночь\"\"\nвторая строка\""));
    }

    @Test
    void xlsExportEscapesHtmlAndKeepsFilteredSummary() {
        overtimeService.createCredit(owner, manual("2026-07-01", 2.0, "<script>alert(1)</script>"));

        String html = new String(overtimeService.exportAccountXls(owner, null, null, "all", "script"), StandardCharsets.UTF_8);
        assertTrue(html.startsWith("\ufeff<!doctype html>"));
        assertTrue(html.contains("Журнал переработок"));
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertFalse(html.contains("<script>alert(1)</script>"));
        assertTrue(html.contains("записей 1"));
    }

    @Test
    void updatingUsageRebuildsFifoAllocationsFromTheOldestCredits() {
        overtimeService.createCredit(owner, manual("2026-07-01", 2.0, "старое"));
        overtimeService.createCredit(owner, manual("2026-07-02", 3.0, "новое"));
        OvertimeAccountDto afterCreate = overtimeService.createUsage(owner,
                new OvertimeUsageCreateRequest("2026-07-05", 1.0, "первый отгул"));
        long usageId = afterCreate.usages().get(0).id();

        OvertimeAccountDto updated = overtimeService.updateUsage(owner, usageId,
                new OvertimeUsageUpdateRequest("2026-07-06", 4.0, "увеличенный отгул"));

        assertEquals(1.0, updated.balanceHours());
        assertEquals("2026-07-06", updated.usages().get(0).usageDate());
        assertEquals("увеличенный отгул", updated.usages().get(0).reason());
        assertEquals(2, updated.usages().get(0).allocations().size());
        assertEquals(2.0, updated.usages().get(0).allocations().get(0).hours());
        assertEquals(2.0, updated.usages().get(0).allocations().get(1).hours());
    }

    @Test
    void usedCreditCannotBeDeletedButUnusedCreditCan() {
        OvertimeAccountDto first = overtimeService.createCredit(owner, manual("2026-07-01", 2.0, "используемое"));
        long usedCreditId = first.credits().get(0).id();
        OvertimeAccountDto second = overtimeService.createCredit(owner, manual("2026-07-02", 3.0, "свободное"));
        long unusedCreditId = second.credits().stream()
                .filter(c -> "2026-07-02".equals(c.workedDate())).findFirst().orElseThrow().id();
        overtimeService.createUsage(owner, new OvertimeUsageCreateRequest("2026-07-03", 1.0, "отгул"));

        assertBadRequest(() -> overtimeService.deleteCredit(owner, usedCreditId));
        OvertimeAccountDto afterDelete = overtimeService.deleteCredit(owner, unusedCreditId);
        assertEquals(1, afterDelete.credits().size());
        assertEquals(1.0, afterDelete.balanceHours());
    }

    @Test
    void accountsAreOwnerScopedAndMalformedRequestsUseStableErrors() {
        overtimeService.createCredit(owner, manual("2026-07-01", 2.0, "своё"));
        overtimeService.createCredit(other, manual("2026-07-02", 7.0, "чужое"));

        assertEquals(2.0, overtimeService.account(owner).totalEarnedHours());
        assertEquals(1, overtimeService.account(owner).credits().size());

        assertBadRequest(() -> overtimeService.createCredit(owner, null));
        assertBadRequest(() -> overtimeService.createCredit(owner, manual("01.07.2026", 1.0, "ошибка")));
        assertBadRequest(() -> overtimeService.createUsage(owner, null));
        assertBadRequest(() -> overtimeService.createUsage(owner,
                new OvertimeUsageCreateRequest("2026-07-02", 0.0, "ошибка")));
        assertBadRequest(() -> overtimeService.updateUsage(owner, Long.MAX_VALUE,
                new OvertimeUsageUpdateRequest(null, 1.0, null)), HttpStatus.NOT_FOUND);
    }

    private void createThreeStatusRows() {
        overtimeService.createCredit(owner, manual("2026-07-01", 2.0, "старое"));
        overtimeService.createCredit(owner, manual("2026-07-02", 3.0, "частично"));
        overtimeService.createCredit(owner, manual("2026-07-03", 4.0, "ППР; резерв"));
        overtimeService.createUsage(owner, new OvertimeUsageCreateRequest("2026-07-05", 4.0, "отгул"));
    }

    private OvertimeCreditCreateRequest manual(String date, double hours, String reason) {
        return new OvertimeCreditCreateRequest(date, null, null, null, null, null, hours, reason);
    }

    private static void assertBadRequest(org.junit.jupiter.api.function.Executable action) {
        assertBadRequest(action, HttpStatus.BAD_REQUEST);
    }

    private static void assertBadRequest(org.junit.jupiter.api.function.Executable action, HttpStatus expectedStatus) {
        ApiException error = assertThrows(ApiException.class, action);
        assertEquals(expectedStatus, error.getStatus());
        assertEquals(expectedStatus == HttpStatus.NOT_FOUND ? "NOT_FOUND" : "BAD_REQUEST", error.getCode());
    }
}
