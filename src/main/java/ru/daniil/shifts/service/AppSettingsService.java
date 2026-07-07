package ru.daniil.shifts.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppSetting;
import ru.daniil.shifts.repo.AppSettingRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Системные настройки, которыми управляет администратор из UI.
 * Значения хранятся в БД, чтобы переживать перезапуск контейнера и деплой.
 */
@Service
public class AppSettingsService {
    public static final String REGISTRATION_ENABLED = "registration.enabled";
    private static final String REGISTRATION_UPDATED_AT = "registration.updatedAt";
    private static final String REGISTRATION_UPDATED_BY = "registration.updatedBy";

    private final AppSettingRepository settings;
    private final boolean defaultRegistrationEnabled;

    public AppSettingsService(AppSettingRepository settings,
                              @Value("${dutylog.registration.default-enabled:true}") boolean defaultRegistrationEnabled) {
        this.settings = settings;
        this.defaultRegistrationEnabled = defaultRegistrationEnabled;
    }

    @Transactional(readOnly = true)
    public boolean isRegistrationEnabled() {
        return settings.findById(REGISTRATION_ENABLED)
                .map(AppSetting::getValue)
                .map(AppSettingsService::parseBoolean)
                .orElse(defaultRegistrationEnabled);
    }

    @Transactional
    public void setRegistrationEnabled(boolean enabled, String changedBy) {
        put(REGISTRATION_ENABLED, Boolean.toString(enabled));
        put(REGISTRATION_UPDATED_AT, Instant.now().toString());
        put(REGISTRATION_UPDATED_BY, changedBy == null || changedBy.isBlank() ? "admin" : changedBy.trim());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> registrationStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean enabled = isRegistrationEnabled();
        result.put("enabled", enabled);
        result.put("mode", enabled ? "open" : "closed");
        result.put("source", settings.existsById(REGISTRATION_ENABLED) ? "database" : "default");
        result.put("updatedAt", valueOrNull(REGISTRATION_UPDATED_AT));
        result.put("updatedBy", valueOrNull(REGISTRATION_UPDATED_BY));
        return result;
    }

    private void put(String key, String value) {
        AppSetting setting = settings.findById(key).orElseGet(() -> new AppSetting(key, value));
        setting.setValue(value);
        settings.save(setting);
    }

    private String valueOrNull(String key) {
        return settings.findById(key).map(AppSetting::getValue).orElse(null);
    }

    private static boolean parseBoolean(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("true") || normalized.equals("1") || normalized.equals("yes") || normalized.equals("on");
    }
}
