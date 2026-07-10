package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.config.SecurityEventLogger;
import ru.daniil.shifts.dto.Dtos.QuickScenarioCreateRequest;
import ru.daniil.shifts.dto.Dtos.QuickScenarioDto;
import ru.daniil.shifts.dto.Dtos.QuickScenarioUpdateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.QuickScenario;
import ru.daniil.shifts.repo.QuickScenarioRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalTime;
import java.util.List;

@Service
public class QuickScenarioService {
    private final QuickScenarioRepository scenarios;
    private final SecurityEventLogger securityEvents;

    public QuickScenarioService(QuickScenarioRepository scenarios,
                                SecurityEventLogger securityEvents) {
        this.scenarios = scenarios;
        this.securityEvents = securityEvents;
    }

    @Transactional
    public List<QuickScenarioDto> list(AppUser user) {
        ensureDefaults(user);
        return scenarios.findByOwnerOrderBySortOrderAscIdAsc(user).stream().map(QuickScenarioDto::from).toList();
    }

    @Transactional
    public QuickScenarioDto create(AppUser user, QuickScenarioCreateRequest req) {
        if (req == null) throw ApiException.badRequest("Некорректный JSON в запросе");
        QuickScenario s = new QuickScenario(user);
        applyCreate(s, req);
        return QuickScenarioDto.from(scenarios.save(s));
    }

    @Transactional
    public QuickScenarioDto update(AppUser user, Long id, QuickScenarioUpdateRequest req) {
        if (req == null) throw ApiException.badRequest("Некорректный JSON в запросе");
        QuickScenario s = requireOwned(user, id);
        if (req.name() != null) s.setName(cleanName(req.name()));
        if (req.groupLabel() != null) s.setGroupLabel(blankToNull(req.groupLabel()));
        if (req.description() != null) s.setDescription(blankToNull(req.description()));
        if (req.startMode() != null) s.setStartMode(req.startMode());
        if (req.endMode() != null) s.setEndMode(req.endMode());
        if (req.endOffsetMinutes() != null) s.setEndOffsetMinutes(req.endOffsetMinutes());
        if (req.endFixedTime() != null) s.setEndFixedTime(parseOptionalTime(req.endFixedTime()));
        if (req.endNextDay() != null) s.setEndNextDay(req.endNextDay());
        if (req.breakMode() != null) s.setBreakMode(req.breakMode());
        if (req.customBreakMinutes() != null) s.setCustomBreakMinutes(req.customBreakMinutes());
        if (req.plannedMode() != null) s.setPlannedMode(req.plannedMode());
        if (req.customPlannedHours() != null) s.setCustomPlannedHours(req.customPlannedHours());
        if (req.reasonTemplate() != null) s.setReasonTemplate(blankToNull(req.reasonTemplate()));
        if (req.sortOrder() != null) s.setSortOrder(req.sortOrder());
        validateConsistency(s);
        return QuickScenarioDto.from(scenarios.save(s));
    }

    @Transactional
    public void delete(AppUser user, Long id) {
        scenarios.delete(requireOwned(user, id));
    }

    public QuickScenario requireOwned(AppUser user, Long id) {
        QuickScenario scenario = scenarios.findById(id)
                .orElseThrow(() -> ApiException.notFound("Сценарий не найден"));
        if (!scenario.getOwner().getId().equals(user.getId())) {
            securityEvents.warn("AUTHZ_OWNERSHIP_MISMATCH", user.getUsername(), "rejected",
                    "resource=quick_scenario id=" + id);
            throw ApiException.notFound("Сценарий не найден");
        }
        return scenario;
    }

