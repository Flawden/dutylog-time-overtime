package ru.daniil.shifts.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.daniil.shifts.dto.Dtos.QuickScenarioCreateRequest;
import ru.daniil.shifts.dto.Dtos.QuickScenarioDto;
import ru.daniil.shifts.dto.Dtos.QuickScenarioUpdateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.ModuleService;
import ru.daniil.shifts.service.QuickScenarioService;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/quick-scenarios")
public class QuickScenarioController {
    private final CurrentUserService currentUserService;
    private final ModuleService moduleService;
    private final QuickScenarioService quickScenarioService;

    public QuickScenarioController(CurrentUserService currentUserService,
                          ModuleService moduleService, QuickScenarioService quickScenarioService) {
        this.currentUserService = currentUserService;
        this.moduleService = moduleService;
        this.quickScenarioService = quickScenarioService;
    }

    @GetMapping
    public List<QuickScenarioDto> list(Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        moduleService.requireEnabled(current, ModuleService.SCENARIOS);
        return quickScenarioService.list(current);
    }

    @PostMapping
    public ResponseEntity<QuickScenarioDto> create(@Valid @RequestBody(required = false) QuickScenarioCreateRequest req,
                                                   Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        moduleService.requireEnabled(current, ModuleService.SCENARIOS);
        return ResponseEntity.status(HttpStatus.CREATED).body(quickScenarioService.create(current, req));
    }

    @PatchMapping("/{id}")
    public QuickScenarioDto update(@PathVariable("id") Long id,
                                   @Valid @RequestBody(required = false) QuickScenarioUpdateRequest req,
                                   Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        moduleService.requireEnabled(current, ModuleService.SCENARIOS);
        return quickScenarioService.update(current, id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id, Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        moduleService.requireEnabled(current, ModuleService.SCENARIOS);
        quickScenarioService.delete(current, id);
        return ResponseEntity.noContent().build();
    }
}
