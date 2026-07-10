package ru.daniil.shifts.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.DayUpsertRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.repo.ShiftTypeRepository;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.DayEntryService;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Owner-scoped, bounded and cache-safe notes export regressions. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExportControllerTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired DayEntryService dayEntries;
    @Autowired ShiftTypeRepository shiftTypes;

    AppUser owner;

    @BeforeEach
    void setUp() {
        owner = users.save(new AppUser("export-owner", "{noop}x"));
        AppUser stranger = users.save(new AppUser("export-stranger", "{noop}x"));

        dayEntries.upsert(owner, "2026-07-03",
                new DayUpsertRequest(null, "# Мой день\nтекст заметки", null, null, null));
        dayEntries.upsert(owner, "2025-12-31",
                new DayUpsertRequest(null, "старый год", null, null, null));
        dayEntries.upsert(owner, "2026-07-04",
                new DayUpsertRequest(null, null, "🔥", null, null));
        dayEntries.upsert(stranger, "2026-07-03",
                new DayUpsertRequest(null, "СЕКРЕТ ЧУЖОГО ЧЕЛОВЕКА", null, null, null));

        ShiftType unusual = shiftTypes.save(new ShiftType(
                owner, "Night \"Ops\"\nTeam\\", 8, "#123456", false));
        dayEntries.upsert(owner, "2026-07-05",
                new DayUpsertRequest(unusual.getId(), "yaml-safe", "🚀\nnext", null, null));
    }

    private byte[] fetchExport() throws Exception {
        MvcResult started = mvc.perform(get("/api/export/notes")
                        .with(user("export-owner").roles("USER")))
                .andExpect(request().asyncStarted())
                .andReturn();

        return mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andReturn().getResponse().getContentAsByteArray();
    }

    private Map<String, String> unzip(byte[] body) throws Exception {
        Map<String, String> files = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(body), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                files.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return files;
    }

    @Test
    void экспортСодержитЗаметкиПоДатамИГодовымПапкам() throws Exception {
        Map<String, String> files = unzip(fetchExport());

        assertTrue(files.containsKey("2026/2026-07-03.md"), "заметка лежит в папке года: " + files.keySet());
        assertTrue(files.containsKey("2025/2025-12-31.md"));
        assertTrue(files.containsKey("README.md"));
        assertFalse(files.containsKey("2026/2026-07-04.md"), "день без заметки не экспортируется");

        String markdown = files.get("2026/2026-07-03.md");
        assertTrue(markdown.contains("date: 2026-07-03"));
        assertTrue(markdown.contains("# Мой день\nтекст заметки"));
    }

    @Test
    void yamlПоляЭкранируются() throws Exception {
        String markdown = unzip(fetchExport()).get("2026/2026-07-05.md");

        assertTrue(markdown.contains("shift: \"Night \\\"Ops\\\"\\nTeam\\\\\""), markdown);
        assertTrue(markdown.contains("emoji: \"🚀\\nnext\""), markdown);
        assertTrue(markdown.endsWith("yaml-safe\n"));
    }

    @Test
    void чужиеЗаметкиНеУтекают() throws Exception {
        String everything = String.join("\n", unzip(fetchExport()).values());
        assertFalse(everything.contains("СЕКРЕТ ЧУЖОГО"));
    }

    @Test
    void анонимНеСкачивает() throws Exception {
        mvc.perform(get("/api/export/notes"))
                .andExpect(status().isUnauthorized());
    }
}
