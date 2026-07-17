package ru.daniil.shifts.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ru.daniil.shifts.config.RequestDiagnosticsFilter;
import ru.daniil.shifts.service.exception.ApiException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiErrorInfrastructureTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void errorResponseSuppliesSafeDefaultsWithoutInventingAModuleKey() {
        ApiErrorResponse response = ApiErrorResponse.of(null, " ", null, "req-1");

        assertEquals("INTERNAL_ERROR", response.code());
        assertEquals("Unexpected error", response.message());
        assertEquals("Unexpected error", response.error());
        assertEquals(Map.of(), response.fields());
        assertNull(response.moduleKey());
        assertEquals("req-1", response.requestId());
        assertNotNull(response.timestamp());
    }

    @Test
    void moduleDisabledMarkerProducesAStructuredModuleKeyOnlyForExactContractCode() {
        ApiErrorResponse structured = ApiErrorResponse.of(
                "MODULE_DISABLED", "MODULE_DISABLED:tasks", Map.of(), "req-2");
        ApiErrorResponse wrongCode = ApiErrorResponse.of(
                "FORBIDDEN", "MODULE_DISABLED:tasks", Map.of(), "req-3");
        ApiErrorResponse emptyKey = ApiErrorResponse.of(
                "MODULE_DISABLED", "MODULE_DISABLED:   ", Map.of(), "req-4");

        assertEquals("tasks", structured.moduleKey());
        assertNull(wrongCode.moduleKey());
        assertNull(emptyKey.moduleKey());
    }

    @Test
    void apiErrorWriterPreservesStatusUtf8FieldsAndCorrelationId() throws Exception {
        ApiErrorWriter writer = new ApiErrorWriter(objectMapper);
        MockHttpServletRequest request = request("POST", "/api/test", "writer-42");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(request, response, 422, "INVALID_TEST", "Проверь поле",
                Map.of("name", "required"));

        assertEquals(422, response.getStatus());
        assertEquals("application/json", response.getContentType());
        assertEquals("UTF-8", response.getCharacterEncoding());
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertEquals("INVALID_TEST", body.path("code").asText());
        assertEquals("Проверь поле", body.path("message").asText());
        assertEquals("required", body.path("fields").path("name").asText());
        assertEquals("writer-42", body.path("requestId").asText());
    }

    @Test
    void apiExceptionFactoriesKeepStableCodesIncludingModuleAndFallbackStatuses() {
        assertEquals("BAD_REQUEST", ApiException.badRequest("bad").getCode());
        assertEquals("AUTH_BAD", ApiException.badRequest("AUTH_BAD", "bad").getCode());
        assertEquals("TOKEN_INVALID", ApiException.unauthorized("TOKEN_INVALID", "bad").getCode());
        assertEquals("NOT_FOUND", ApiException.notFound("missing").getCode());
        assertEquals("CONFLICT", ApiException.conflict("conflict").getCode());
        assertEquals("PAYLOAD_TOO_LARGE", ApiException.payloadTooLarge("large").getCode());
        assertEquals("MODULE_DISABLED", ApiException.forbidden("MODULE_DISABLED:notes").getCode());
        assertEquals("FORBIDDEN", ApiException.forbidden("denied").getCode());
        assertEquals("HTTP_418", new ApiException(HttpStatus.I_AM_A_TEAPOT, "brew").getCode());
        assertEquals("BAD_REQUEST", new ApiException(HttpStatus.BAD_REQUEST, " ", "bad").getCode());
    }

    @Test
    void handlerPreservesCustomApiCodeAndStructuredModuleMetadata() {
        MockHttpServletRequest request = request("GET", "/api/tasks", "handler-1");
        ApiException exception = ApiException.forbidden("MODULE_DISABLED:tasks");

        ResponseEntity<ApiErrorResponse> response = handler.api(exception, request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("MODULE_DISABLED", response.getBody().code());
        assertEquals("tasks", response.getBody().moduleKey());
        assertEquals("handler-1", response.getBody().requestId());
    }

    @Test
    void missingAndMalformedParametersUseStableMachineReadableFields() {
        MockHttpServletRequest request = request("GET", "/api/test", "handler-2");
        ResponseEntity<ApiErrorResponse> missing = handler.missingParam(
                new MissingServletRequestParameterException("from", "String"), request);
        ResponseEntity<ApiErrorResponse> mismatch = handler.typeMismatch(
                new MethodArgumentTypeMismatchException("abc", Integer.class, "page", null,
                        new NumberFormatException("abc")), request);

        assertEquals(HttpStatus.BAD_REQUEST, missing.getStatusCode());
        assertEquals("MISSING_PARAMETER", missing.getBody().code());
        assertEquals("required", missing.getBody().fields().get("from"));
        assertEquals("INVALID_PARAMETER", mismatch.getBody().code());
        assertEquals("invalid", mismatch.getBody().fields().get("page"));
        assertEquals("handler-2", mismatch.getBody().requestId());
    }

    @Test
    void unexpectedExceptionsAreHiddenBehindGeneric500Envelope() {
        MockHttpServletRequest request = request("POST", "/api/failure", "handler-3");
        ResponseEntity<ApiErrorResponse> response = handler.unexpected(
                new IllegalStateException("database password=secret-value"), request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", response.getBody().code());
        assertEquals("Внутренняя ошибка сервера", response.getBody().message());
        assertFalse(response.getBody().message().contains("secret-value"));
        assertEquals("handler-3", response.getBody().requestId());
    }

    private MockHttpServletRequest request(String method, String path, String requestId) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setAttribute(RequestDiagnosticsFilter.REQUEST_ID_ATTRIBUTE, requestId);
        return request;
    }
}
