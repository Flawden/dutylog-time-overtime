package ru.daniil.shifts.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import ru.daniil.shifts.config.RequestDiagnosticsFilter;

import java.io.IOException;
import java.util.Map;

/** Writes the same API error envelope from filters and Spring Security handlers. */
@Component
public class ApiErrorWriter {
    private final ObjectMapper objectMapper;

    public ApiErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletRequest request,
                      HttpServletResponse response,
                      int status,
                      String code,
                      String message) throws IOException {
        write(request, response, status, code, message, Map.of());
    }

    public void write(HttpServletRequest request,
                      HttpServletResponse response,
                      int status,
                      String code,
                      String message,
                      Map<String, String> fields) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiErrorResponse.of(
                code,
                message,
                fields,
                requestId(request)
        ));
    }

    public static String requestId(HttpServletRequest request) {
        if (request == null) return null;
        Object value = request.getAttribute(RequestDiagnosticsFilter.REQUEST_ID_ATTRIBUTE);
        return value == null ? null : value.toString();
    }
}
