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
import ru.daniil.shifts.dto.Dtos.DayNoteCreateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.DayNoteService;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Owner-scoped, bounded and cache-safe export of independent daily notes. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExportControllerTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired DayNoteService dayNotes;

    AppUser owner;
    long primaryId;
    long siblingId;
    long oldYearId;
    long yamlId;

    @BeforeEach
    void setUp() {
        owner = users.save(new AppUser("export-owner", "{noop}x"));
        AppUser stranger = users.save(new AppUser("export-stranger", "{noop}x"));

        primaryId = dayNotes.create(owner,
                new DayNoteCreateRequest("2026-07-03", "Мой день", "текст заметки", false)).id();
        siblingId = dayNotes.create(owner,
                new DayNoteCreateRequest("2026-07-03", "Вторая", "соседняя заметка", true)).id();
        oldYearId = dayNotes.create(owner,
                new DayNoteCreateRequest("2025-12-31", null, "старый год", false)).id();
        yamlId = dayNotes.create(owner,
                new DayNoteCreateRequest("2026-07-05", "Night \"Ops\"\nTeam\\", "yaml-safe", false)).id();
        dayNotes.create(stranger,
                new DayNoteCreateRequest("2026-07-03", "Secret", "СЕКРЕТ ЧУЖОГО ЧЕЛОВЕКА", false));
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
    void экспортСодержитКаждуюНезависимуюЗаметкуОтдельнымФайлом() throws Exception {
        Map<String, String> files = unzip(fetchExport());

        String primary = fileById(files, primaryId);
        String sibling = fileById(files, siblingId);
        String oldYear = fileById(files, oldYearId);
        assertNotNull(primary);
        assertNotNull(sibling);
        assertNotNull(oldYear);
        assertTrue(files.containsKey("README.md"));
        assertTrue(primary.contains("date: 2026-07-03"));
        assertTrue(primary.contains("title: \"Мой день\""));
        assertTrue(primary.contains("# Мой день\n\nтекст заметки"));
        assertTrue(sibling.contains("pinned: true"));
        assertTrue(oldYear.contains("старый год"));
    }

    @Test
    void yamlПоляЭкранируются() throws Exception {
        String markdown = fileById(unzip(fetchExport()), yamlId);

        assertNotNull(markdown);
        assertTrue(markdown.contains("title: \"Night \\\"Ops\\\"\\nTeam\\\\\""), markdown);
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

    private static String fileById(Map<String, String> files, long id) {
        return files.entrySet().stream()
                .filter(entry -> entry.getKey().endsWith("-" + id + ".md"))
                .map(Map.Entry::getValue)
                .findFirst().orElse(null);
    }
}
