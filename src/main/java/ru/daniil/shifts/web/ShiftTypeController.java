package ru.daniil.shifts.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ru.daniil.shifts.dto.Dtos.ShiftTypeCreateRequest;
import ru.daniil.shifts.dto.Dtos.ShiftTypeDto;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.repo.ShiftTypeRepository;

import java.util.List;

@RestController
@RequestMapping("/api/shift-types")
public class ShiftTypeController {

    private final ShiftTypeRepository shiftTypes;
    private final DayEntryRepository days;

    public ShiftTypeController(ShiftTypeRepository shiftTypes, DayEntryRepository days) {
        this.shiftTypes = shiftTypes;
        this.days = days;
    }

    @GetMapping
    public List<ShiftTypeDto> list() {
        return shiftTypes.findAll().stream().map(ShiftTypeDto::from).toList();
    }

    @PostMapping
    public ResponseEntity<ShiftTypeDto> create(@RequestBody ShiftTypeCreateRequest req) {
        if (req.name() == null || req.name().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String color = (req.color() != null && req.color().matches("#[0-9a-fA-F]{6}"))
                ? req.color() : "#8B929E";
        double hours = req.hours() != null ? req.hours() : 0;

        ShiftType saved = shiftTypes.save(new ShiftType(req.name().trim(), hours, color, false));
        return ResponseEntity.status(HttpStatus.CREATED).body(ShiftTypeDto.from(saved));
    }

    /**
     * Удаление типа смены. У дней, где он был назначен, смена снимается;
     * пустые записи (без заметки) удаляются целиком.
     */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return shiftTypes.findById(id).map(st -> {
            days.findByShiftType(st).forEach(entry -> {
                entry.setShiftType(null);
                if (entry.isEmpty()) {
                    days.delete(entry);
                } else {
                    days.save(entry);
                }
            });
            shiftTypes.delete(st);
            return ResponseEntity.noContent().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }
}
