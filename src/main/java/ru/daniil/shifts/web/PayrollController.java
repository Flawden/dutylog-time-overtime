package ru.daniil.shifts.web;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.daniil.shifts.dto.Dtos.*;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.ModuleService;
import ru.daniil.shifts.service.PayrollService;

import java.security.Principal;

/** Native Payroll API. Reads are private/no-store; closed calculations remain immutable revisions. */
@RestController
@RequestMapping({"/api/payroll", "/api/v1/payroll"})
public class PayrollController {
    private final CurrentUserService users; private final ModuleService modules; private final PayrollService payroll;
    public PayrollController(CurrentUserService users, ModuleService modules, PayrollService payroll) {
        this.users = users; this.modules = modules; this.payroll = payroll;
    }
    @GetMapping("/periods/{month}")
    public ResponseEntity<PayrollPeriodDto> period(@PathVariable("month") String month, Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(payroll.period(user(principal), month));
    }
    /** Deprecated compatibility adapter; the native UI uses compensation-terms. */
    @PatchMapping("/settings")
    public PayrollSettingsDto updateSettings(@Valid @RequestBody(required = false) PayrollSettingsUpdateRequest request, Principal principal) {
        return payroll.updateSettings(user(principal), request);
    }
    @PutMapping("/compensation-terms/{month}")
    public PayrollCompensationTermDto upsertCompensationTerm(@PathVariable("month") String month,
            @Valid @RequestBody(required = false) PayrollCompensationTermRequest request, Principal principal) {
        return payroll.upsertCompensationTerm(user(principal), month, request);
    }
    @DeleteMapping("/compensation-terms/{month}")
    public ResponseEntity<Void> deleteCompensationTerm(@PathVariable("month") String month, Principal principal) {
        payroll.deleteCompensationTerm(user(principal), month); return ResponseEntity.noContent().build();
    }
    @PostMapping("/adjustments")
    public ResponseEntity<PayrollAdjustmentDto> addAdjustment(@Valid @RequestBody(required = false) PayrollAdjustmentRequest request, Principal principal) {
        return ResponseEntity.status(201).body(payroll.addAdjustment(user(principal), request));
    }
    @PostMapping("/periods/{month}/calculate")
    public ResponseEntity<PayrollSnapshotDto> calculate(@PathVariable("month") String month, Principal principal) {
        return ResponseEntity.status(201).body(payroll.calculate(user(principal), month));
    }
    private AppUser user(Principal principal) {
        AppUser user = users.requireUser(principal); modules.requireEnabled(user, ModuleService.PAYROLL); return user;
    }
}
