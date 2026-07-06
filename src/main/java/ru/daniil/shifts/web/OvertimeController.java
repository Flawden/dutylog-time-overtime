package ru.daniil.shifts.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.daniil.shifts.dto.Dtos.OvertimeLedgerItemDto;
import ru.daniil.shifts.dto.Dtos.OvertimeSummaryDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.DayEntryService;
import ru.daniil.shifts.service.OvertimeService;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

/** Отдельные endpoint'ы под экран баланса переработок и отгулов. */
@RestController
@RequestMapping("/api/overtime")
public class OvertimeController {
    private final CurrentUserService currentUserService;
    private final DayEntryService dayEntryService;
    private final OvertimeService overtimeService;

    public OvertimeController(CurrentUserService currentUserService,
                              DayEntryService dayEntryService,
                              OvertimeService overtimeService) {
        this.currentUserService = currentUserService;
        this.dayEntryService = dayEntryService;
        this.overtimeService = overtimeService;
    }

    /** GET /api/overtime/balance?from=2026-06-01&to=2026-06-30 */
    @GetMapping("/balance")
    public OvertimeSummaryDto balance(@RequestParam String from,
                                      @RequestParam String to,
                                      Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        LocalDate fromDate = dayEntryService.parseDate(from, "Дата from должна быть в формате yyyy-MM-dd");
        LocalDate toDate = dayEntryService.parseDate(to, "Дата to должна быть в формате yyyy-MM-dd");
        return overtimeService.summary(current, fromDate, toDate);
    }

    /** GET /api/overtime/ledger?from=2026-06-01&to=2026-06-30 */
    @GetMapping("/ledger")
    public List<OvertimeLedgerItemDto> ledger(@RequestParam String from,
                                              @RequestParam String to,
                                              Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        LocalDate fromDate = dayEntryService.parseDate(from, "Дата from должна быть в формате yyyy-MM-dd");
        LocalDate toDate = dayEntryService.parseDate(to, "Дата to должна быть в формате yyyy-MM-dd");
        return overtimeService.ledger(current, fromDate, toDate);
    }
}
