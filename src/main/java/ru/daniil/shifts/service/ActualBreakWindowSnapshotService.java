package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import ru.daniil.shifts.dto.Dtos.ActualWorkBreakWindowRequest;
import ru.daniil.shifts.model.ActualWorkBreakWindow;
import ru.daniil.shifts.model.ActualWorkInterval;
import ru.daniil.shifts.model.WorkBreakAuthority;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Freezes exact unpaid-break evidence for explicit Actual Work facts.
 *
 * Request-local boundaries are the durable audit anchor. Their absolute
 * instants are resolved under the factual interval's historical Work Timezone.
 * An intentional historical timezone correction re-resolves those same local
 * boundaries instead of preserving stale absolute projections.
 */
@Service
public class ActualBreakWindowSnapshotService {
    private final WorkBreakWindowAuthorityService breakAuthority;

    public ActualBreakWindowSnapshotService(
            WorkBreakWindowAuthorityService breakAuthority
    ) {
        this.breakAuthority = breakAuthority;
    }

    public ExplicitSnapshot resolveRequested(
            ActualWorkIdentityService.Identity identity,
            LocalDateTime sourceWorkStart,
            LocalDateTime sourceWorkEnd,
            Integer compatibilityBreakMinutes,
            List<ActualWorkBreakWindowRequest> requested
    ) {
        if (identity == null) {
            throw new IllegalArgumentException("Actual Work identity is required");
        }
        if (requested == null) {
            throw new IllegalArgumentException(
                    "Explicit break request list is required"
            );
        }

        List<WorkBreakWindowAuthorityService.SourceLocalBreakWindow> source =
                requested.stream()
                        .map(this::parseRequest)
                        .toList();

        List<WorkBreakWindowAuthorityService.ResolvedBreakWindow> resolved;
        try {
            resolved = breakAuthority.resolveSourceLocal(
                    identity.startInstant(),
                    identity.endInstant(),
                    sourceWorkStart,
                    sourceWorkEnd,
                    identity.sourceTimezone(),
                    source
            );
        } catch (IllegalArgumentException | ArithmeticException ex) {
            throw ApiException.badRequest(
                    "Некорректные точные перерывы: " + ex.getMessage()
            );
        }

        int derivedBreakMinutes = derivedBreakMinutes(
                identity,
                resolved,
                false
        );

        if (compatibilityBreakMinutes != null
                && compatibilityBreakMinutes != derivedBreakMinutes) {
            throw ApiException.badRequest(
                    "breakMinutes конфликтует с точными окнами перерыва. "
                            + "Убери breakMinutes или передай "
                            + derivedBreakMinutes + "."
            );
        }

        return new ExplicitSnapshot(
                derivedBreakMinutes,
                resolved
        );
    }

    public void capture(
            ActualWorkInterval interval,
            ExplicitSnapshot snapshot
    ) {
        if (interval == null || snapshot == null) {
            throw new IllegalArgumentException(
                    "Actual Work interval and explicit break snapshot are required"
            );
        }

        List<ActualWorkBreakWindow> windows =
                snapshot.windows().stream()
                        .map(window -> new ActualWorkBreakWindow(
                                interval,
                                window.position(),
                                window.sourceStart(),
                                window.sourceEnd(),
                                window.startInstant(),
                                window.endInstant(),
                                window.sourceTimezone()
                        ))
                        .toList();

        interval.captureExplicitBreakWindows(
                snapshot.breakMinutes(),
                windows
        );
    }

