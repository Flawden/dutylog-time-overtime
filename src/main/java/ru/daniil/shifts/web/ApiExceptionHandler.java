package ru.daniil.shifts.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.daniil.shifts.service.exception.ApiException;

import java.util.LinkedHashMap;
import java.util.Map;

/** Central stable error envelope for web, PWA and Android clients. */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> api(ApiException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatus()).body(ApiErrorResponse.of(
                ex.getCode(), ex.getMessage(), Map.of(), ApiErrorWriter.requestId(request)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException ex,
                                                       HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError err : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(err.getField(), err.getDefaultMessage());
        }
        String first = fields.values().stream().findFirst().orElse("Проверь данные формы");
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(
                "VALIDATION_FAILED", first, fields, ApiErrorWriter.requestId(request)));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> missingParam(MissingServletRequestParameterException ex,
                                                         HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(
                "MISSING_PARAMETER",
                "Не хватает параметра запроса: " + ex.getParameterName(),
                Map.of(ex.getParameterName(), "required"),
                ApiErrorWriter.requestId(request)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> unreadableJson(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiErrorResponse.of(
                "INVALID_JSON", "Некорректный JSON в запросе", Map.of(), ApiErrorWriter.requestId(request)));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> typeMismatch(MethodArgumentTypeMismatchException ex,
                                                         HttpServletRequest request) {
        String name = ex.getName() == null ? "parameter" : ex.getName();
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(
                "INVALID_PARAMETER",
                "Некорректное значение параметра: " + name,
                Map.of(name, "invalid"),
                ApiErrorWriter.requestId(request)));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> constraintViolation(ConstraintViolationException ex,
                                                                HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(violation ->
                fields.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));
        String first = fields.values().stream().findFirst().orElse("Проверь параметры запроса");
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(
                "VALIDATION_FAILED", first, fields, ApiErrorWriter.requestId(request)));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> noResource(NoResourceFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiErrorResponse.of(
                "NOT_FOUND", "Ресурс не найден", Map.of(), ApiErrorWriter.requestId(request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> unexpected(Exception ex, HttpServletRequest request) {
        String requestId = ApiErrorWriter.requestId(request);
        log.error("Unexpected API failure requestId={} method={} path={} exceptionType={}", requestId,
                request.getMethod(), request.getRequestURI(), ex.getClass().getName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiErrorResponse.of(
                "INTERNAL_ERROR", "Внутренняя ошибка сервера", Map.of(), requestId));
    }
}
