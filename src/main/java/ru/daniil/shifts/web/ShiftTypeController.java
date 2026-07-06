package ru.daniil.shifts.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ru.daniil.shifts.dto.Dtos.ShiftTypeCreateRequest;
import ru.daniil.shifts.dto.Dtos.ShiftTypeDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.repo.ShiftTypeRepository;
import ru.daniil.shifts.repo.UserRepository;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/shift-types")
public class ShiftTypeController {

    private static final String DEFAULT_COLOR = "#8B929E";

    private final ShiftTypeRepository shiftTypes;
    private final DayEntryRepository days;
    private final UserRepository users;

    public ShiftTypeController(ShiftTypeRepository shiftTypes, DayEntryRepository days, UserRepository users) {
        this.shiftTypes = shiftTypes;
        this.days = days;
        this.users = users;
    }

    private AppUser me(Principal principal) {
        return users.findByUsername(principal.getName()).orElseThrow();
    }

    @GetMapping
    @Transactional
    public List<ShiftTypeDto> list(Principal principal) {
        AppUser current = me(principal);
        ensureBuiltinShiftTypes(current);
        return shiftTypes.findByOwner(current).stream().map(ShiftTypeDto::from).toList();
    }

    /**
     * Подстраховка для старых пользователей из предыдущих версий проекта:
     * если у них ещё нет встроенного «Выходного», он появится при следующей загрузке.
     */
    private void ensureBuiltinShiftTypes(AppUser user) {
        ensureBuiltin(user, "Дневная", 8, "#F5B841");
        ensureBuiltin(user, "Ночная", 8, "#7B8CE0");
        ensureBuiltin(user, "Выходной", 0, "#6FBF73");
    }

    private void ensureBuiltin(AppUser user, String name, double hours, String color) {
        List<ShiftType> existing = shiftTypes.findByOwnerAndName(user, name);
        if (existing.isEmpty()) {
            shiftTypes.save(new ShiftType(user, name, hours, color, true));
            return;
        }

        ShiftType first = existing.get(0);
        if (!first.isBuiltin()) {
            first.setBuiltin(true);
            shiftTypes.save(first);
        }
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody(required = false) ShiftTypeCreateRequest req, Principal principal) {
        if (req == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Некорректный JSON в запросе"));
        }

        String name = req.name().trim();
        double hours = req.hours() != null ? req.hours() : 0;
        String color = req.color() != null ? req.color() : DEFAULT_COLOR;

        ShiftType saved = shiftTypes.save(new ShiftType(me(principal), name, hours, color, false));
        return ResponseEntity.status(HttpStatus.CREATED).body(ShiftTypeDto.from(saved));
    }

    /**
     * Удаление своего типа смены. Чужой удалить нельзя (404).
     * Встроенные типы не удаляются (409).
     * У дней, где был удаляемый тип, смена снимается; пустые записи удаляются.
     */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> delete(@PathVariable Long id, Principal principal) {
        AppUser current = me(principal);
        ShiftType st = shiftTypes.findById(id)
                .filter(type -> type.getOwner().getId().equals(current.getId()))
                .orElse(null);

        if (st == null) {
            return ResponseEntity.notFound().build();
        }
        if (st.isBuiltin()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Встроенную смену удалить нельзя"));
        }

        days.findByShiftType(st).forEach(entry -> {
            entry.setShiftType(null);
            if (entry.isEmpty()) {
                days.delete(entry);
            } else {
                days.save(entry);
            }
        });
        shiftTypes.delete(st);
        return ResponseEntity.noContent().build();
    }
}