    /**
     * Rebuilds explicit break absolute identities after an intentional
     * historical Work Timezone correction. The source-local break evidence is
     * preserved exactly; only start/end instants and source timezone change.
     */
    public int reconstructHistoricalIdentity(
            ActualWorkInterval interval,
            ActualWorkIdentityService.Identity identity
    ) {
        if (interval == null || identity == null) {
            throw new IllegalArgumentException(
                    "Actual Work interval and identity are required"
            );
        }
        if (interval.getBreakAuthority() != WorkBreakAuthority.EXPLICIT_WINDOWS) {
            return Math.max(0, interval.getBreakMinutes());
        }

        LocalDateTime sourceWorkStart = interval.getWorkDate()
                .atTime(interval.getStartTime());
        LocalDateTime sourceWorkEnd = interval.getEndDate()
                .atTime(interval.getEndTime());

        List<WorkBreakWindowAuthorityService.SourceLocalBreakWindow> source =
                interval.getBreakWindows().stream()
                        .map(window ->
                                new WorkBreakWindowAuthorityService.SourceLocalBreakWindow(
                                        window.getPosition(),
                                        window.getSourceStartLocal(),
                                        window.getSourceEndLocal()
                                )
                        )
                        .toList();

        List<WorkBreakWindowAuthorityService.ResolvedBreakWindow> resolved;
        try {
            resolved = breakAuthority.resolveSourceLocal(
                    identity.startInstant(),
                    identity.endInstant(),
                    sourceWorkStart,
                    sourceWorkEnd,
                    identity.sourceTimezone(),
                    source
            );
        } catch (IllegalArgumentException | ArithmeticException ex) {
            throw ApiException.conflict(
                    "ACTUAL_EXPLICIT_BREAK_INVALID_AFTER_CONTEXT_CHANGE",
                    "После изменения рабочего часового пояса точные перерывы "
                            + "фактического интервала " + interval.getId()
                            + " больше не образуют допустимый снимок: "
                            + ex.getMessage()
            );
        }

        int derivedBreakMinutes = derivedBreakMinutes(
                identity,
                resolved,
                true
        );

        Map<Integer, WorkBreakWindowAuthorityService.ResolvedBreakWindow> byPosition =
                resolved.stream().collect(Collectors.toMap(
                        WorkBreakWindowAuthorityService.ResolvedBreakWindow::position,
                        Function.identity()
                ));

        for (ActualWorkBreakWindow window : interval.getBreakWindows()) {
            WorkBreakWindowAuthorityService.ResolvedBreakWindow rebuilt =
                    byPosition.get(window.getPosition());
            if (rebuilt == null) {
                throw ApiException.conflict(
                        "ACTUAL_EXPLICIT_BREAK_INVALID_AFTER_CONTEXT_CHANGE",
                        "Не удалось восстановить точный перерыв позиции "
                                + window.getPosition()
                );
            }
            window.reconstructAbsoluteIdentity(
                    rebuilt.startInstant(),
                    rebuilt.endInstant(),
                    rebuilt.sourceTimezone()
            );
        }

        interval.setBreakMinutes(derivedBreakMinutes);
        return derivedBreakMinutes;
    }

    private WorkBreakWindowAuthorityService.SourceLocalBreakWindow parseRequest(
            ActualWorkBreakWindowRequest request
    ) {
        if (request == null || request.position() == null) {
            throw ApiException.badRequest(
                    "Каждый точный перерыв должен иметь позицию"
            );
        }

        LocalDateTime start = parseLocal(
                request.sourceStartLocal(),
                "Начало точного перерыва"
        );
        LocalDateTime end = parseLocal(
                request.sourceEndLocal(),
                "Окончание точного перерыва"
        );

        try {
            return new WorkBreakWindowAuthorityService.SourceLocalBreakWindow(
                    request.position(),
                    start,
                    end
            );
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(
                    "Некорректный точный перерыв: " + ex.getMessage()
            );
        }
    }

    private LocalDateTime parseLocal(String value, String label) {
        final LocalDateTime parsed;
        try {
            parsed = LocalDateTime.parse(value);
        } catch (DateTimeParseException | NullPointerException ex) {
            throw ApiException.badRequest(
                    label + " должно быть в формате yyyy-MM-ddTHH:mm"
            );
        }
        if (parsed.getSecond() != 0 || parsed.getNano() != 0) {
            throw ApiException.badRequest(
                    label + " должно иметь минутную точность yyyy-MM-ddTHH:mm"
            );
        }
        return parsed;
    }

    private int derivedBreakMinutes(
            ActualWorkIdentityService.Identity identity,
            List<WorkBreakWindowAuthorityService.ResolvedBreakWindow> resolved,
            boolean historicalCorrection
    ) {
        long paidMinutes = breakAuthority.paidMinutes(
                identity.startInstant(),
                identity.endInstant(),
                resolved
        );
        long breakMinutes = (long) identity.elapsedMinutes() - paidMinutes;

        if (breakMinutes < 0L
                || breakMinutes > 1440L
                || breakMinutes >= identity.elapsedMinutes()) {
            String message =
                    "Суммарный точный перерыв должен быть от 0 до 1440 минут "
                            + "и короче фактического интервала";
            if (historicalCorrection) {
                throw ApiException.conflict(
                        "ACTUAL_EXPLICIT_BREAK_INVALID_AFTER_CONTEXT_CHANGE",
                        message
                );
            }
            throw ApiException.badRequest(message);
        }

        return Math.toIntExact(breakMinutes);
    }

    public record ExplicitSnapshot(
            int breakMinutes,
            List<WorkBreakWindowAuthorityService.ResolvedBreakWindow> windows
    ) {
        public ExplicitSnapshot {
            windows = windows == null ? List.of() : List.copyOf(windows);
        }
    }
}
