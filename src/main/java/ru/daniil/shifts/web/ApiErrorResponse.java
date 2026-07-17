package ru.daniil.shifts.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Stable machine-readable API error contract.
 *
 * The legacy {@code error} field is intentionally retained so the existing
 * web/PWA client keeps working while Android uses {@code code} and
 * {@code fields} instead of parsing localized text.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiErrorResponse(
        String code,
        String message,
        String error,
        Map<String, String> fields,
        String moduleKey,
        String requestId,
        String timestamp
) {
    public static ApiErrorResponse of(String code,
                                      String message,
                                      Map<String, String> fields,
                                      String requestId) {
        String safeCode = code == null || code.isBlank() ? "INTERNAL_ERROR" : code;
        String safeMessage = message == null || message.isBlank() ? "Unexpected error" : message;
        return new ApiErrorResponse(
                safeCode,
                safeMessage,
                safeMessage,
                fields == null ? Map.of() : fields,
                moduleKey(safeCode, safeMessage),
                requestId,
                Instant.now().toString()
        );
    }

    private static String moduleKey(String code, String message) {
        if (!"MODULE_DISABLED".equals(code) || message == null) return null;
        String marker = "MODULE_DISABLED:";
        if (!message.startsWith(marker)) return null;
        String key = message.substring(marker.length()).trim();
        return key.isEmpty() ? null : key;
    }
}
