package ru.daniil.shifts.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.OvertimeCreditCreateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.OvertimeCredit;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.repo.OvertimeCreditRepository;
import ru.daniil.shifts.service.OvertimeService;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP contract for overtime accounting, FIFO, exports, modules and ownership. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OvertimeControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;
    @Autowired OvertimeService overtimeService;
    @Autowired OvertimeCreditRepository credits;

    AppUser owner;
    AppUser other;

    @BeforeEach
    void setUp() {
        owner = users.save(new AppUser("overtime-controller-owner", "{noop}unused"));
        other = users.save(new AppUser("overtime-controller-other", "{noop}unused"));
    }

    @Test
    void canonicalAbsenceMigrationPreservesLegacyFifoAndRetiresDirectUsageWrites() throws Exception {
        setOvertimeEnabled(owner, true);
        setVacationEnabled(owner, true);

        long oldCreditId = createCreditViaApi("/api/v1/overtime/credits", "2026-07-01", 2.0, "старое");
        long recentCreditId = createCreditViaApi("/api/overtime/credits", "2026-07-02", 3.0, "новое");
        var legacy = overtimeService.createUsage(owner,
                new ru.daniil.shifts.dto.Dtos.OvertimeUsageCreateRequest("2026-07-05", 4.0, "отгул"));
        long usageId = legacy.usages().get(0).id();
        assertEquals(2, legacy.usages().get(0).allocations().size());

        mvc.perform(post("/api/v1/overtime/usages")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"date\":\"2026-07-06\",\"hours\":1,\"reason\":\"новое списание\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DIRECT_USAGE_RETIRED"));

        mvc.perform(patch("/api/overtime/usages/{id}", usageId)
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"hours\":3}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEGACY_USAGE_MUST_BE_MIGRATED"));

        String migrationBody = "{\"usageIds\":[" + usageId + "]}";
        mvc.perform(post("/api/overtime/legacy-usages/preview")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf())
                        .contentType("application/json").content(migrationBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.hoursOnlyCount").value(1))
                .andExpect(jsonPath("$.usages[0].usageId").value(usageId))
                .andExpect(jsonPath("$.usages[0].migratable").value(true));

        mvc.perform(post("/api/v1/overtime/legacy-usages/migrate")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf())
                        .contentType("application/json").content(migrationBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.migratedCount").value(1))
                .andExpect(jsonPath("$.skippedCount").value(0))
                .andExpect(jsonPath("$.absenceIds", hasSize(1)));

        mvc.perform(get("/api/overtime/account")
                        .with(user(owner.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEarnedHours").value(5.0))
                .andExpect(jsonPath("$.totalUsedHours").value(4.0))
                .andExpect(jsonPath("$.balanceHours").value(1.0))
                .andExpect(jsonPath("$.credits[0].id").value(oldCreditId))
                .andExpect(jsonPath("$.credits[0].usedHours").value(2.0))
                .andExpect(jsonPath("$.credits[1].id").value(recentCreditId))
                .andExpect(jsonPath("$.credits[1].usedHours").value(2.0))
                .andExpect(jsonPath("$.usages[0].id").value(usageId))
                .andExpect(jsonPath("$.usages[0].sourceKind").value("ABSENCE"))
                .andExpect(jsonPath("$.usages[0].sourceAbsenceId").isNumber())
                .andExpect(jsonPath("$.usages[0].editable").value(false))
                .andExpect(jsonPath("$.usages[0].allocations", hasSize(2)));

        mvc.perform(patch("/api/overtime/usages/{id}", usageId)
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"hours\":3}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LINKED_USAGE_MANAGED_BY_ABSENCE"));

        mvc.perform(delete("/api/v1/overtime/usages/{id}", usageId)
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LINKED_USAGE_MANAGED_BY_ABSENCE"));
    }

    @Test
    void previewUsesCanonicalProfileTimezoneThroughLegacyAndV1Aliases() throws Exception {
        setOvertimeEnabled(owner, true);
        owner.setWorkTimezone("Europe/Berlin");
        owner.setDisplayTimezone("Europe/Berlin");
        users.save(owner);
        String body = """
                {"date":"2026-03-29","startDateTime":"2026-03-29T00:00","endDateTime":"2026-03-29T08:00","breakMinutes":0,"plannedHours":0}
                """;

        for (String path : new String[]{"/api/overtime/preview", "/api/v1/overtime/preview"}) {
            mvc.perform(post(path)
                            .with(user(owner.getUsername()).roles("USER"))
                            .with(csrf())
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.calculated").value(true))
                    .andExpect(jsonPath("$.elapsedMinutes").value(420))
                    .andExpect(jsonPath("$.creditedHours").value(7.0))
                    .andExpect(jsonPath("$.sourceTimezone").value("Europe/Berlin"));
        }
    }

    @Test
    void previewReturnsOkForAZeroCalculatedDraftThroughLegacyAndV1Aliases() throws Exception {
        setOvertimeEnabled(owner, true);
        String body = """
                {"date":"2026-07-25","startDateTime":"2026-07-25T08:00","endDateTime":"2026-07-25T20:00","breakMinutes":0,"plannedHours":12}
                """;

        for (String path : new String[]{"/api/overtime/preview", "/api/v1/overtime/preview"}) {
            mvc.perform(post(path)
                            .with(user(owner.getUsername()).roles("USER"))
                            .with(csrf())
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.calculated").value(true))
                    .andExpect(jsonPath("$.elapsedMinutes").value(720))
                    .andExpect(jsonPath("$.creditedMinutes").value(0))
                    .andExpect(jsonPath("$.creditedHours").value(0.0));
        }
    }

    @Test
    void accountPageFiltersAndExportsMatchTheVisibleLedger() throws Exception {
        setOvertimeEnabled(owner, true);
        overtimeService.createCredit(owner, manual("2026-07-01", 2.0, "старое"));
        overtimeService.createCredit(owner, manual("2026-07-02", 3.0, "ППР; ночь"));
        overtimeService.createCredit(owner, manual("2026-07-03", 4.0, "резерв"));
        overtimeService.createUsage(owner, new ru.daniil.shifts.dto.Dtos.OvertimeUsageCreateRequest(
                "2026-07-05", 4.0, "отгул"));

        mvc.perform(get("/api/v1/overtime/account-page")
                        .with(user(owner.getUsername()).roles("USER"))
                        .param("status", "partial")
                        .param("q", "ппр")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credits.total").value(1))
                .andExpect(jsonPath("$.credits.items[0].workedDate").value("2026-07-02"))
                .andExpect(jsonPath("$.credits.items[0].usedHours").value(2.0))
                .andExpect(jsonPath("$.usages.length()").value(1))
                .andExpect(jsonPath("$.usages[0].usageDate").value("2026-07-05"))
                .andExpect(jsonPath("$.usages[0].hours").value(4.0))
                .andExpect(jsonPath("$.usages[0].allocations.length()").value(2));

        byte[] csv = mvc.perform(get("/api/overtime/export.csv")
                        .with(user(owner.getUsername()).roles("USER"))
                        .param("from", "2026-07-02")
                        .param("to", "2026-07-02")
                        .param("status", "partial")
                        .param("q", "ппр"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"overtime-ledger.csv\""))
                .andExpect(content().contentType("text/csv"))
                .andReturn().getResponse().getContentAsByteArray();
        String csvText = new String(csv, StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertTrue(csvText.startsWith("\ufeff"));
        org.junit.jupiter.api.Assertions.assertTrue(csvText.contains("2026-07-02"));
        org.junit.jupiter.api.Assertions.assertFalse(csvText.contains("2026-07-01"));

        mvc.perform(get("/api/v1/overtime/export.xls")
                        .with(user(owner.getUsername()).roles("USER"))
                        .param("q", "ппр"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"overtime-ledger.xls\""))
                .andExpect(content().contentTypeCompatibleWith("application/vnd.ms-excel"))
                .andExpect(content().string(containsString("Журнал переработок")))
                .andExpect(content().string(containsString("ППР; ночь")));
    }

    @Test
    void validationAndMalformedIntervalsUseStableErrorEnvelopes() throws Exception {
        setOvertimeEnabled(owner, true);

        mvc.perform(post("/api/overtime/credits")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"date\":\"\",\"hours\":0,\"breakMinutes\":-1,\"plannedHours\":101}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.date").exists())
                .andExpect(jsonPath("$.fields.hours").exists())
                .andExpect(jsonPath("$.fields.breakMinutes").exists())
                .andExpect(jsonPath("$.fields.plannedHours").exists())
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        mvc.perform(post("/api/v1/overtime/credits")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"date\":\"2026-07-01\",\"startDateTime\":\"2026-07-01T20:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        mvc.perform(post("/api/overtime/usages")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"date\":\"2026-07-01\",\"hours\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DIRECT_USAGE_RETIRED"));

        mvc.perform(get("/api/overtime/balance")
                        .with(user(owner.getUsername()).roles("USER"))
                        .param("from", "01.07.2026")
                        .param("to", "2026-07-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void disabledModuleGuardsAllEndpointsWithoutDeletingAccountData() throws Exception {
        setOvertimeEnabled(owner, true);
        var account = overtimeService.createCredit(owner, manual("2026-07-01", 2.0, "сохранить"));
        long id = account.credits().get(0).id();
        setOvertimeEnabled(owner, false);

        mvc.perform(get("/api/overtime/account")
                        .with(user(owner.getUsername()).roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_DISABLED"))
                .andExpect(jsonPath("$.moduleKey").value("overtime"));

        mvc.perform(get("/api/overtime/account-page")
                        .with(user(owner.getUsername()).roles("USER")))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/overtime/export.csv")
                        .with(user(owner.getUsername()).roles("USER")))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/overtime/credits")
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"date\":\"2026-07-02\",\"hours\":1}"))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/overtime/credits/{id}", id)
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"hours\":3}"))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/overtime/credits/{id}", id)
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        setOvertimeEnabled(owner, true);
        mvc.perform(get("/api/v1/overtime/account")
                        .with(user(owner.getUsername()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credits", hasSize(1)))
                .andExpect(jsonPath("$.credits[0].id").value(id))
                .andExpect(jsonPath("$.credits[0].reason").value("сохранить"));
    }

    @Test
    void writesRequireCsrfAndReadsRequireAuthentication() throws Exception {
        setOvertimeEnabled(owner, true);

        mvc.perform(post("/api/overtime/credits")
                        .with(user(owner.getUsername()).roles("USER"))
                        .contentType("application/json")
                        .content("{\"date\":\"2026-07-01\",\"hours\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mvc.perform(get("/api/overtime/account"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void foreignCreditAndUsageIdsRemainNotFound() throws Exception {
        setOvertimeEnabled(owner, true);
        setOvertimeEnabled(other, true);
        var foreignCreditAccount = overtimeService.createCredit(other, manual("2026-07-01", 3.0, "чужое"));
        long foreignCreditId = foreignCreditAccount.credits().get(0).id();
        var foreignUsageAccount = overtimeService.createUsage(other,
                new ru.daniil.shifts.dto.Dtos.OvertimeUsageCreateRequest("2026-07-02", 1.0, "чужой отгул"));
        long foreignUsageId = foreignUsageAccount.usages().get(0).id();

        mvc.perform(patch("/api/overtime/credits/{id}", foreignCreditId)
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"hours\":2}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(delete("/api/v1/overtime/credits/{id}", foreignCreditId)
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        mvc.perform(patch("/api/overtime/usages/{id}", foreignUsageId)
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"hours\":0.5}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mvc.perform(delete("/api/v1/overtime/usages/{id}", foreignUsageId)
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }


    @Test
    void legacyMigrationPreviewAndV1MigrateExposeExactReconstructedIntervals() throws Exception {
        setOvertimeEnabled(owner, true);
        owner.setWorkTimezone("Europe/Moscow");
        users.save(owner);
        var created = overtimeService.createCredit(owner, new OvertimeCreditCreateRequest(
                "2026-07-20", null, "2026-07-20T17:00", "2026-07-20T20:00",
                0, 0.0, null, "legacy"));
        long creditId = created.credits().get(0).id();
        OvertimeCredit legacy = credits.findByOwnerAndId(owner, creditId).orElseThrow();
        legacy.setStartAtInstant(null);
        legacy.setEndAtInstant(null);
        legacy.setCreditedStartAtInstant(null);
        legacy.setCreditedEndAtInstant(null);
        legacy.setSourceTimezone(null);
        credits.saveAndFlush(legacy);
        overtimeService.createUsage(owner, new ru.daniil.shifts.dto.Dtos.OvertimeUsageCreateRequest(
                "2026-07-21", 1.0, "отгул"));

        String body = "{\"creditIds\":[" + creditId + "],\"sourceTimezone\":\"Asia/Yekaterinburg\"}";
        mvc.perform(post("/api/overtime/legacy-credits/preview")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedCount").value(1))
                .andExpect(jsonPath("$.migratableCount").value(1))
                .andExpect(jsonPath("$.credits[0].sourceTimezone").value("Asia/Yekaterinburg"));

        mvc.perform(post("/api/v1/overtime/legacy-credits/migrate")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf())
                        .contentType("application/json")
                        .content("{\"creditIds\":[],\"sourceTimezone\":\"Asia/Yekaterinburg\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Выбери хотя бы одну")));

        mvc.perform(post("/api/v1/overtime/legacy-credits/migrate")
                        .with(user(owner.getUsername()).roles("USER")).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.migratedCount").value(1))
                .andExpect(jsonPath("$.account.credits[0].migratedFromLegacy").value(true))
                .andExpect(jsonPath("$.account.usages[0].allocations[0].exact").value(true))
                .andExpect(jsonPath("$.account.usages[0].allocations[0].reconstructed").value(true));
    }

    private long createCreditViaApi(String path, String date, double hours, String reason) throws Exception {
        String response = mvc.perform(post(path)
                        .with(user(owner.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"date\":\"" + date + "\",\"hours\":" + hours + ",\"reason\":\"" + reason + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode credits = objectMapper.readTree(response).path("credits");
        for (JsonNode credit : credits) {
            if (date.equals(credit.path("workedDate").asText())) {
                return credit.path("id").asLong();
            }
        }
        throw new AssertionError("API did not return the created credit for " + date);
    }

    private OvertimeCreditCreateRequest manual(String date, double hours, String reason) {
        return new OvertimeCreditCreateRequest(date, null, null, null, null, null, hours, reason);
    }

    private void setVacationEnabled(AppUser account, boolean enabled) throws Exception {
        mvc.perform(patch("/api/modules")
                        .with(user(account.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"enabled\":{\"vacation\":" + enabled + "}}"))
                .andExpect(status().isOk());
    }

    private void setOvertimeEnabled(AppUser account, boolean enabled) throws Exception {
        mvc.perform(patch("/api/modules")
                        .with(user(account.getUsername()).roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"enabled\":{\"overtime\":" + enabled + "}}"))
                .andExpect(status().isOk());
    }
}
