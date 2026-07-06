package ru.daniil.shifts.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.daniil.shifts.service.exception.ApiException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Единый формат ошибок API: фронтенду проще показать нормальное сообщение,
 * а не просто «400» без пояснения.
 */
@RestControllerAdvice
public class ApiExceptionHandler {


    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> api(ApiException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError err : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(err.getField(), err.getDefaultMessage());
        }

        String first = fields.values().stream().findFirst().orElse("Проверь данные формы");
        return ResponseEntity.badRequest().body(Map.of(
                "error", first,
                "fields", fields
        ));
    }


    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, String>> missingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Не хватает параметра запроса: " + ex.getParameterName()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> unreadableJson() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Некорректный JSON в запросе"));
    }
}
