package ru.daniil.shifts.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.daniil.shifts.dto.Dtos.ShiftTypeCreateRequest;
import ru.daniil.shifts.dto.Dtos.ShiftTypeDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.ShiftTypeService;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/shift-types")
public class ShiftTypeController {

    private final CurrentUserService currentUserService;
    private final ShiftTypeService shiftTypeService;

    public ShiftTypeController(CurrentUserService currentUserService, ShiftTypeService shiftTypeService) {
        this.currentUserService = currentUserService;
        this.shiftTypeService = shiftTypeService;
    }

    @GetMapping
    public List<ShiftTypeDto> list(Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        return shiftTypeService.list(current);
    }

    @PostMapping
    public ResponseEntity<ShiftTypeDto> create(@Valid @RequestBody(required = false) ShiftTypeCreateRequest req,
                                               Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(shiftTypeService.create(current, req));
    }

    /**
     * Удаление своего типа смены. Чужой удалить нельзя (404).
     * Встроенные типы не удаляются (409).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        shiftTypeService.delete(current, id);
        return ResponseEntity.noContent().build();
    }
}
