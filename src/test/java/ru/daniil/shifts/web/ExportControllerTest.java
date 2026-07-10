package ru.daniil.shifts.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.DayUpsertRequest;
import ru.daniil.shifts.model.AppUser;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Экспорт заметок: файлы лежат по датам, содержимое не искажается,
 * и — главное — в архив не попадает ни байта чужих данных.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExportControllerTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired DayEntryService dayEntries;

    AppUser owner;

    @BeforeEach
    void setUp() {
        owner = users.save(new AppUser("export-owner", "{noop}x"));
        AppUser stranger = users.save(new AppUser("export-stranger", "{noop}x"));

        dayEntries.upsert(owner, "2026-07-03",
                new DayUpsertRequest(null, "# Мой день\nтекст заметки", null, null, null));
        dayEntries.upsert(owner, "2025-12-31",
                new DayUpsertRequest(null, "старый год", null, null, null));
        // день без заметки — в экспорт попадать не должен
        dayEntries.upsert(owner, "2026-07-04",
                new DayUpsertRequest(null, null, "🔥", null, null));
        // чужая заметка — не должна попасть НИКОГДА
        dayEntries.upsert(stranger, "2026-07-03",
                new DayUpsertRequest(null, "СЕКРЕТ ЧУЖОГО ЧЕЛОВЕКА", null, null, null));
    }

    private Map<String, String> unzip(byte[] body) throws Exception {
        Map<String, String> files = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(body), StandardCharsets.UTF_8)) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                files.put(e.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return files;
    }

    @Test
    void экспортСодержитЗаметкиПоДатамИГодовымПапкам() throws Exception {
        byte[] body = mvc.perform(get("/api/export/notes")
                        .with(user("export-owner").roles("USER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        Map<String, String> files = unzip(body);

        assertTrue(files.containsKey("2026/2026-07-03.md"), "заметка лежит в папке года: " + files.keySet());
        assertTrue(files.containsKey("2025/2025-12-31.md"));
        assertTrue(files.containsKey("README.md"));
        assertFalse(files.containsKey("2026/2026-07-04.md"), "день без заметки не экспортируется");

        String md = files.get("2026/2026-07-03.md");
        assertTrue(md.contains("date: 2026-07-03"), "front matter с датой");
        assertTrue(md.contains("# Мой день\nтекст заметки"), "текст заметки не искажён");
    }

    @Test
    void чужиеЗаметкиНеУтекают() throws Exception {
        byte[] body = mvc.perform(get("/api/export/notes")
                        .with(user("export-owner").roles("USER")))
                .andReturn().getResponse().getContentAsByteArray();

        String everything = String.join("\n", unzip(body).values());
        assertFalse(everything.contains("СЕКРЕТ ЧУЖОГО"),
                "в экспорте не должно быть ни байта чужих данных");
    }

    @Test
    void анонимНеСкачивает() throws Exception {
        mvc.perform(get("/api/export/notes"))
                .andExpect(status().isUnauthorized());
    }
}