    private void applyCreate(QuickScenario s, QuickScenarioCreateRequest req) {
        s.setName(cleanName(req.name()));
        s.setGroupLabel(blankToNull(req.groupLabel()));
        s.setDescription(blankToNull(req.description()));
        s.setStartMode(req.startMode() != null ? req.startMode() : "SHIFT_END");
        s.setEndMode(req.endMode() != null ? req.endMode() : "ADD_MINUTES");
        s.setEndOffsetMinutes(req.endOffsetMinutes() != null ? req.endOffsetMinutes() : 120);
        s.setEndFixedTime(parseOptionalTime(req.endFixedTime()));
        s.setEndNextDay(Boolean.TRUE.equals(req.endNextDay()));
        s.setBreakMode(req.breakMode() != null ? req.breakMode() : "ZERO");
        s.setCustomBreakMinutes(req.customBreakMinutes() != null ? req.customBreakMinutes() : 0);
        s.setPlannedMode(req.plannedMode() != null ? req.plannedMode() : "ZERO");
        s.setCustomPlannedHours(req.customPlannedHours() != null ? req.customPlannedHours() : 0);
        s.setReasonTemplate(blankToNull(req.reasonTemplate()));
        s.setSortOrder(req.sortOrder() != null ? req.sortOrder() : 100);
        validateConsistency(s);
    }

    @Transactional
    public void ensureDefaults(AppUser user) {
        // Сеем дефолтные сценарии только один раз. Если пользователь потом удалил
        // какой-то сценарий, не восстанавливаем его насильно.
        if (!scenarios.findByOwnerOrderBySortOrderAscIdAsc(user).isEmpty()) return;
        addDefaultIfMissing(user, "+2 часа", "после смены", "Начинает от конца смены и добавляет 2 часа сверх плана.",
                "SHIFT_END", "ADD_MINUTES", 120, null, false, "ZERO", 0, "ZERO", 0, "задержался после смены на 2 ч", 10);
        addDefaultIfMissing(user, "+4 часа", "после смены", "То же самое, но сразу на 4 часа задержки.",
                "SHIFT_END", "ADD_MINUTES", 240, null, false, "ZERO", 0, "ZERO", 0, "задержался после смены на 4 ч", 20);
        addDefaultIfMissing(user, "Остался в ночь", "частый сценарий", "Считает только время после смены до утра. План не вычитает.",
                "SHIFT_END", "FIXED_TIME", 0, "08:00", true, "ZERO", 0, "ZERO", 0, "остался в ночь", 30);
        addDefaultIfMissing(user, "Смена + ночь", "проверочный режим", "Вносит весь фактический интервал и вычитает план смены.",
                "SHIFT_START", "FIXED_TIME", 0, "08:00", true, "SHIFT", 0, "SHIFT", 0, "смена + ППР/ночь", 40);
        addDefaultIfMissing(user, "Обычная смена", "по настройкам", "Заполняет интервал, обед и план по выбранной смене.",
                "SHIFT_START", "SHIFT_END", 0, null, false, "SHIFT", 0, "SHIFT", 0, "обычная смена целиком", 50);
    }

    private void addDefaultIfMissing(AppUser user, String name, String group, String description,
                                     String startMode, String endMode, int endOffsetMinutes, String endFixedTime,
                                     boolean endNextDay, String breakMode, int customBreakMinutes,
                                     String plannedMode, double customPlannedHours, String reason, int sortOrder) {
        if (!scenarios.findByOwnerAndName(user, name).isEmpty()) return;
        QuickScenario s = new QuickScenario(user);
        s.setName(name);
        s.setGroupLabel(group);
        s.setDescription(description);
        s.setStartMode(startMode);
        s.setEndMode(endMode);
        s.setEndOffsetMinutes(endOffsetMinutes);
        s.setEndFixedTime(parseOptionalTime(endFixedTime));
        s.setEndNextDay(endNextDay);
        s.setBreakMode(breakMode);
        s.setCustomBreakMinutes(customBreakMinutes);
        s.setPlannedMode(plannedMode);
        s.setCustomPlannedHours(customPlannedHours);
        s.setReasonTemplate(reason);
        s.setSortOrder(sortOrder);
        scenarios.save(s);
    }

    private void validateConsistency(QuickScenario s) {
        if ("FIXED_TIME".equals(s.getEndMode()) && s.getEndFixedTime() == null) {
            throw ApiException.badRequest("Для FIXED_TIME нужно указать endFixedTime");
        }
    }

    private String cleanName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isBlank()) throw ApiException.badRequest("Название сценария не должно быть пустым");
        return name;
    }

    private String blankToNull(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        return s.isBlank() ? null : s;
    }

    private LocalTime parseOptionalTime(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return LocalTime.parse(raw.trim());
    }
}
