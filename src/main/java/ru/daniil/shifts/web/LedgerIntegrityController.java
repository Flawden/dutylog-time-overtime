package ru.daniil.shifts.web;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.daniil.shifts.dto.Dtos.AccountingPeriodDto;
import ru.daniil.shifts.dto.Dtos.LedgerAdjustmentRequest;
import ru.daniil.shifts.dto.Dtos.LedgerIntegrityDto;
import ru.daniil.shifts.dto.Dtos.TimeLedgerEntryDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.*;

import java.security.Principal;
import java.time.LocalDate;

@RestController
@RequestMapping({"/api/ledger-integrity", "/api/v1/ledger-integrity"})
public class LedgerIntegrityController {
    private final CurrentUserService users;
    private final ModuleService modules;
    private final DayEntryService dates;
    private final LedgerIntegrityService ledger;

    public LedgerIntegrityController(CurrentUserService users, ModuleService modules,
                                     DayEntryService dates, LedgerIntegrityService ledger) {
        this.users = users;
        this.modules = modules;
        this.dates = dates;
        this.ledger = ledger;
    }

    @GetMapping
    public ResponseEntity<LedgerIntegrityDto> inspect(@RequestParam("from") String from,
                                                       @RequestParam("to") String to,
                                                       Principal principal) {
        AppUser user = user(principal);
        LocalDate rangeFrom = dates.parseDate(from, "Дата from должна быть в формате yyyy-MM-dd");
        LocalDate rangeTo = dates.parseDate(to, "Дата to должна быть в формате yyyy-MM-dd");
        dates.validateRange(rangeFrom, rangeTo);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ledger.inspect(user, rangeFrom, rangeTo));
    }

    @PostMapping("/periods/{month}/close")
    public AccountingPeriodDto close(@PathVariable("month") String month, Principal principal) {
        return ledger.closePeriod(user(principal), month);
    }

    @PostMapping("/periods/{month}/reopen")
    public AccountingPeriodDto reopen(@PathVariable("month") String month, Principal principal) {
        return ledger.reopenPeriod(user(principal), month);
    }

    @PostMapping("/adjustments")
    public ResponseEntity<TimeLedgerEntryDto> adjustment(
            @Valid @RequestBody(required = false) LedgerAdjustmentRequest request,
            Principal principal) {
        return ResponseEntity.status(201).body(ledger.addClosedPeriodAdjustment(user(principal), request));
    }

    private AppUser user(Principal principal) {
        AppUser user = users.requireUser(principal);
        modules.requireEnabled(user, ModuleService.OVERTIME);
        return user;
    }
}
