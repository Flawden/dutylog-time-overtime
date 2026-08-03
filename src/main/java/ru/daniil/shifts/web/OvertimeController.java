package ru.daniil.shifts.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.daniil.shifts.dto.Dtos.LegacyOvertimeMigrationPreviewDto;
import ru.daniil.shifts.dto.Dtos.LegacyOvertimeMigrationRequest;
import ru.daniil.shifts.dto.Dtos.LegacyOvertimeMigrationResultDto;
import ru.daniil.shifts.dto.Dtos.OvertimeAccountDto;
import ru.daniil.shifts.dto.Dtos.OvertimeAccountPageDto;
import ru.daniil.shifts.dto.Dtos.OvertimeCreditCreateRequest;
import ru.daniil.shifts.dto.Dtos.OvertimeCreditPreviewDto;
import ru.daniil.shifts.dto.Dtos.OvertimeCreditUpdateRequest;
import ru.daniil.shifts.dto.Dtos.OvertimeLedgerItemDto;
import ru.daniil.shifts.dto.Dtos.OvertimeSummaryDto;
import ru.daniil.shifts.dto.Dtos.OvertimeUsageCreateRequest;
import ru.daniil.shifts.dto.Dtos.OvertimeUsageUpdateRequest;
import ru.daniil.shifts.dto.Dtos.LegacyOvertimeUsageMigrationPreviewDto;
import ru.daniil.shifts.dto.Dtos.LegacyOvertimeUsageMigrationRequest;
import ru.daniil.shifts.dto.Dtos.LegacyOvertimeUsageMigrationResultDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.ModuleService;
import ru.daniil.shifts.service.DayEntryService;
import ru.daniil.shifts.service.OvertimeService;
import ru.daniil.shifts.service.VacationPlannerService;
import ru.daniil.shifts.service.exception.ApiException;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

/** Отдельные endpoint'ы под экран баланса переработок и отгулов. */
@RestController
@RequestMapping({"/api/overtime", "/api/v1/overtime"})
public class OvertimeController {
    private final CurrentUserService currentUserService;
    private final ModuleService moduleService;
    private final DayEntryService dayEntryService;
    private final OvertimeService overtimeService;
    private final VacationPlannerService vacationPlannerService;

    public OvertimeController(CurrentUserService currentUserService,
                              ModuleService moduleService,
                              DayEntryService dayEntryService,
                              OvertimeService overtimeService,
                              VacationPlannerService vacationPlannerService) {
        this.currentUserService = currentUserService;
        this.moduleService = moduleService;
        this.dayEntryService = dayEntryService;
        this.overtimeService = overtimeService;
        this.vacationPlannerService = vacationPlannerService;
    }

    /** POST /api/overtime/preview — canonical preview in the user's IANA timezone. */
    @PostMapping("/preview")
    public OvertimeCreditPreviewDto preview(@Valid @RequestBody(required = false) OvertimeCreditCreateRequest req,
                                            Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        moduleService.requireEnabled(current, ModuleService.OVERTIME);
        return overtimeService.previewCredit(current, req);
    }

    /** GET /api/overtime/account — полная таблица начислений, списаний и остатка. */
    @GetMapping("/account")
    public OvertimeAccountDto account(Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        moduleService.requireEnabled(current, ModuleService.OVERTIME);
        return overtimeService.account(current);
    }

    /** GET /api/overtime/account-page — журнал переработок страницами для UI. */
    @GetMapping("/account-page")
    public OvertimeAccountPageDto accountPage(@RequestParam(name = "from", required = false) String from,
                                              @RequestParam(name = "to", required = false) String to,
                                              @RequestParam(name = "status", required = false, defaultValue = "all") String status,
                                              @RequestParam(name = "q", required = false, defaultValue = "") String q,
                                              @RequestParam(name = "page", required = false, defaultValue = "0") int page,
                                              @RequestParam(name = "size", required = false, defaultValue = "50") int size,
                                              Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        moduleService.requireEnabled(current, ModuleService.OVERTIME);
        return overtimeService.accountPage(current, from, to, status, q, page, size);
    }

