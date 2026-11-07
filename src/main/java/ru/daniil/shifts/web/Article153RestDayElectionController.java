package ru.daniil.shifts.web;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.Article153RestDayElectionAuthorityService;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.ModuleService;
import ru.daniil.shifts.service.OrdinaryWorkPremiumSourceService.SourceKind;
import ru.daniil.shifts.service.exception.ApiException;

import java.io.IOException;
import java.security.Principal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/** Employee's event-specific Article 153 rest-day election; NEVER an overtime-bank debit. */
@RestController
@RequestMapping({"/api/payroll/periods/{month}/rest-day-elections",
        "/api/v1/payroll/periods/{month}/rest-day-elections"})
public class Article153RestDayElectionController {
    private static final JsonMapper STRICT_JSON = JsonMapper.builder()
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .build();

    private final CurrentUserService users;
    private final ModuleService modules;
    private final Article153RestDayElectionAuthorityService elections;

    public Article153RestDayElectionController(CurrentUserService users, ModuleService modules,
            Article153RestDayElectionAuthorityService elections) {
        this.users = users;
        this.modules = modules;
        this.elections = elections;
    }

    @GetMapping
    public ResponseEntity<Article153RestDayElectionAuthorityService.MonthlyWorkspace> workspace(
            @PathVariable("month") String month, Principal principal) {
        AppUser owner = owner(principal);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(elections.workspace(owner, parseMonth(month)));
    }

    @PostMapping
    public ResponseEntity<Article153RestDayElectionAuthorityService.ElectionFact> elect(
            @PathVariable("month") String month, @RequestBody(required = false) String json, Principal principal) {
        AppUser owner = owner(principal);
        YearMonth selectedMonth = parseMonth(month);
        ElectionRequest request = decode(json);
        LocalDate date;
        SourceKind kind;
        try {
            date = LocalDate.parse(request.workDate());
            kind = SourceKind.valueOf(request.sourceKind());
        } catch (DateTimeParseException | IllegalArgumentException ex) {
            throw ApiException.badRequest("ARTICLE153_ELECTION_INPUT", "Некорректная дата или источник работы");
        }
        if (!YearMonth.from(date).equals(selectedMonth)) {
            throw ApiException.badRequest("ARTICLE153_ELECTION_PERIOD", "Дата работы не принадлежит расчётному месяцу");
        }
        if (request.sourceEntityId() == null || request.sourceEntityId() <= 0L
                || request.expectedSourceFingerprint() == null
                || !request.expectedSourceFingerprint().matches("[0-9a-f]{64}")) {
            throw ApiException.badRequest("ARTICLE153_ELECTION_INPUT", "Не указан проверенный источник работы");
        }
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(elections.elect(owner, date, kind, request.sourceEntityId(),
                            request.expectedSourceFingerprint()));
        } catch (IllegalStateException ex) {
            // A previously revoked or changed event must not silently become a new election.
            if (ex.getMessage() != null && (ex.getMessage().startsWith("ARTICLE153_REST_DAY_ELECTION_")
                    || ex.getMessage().startsWith("HOLIDAY_PAY_"))) {
                throw ApiException.conflict("ARTICLE153_ELECTION_BLOCKED",
                        "Источник изменился или требует проверки. Обнови данные месяца.");
            }
            throw ex;
        }
    }

    static ElectionRequest decode(String json) {
        if (json == null || json.isBlank()) {
            throw ApiException.badRequest("ARTICLE153_ELECTION_INPUT", "Заполни данные выбора компенсации");
        }
        try {
            return STRICT_JSON.readValue(json, ElectionRequest.class);
        } catch (IOException | IllegalArgumentException ex) {
            throw ApiException.badRequest("ARTICLE153_ELECTION_INPUT", "Некорректные данные выбора компенсации");
        }
    }

    private static YearMonth parseMonth(String month) {
        try {
            if (month == null || !month.matches("[0-9]{4}-(0[1-9]|1[0-2])")) throw new IllegalArgumentException();
            return YearMonth.parse(month);
        } catch (DateTimeParseException | IllegalArgumentException ex) {
            throw ApiException.badRequest("ARTICLE153_ELECTION_PERIOD", "Некорректный расчётный месяц");
        }
    }

    private AppUser owner(Principal principal) {
        AppUser owner = users.requireUser(principal);
        modules.requireEnabled(owner, ModuleService.PAYROLL);
        return owner;
    }

    public record ElectionRequest(String workDate, String sourceKind,
            Long sourceEntityId, String expectedSourceFingerprint) {}
}
