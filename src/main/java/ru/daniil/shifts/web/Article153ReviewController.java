package ru.daniil.shifts.web;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.*;
import java.security.Principal;

@RestController
@RequestMapping({"/api/payroll/periods/{month}/holiday-review","/api/v1/payroll/periods/{month}/holiday-review"})
public class Article153ReviewController {
    private final CurrentUserService users;
    private final ModuleService modules;
    private final Article153ReviewWorkspaceService workspace;
    public Article153ReviewController(CurrentUserService users,ModuleService modules,Article153ReviewWorkspaceService workspace) {
        this.users=users;this.modules=modules;this.workspace=workspace;
    }
    @GetMapping
    public ResponseEntity<Article153ReviewWorkspaceService.Workspace> read(@PathVariable("month") String month,Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(workspace.read(owner(principal),month));
    }
    @PutMapping
    public ResponseEntity<Article153ReviewWorkspaceService.Workspace> save(@PathVariable("month") String month,
            @RequestBody String json,Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(workspace.save(owner(principal),month,decode(json)));
    }
    static Article153ReviewWorkspaceService.Submission decode(String json) {
        try {
            return com.fasterxml.jackson.databind.json.JsonMapper.builder()
                .disable(com.fasterxml.jackson.databind.MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .disable(com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .build().readValue(json,Article153ReviewWorkspaceService.Submission.class);
        } catch (java.io.IOException | IllegalArgumentException e) {
            throw ru.daniil.shifts.service.exception.ApiException.badRequest("ARTICLE153_REVIEW_INPUT","Некорректные основания или денежные правила");
        }
    }
    private AppUser owner(Principal principal) {
        var owner=users.requireUser(principal);modules.requireEnabled(owner,ModuleService.PAYROLL);return owner;
    }
}
