package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppSetting;
import ru.daniil.shifts.repo.AppSettingRepository;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Persistent registration switch: defaults, metadata and legacy boolean parsing. */
@SpringBootTest
@Transactional
class AppSettingsServiceTest {

    @Autowired AppSettingsService service;
    @Autowired AppSettingRepository settings;

    @Test
    void missingDatabaseSettingUsesConfiguredDefaultWithoutInventingAuditMetadata() {
        settings.deleteAll();

        Map<String, Object> status = service.registrationStatus();
        assertEquals(true, status.get("enabled"));
        assertEquals("open", status.get("mode"));
        assertEquals("default", status.get("source"));
        assertNull(status.get("updatedAt"));
        assertNull(status.get("updatedBy"));
    }

    @Test
    void updatePersistsModeTimestampAndTrimmedAdministratorName() {
        service.setRegistrationEnabled(false, "  root-admin  ");

        Map<String, Object> status = service.registrationStatus();
        assertEquals(false, status.get("enabled"));
        assertEquals("closed", status.get("mode"));
        assertEquals("database", status.get("source"));
        assertEquals("root-admin", status.get("updatedBy"));
        assertNotNull(status.get("updatedAt"));

        service.setRegistrationEnabled(true, "   ");
        Map<String, Object> reopened = service.registrationStatus();
        assertEquals(true, reopened.get("enabled"));
        assertEquals("admin", reopened.get("updatedBy"));
    }

    @Test
    void legacyTrueSpellingsRemainAcceptedAndEverythingElseIsFalse() {
        settings.save(new AppSetting(AppSettingsService.REGISTRATION_ENABLED, " YES "));
        assertTrue(service.isRegistrationEnabled());

        settings.findById(AppSettingsService.REGISTRATION_ENABLED).orElseThrow().setValue("on");
        assertTrue(service.isRegistrationEnabled());

        settings.findById(AppSettingsService.REGISTRATION_ENABLED).orElseThrow().setValue("1");
        assertTrue(service.isRegistrationEnabled());

        settings.findById(AppSettingsService.REGISTRATION_ENABLED).orElseThrow().setValue("off");
        assertFalse(service.isRegistrationEnabled());
    }
}