    /** GET /api/overtime/export.csv — выгрузить текущий журнал переработок в CSV. */
    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(name = "from", required = false) String from,
                                            @RequestParam(name = "to", required = false) String to,
                                            @RequestParam(name = "status", required = false, defaultValue = "all") String status,
                                            @RequestParam(name = "q", required = false, defaultValue = "") String q,
                                            Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        moduleService.requireEnabled(current, ModuleService.OVERTIME);
        byte[] body = overtimeService.exportAccountCsv(current, from, to, status, q);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"overtime-ledger.csv\"")
                .contentType(new MediaType("text", "csv"))
                .body(body);
    }

    /** GET /api/overtime/export.xls — Excel-совместимый отчёт без дополнительной тяжёлой зависимости. */
    @GetMapping("/export.xls")
    public ResponseEntity<byte[]> exportXls(@RequestParam(name = "from", required = false) String from,
                                            @RequestParam(name = "to", required = false) String to,
                                            @RequestParam(name = "status", required = false, defaultValue = "all") String status,
                                            @RequestParam(name = "q", required = false, defaultValue = "") String q,
                                            Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        moduleService.requireEnabled(current, ModuleService.OVERTIME);
        byte[] body = overtimeService.exportAccountXls(current, from, to, status, q);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"overtime-ledger.xls\"")
                .contentType(MediaType.parseMediaType("application/vnd.ms-excel; charset=UTF-8"))
                .body(body);
    }

    /** POST /api/overtime/credits — начислить переработку. */
    @PostMapping("/credits")
    public OvertimeAccountDto createCredit(@Valid @RequestBody OvertimeCreditCreateRequest req,
                                           Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        moduleService.requireEnabled(current, ModuleService.OVERTIME);
        return overtimeService.createCredit(current, req);
    }

    /** PATCH /api/overtime/credits/{id} — отредактировать начисление и безопасно пересчитать часы. */
    @PatchMapping("/credits/{id}")
    public OvertimeAccountDto updateCredit(@PathVariable("id") long id,
                                           @Valid @RequestBody OvertimeCreditUpdateRequest req,
                                           Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        moduleService.requireEnabled(current, ModuleService.OVERTIME);
        return overtimeService.updateCredit(current, id, req);
    }

    /** DELETE /api/overtime/credits/{id} — удалить начисление, если из него ещё ничего не списано. */
    @DeleteMapping("/credits/{id}")
    public OvertimeAccountDto deleteCredit(@PathVariable("id") long id, Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        moduleService.requireEnabled(current, ModuleService.OVERTIME);
        return overtimeService.deleteCredit(current, id);
    }

    /**
     * Direct manual usage creation retired in v27.31.0. A time-off is now an absence
     * and the FIFO usage is created by Vacation Planner as a linked implementation detail.
     */
    @PostMapping("/usages")
    public OvertimeAccountDto createUsage(@Valid @RequestBody OvertimeUsageCreateRequest req,
                                          Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        moduleService.requireEnabled(current, ModuleService.OVERTIME);
        throw ApiException.conflict("DIRECT_USAGE_RETIRED",
                "Новое списание оформляется как отсутствие через единый конструктор");
    }

    /** Legacy MANUAL usages must be promoted before they can be edited. */
    @PatchMapping("/usages/{id}")
    public OvertimeAccountDto updateUsage(@PathVariable("id") long id,
                                          @Valid @RequestBody OvertimeUsageUpdateRequest req,
                                          Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        moduleService.requireEnabled(current, ModuleService.OVERTIME);
        if (overtimeService.isAbsenceLinkedUsage(current, id)) {
            throw ApiException.conflict("LINKED_USAGE_MANAGED_BY_ABSENCE",
                    "Связанное списание изменяется только через отсутствие");
        }
        throw ApiException.conflict("LEGACY_USAGE_MUST_BE_MIGRATED",
                "Старое списание сначала нужно перенести в список отсутствий");
    }

    /** Preview one-time promotion of old MANUAL usages into canonical absences. */
    @PostMapping("/legacy-usages/preview")
    public LegacyOvertimeUsageMigrationPreviewDto previewLegacyUsages(
            @RequestBody(required = false) LegacyOvertimeUsageMigrationRequest req, Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        moduleService.requireEnabled(current, ModuleService.OVERTIME);
        moduleService.requireEnabled(current, ModuleService.VACATION);
        return vacationPlannerService.previewLegacyUsageMigration(current, req);
    }

    /** Promote selected MANUAL usages in place while preserving FIFO allocation rows. */
    @PostMapping("/legacy-usages/migrate")
    public LegacyOvertimeUsageMigrationResultDto migrateLegacyUsages(
            @RequestBody(required = false) LegacyOvertimeUsageMigrationRequest req, Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        moduleService.requireEnabled(current, ModuleService.OVERTIME);
        moduleService.requireEnabled(current, ModuleService.VACATION);
        return vacationPlannerService.migrateLegacyUsages(current, req);
    }

    /** DELETE /api/overtime/usages/{id} — удалить списание и вернуть часы в остатки начислений. */
    @DeleteMapping("/usages/{id}")
    public OvertimeAccountDto deleteUsage(@PathVariable("id") long id, Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        moduleService.requireEnabled(current, ModuleService.OVERTIME);
        return overtimeService.deleteUsage(current, id);
    }

    /** POST /api/overtime/legacy-credits/preview — безопасный предварительный просмотр миграции старых интервалов. */
    @PostMapping("/legacy-credits/preview")
    public LegacyOvertimeMigrationPreviewDto previewLegacyCredits(@RequestBody LegacyOvertimeMigrationRequest req,
                                                                   Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        moduleService.requireEnabled(current, ModuleService.OVERTIME);
        return overtimeService.previewLegacyCredits(current, req);
    }

    /** POST /api/overtime/legacy-credits/migrate — привязать выбранные legacy-записи к исходной IANA-зоне. */
    @PostMapping("/legacy-credits/migrate")
    public LegacyOvertimeMigrationResultDto migrateLegacyCredits(@RequestBody LegacyOvertimeMigrationRequest req,
                                                                  Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        moduleService.requireEnabled(current, ModuleService.OVERTIME);
        return overtimeService.migrateLegacyCredits(current, req);
    }

    /** GET /api/overtime/balance?from=2026-06-01&to=2026-06-30 — старый периодный отчёт по day_entries. */
    @GetMapping("/balance")
    public OvertimeSummaryDto balance(@RequestParam("from") String from,
                                      @RequestParam("to") String to,
                                      Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        moduleService.requireEnabled(current, ModuleService.OVERTIME);
        LocalDate fromDate = dayEntryService.parseDate(from, "Дата from должна быть в формате yyyy-MM-dd");
        LocalDate toDate = dayEntryService.parseDate(to, "Дата to должна быть в формате yyyy-MM-dd");
        return overtimeService.summary(current, fromDate, toDate);
    }

    /** GET /api/overtime/ledger?from=2026-06-01&to=2026-06-30 — старый журнал по day_entries. */
    @GetMapping("/ledger")
    public List<OvertimeLedgerItemDto> ledger(@RequestParam("from") String from,
                                              @RequestParam("to") String to,
                                              Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        moduleService.requireEnabled(current, ModuleService.OVERTIME);
        LocalDate fromDate = dayEntryService.parseDate(from, "Дата from должна быть в формате yyyy-MM-dd");
        LocalDate toDate = dayEntryService.parseDate(to, "Дата to должна быть в формате yyyy-MM-dd");
        return overtimeService.ledger(current, fromDate, toDate);
    }
}
