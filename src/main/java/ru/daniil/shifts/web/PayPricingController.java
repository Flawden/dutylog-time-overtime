package ru.daniil.shifts.web;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.daniil.shifts.dto.Dtos.PayPricingTermDto;
import ru.daniil.shifts.dto.Dtos.PayPricingTermRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.ModuleService;
import ru.daniil.shifts.service.PayPricingConfigurationService;

import java.security.Principal;
import java.util.List;

/** Native effective-dated Pay Pricing configuration API. */
@RestController
@RequestMapping({
        "/api/payroll/pricing/terms",
        "/api/v1/payroll/pricing/terms"
})
public class PayPricingController {

    private final CurrentUserService users;
    private final ModuleService modules;
    private final PayPricingConfigurationService pricing;

    public PayPricingController(
            CurrentUserService users,
            ModuleService modules,
            PayPricingConfigurationService pricing
    ) {
        this.users = users;
        this.modules = modules;
        this.pricing = pricing;
    }

    @GetMapping
    public ResponseEntity<List<PayPricingTermDto>> history(
            Principal principal
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(
                        pricing.history(
                                user(principal)
                        )
                );
    }

    @PutMapping("/{effectiveFrom}")
    public PayPricingTermDto upsert(
            @PathVariable("effectiveFrom") String effectiveFrom,
            @Valid @RequestBody(required = false) PayPricingTermRequest request,
            Principal principal
    ) {
        return pricing.upsert(
                user(principal),
                effectiveFrom,
                request
        );
    }

    @DeleteMapping("/{effectiveFrom}")
    public ResponseEntity<Void> delete(
            @PathVariable("effectiveFrom") String effectiveFrom,
            Principal principal
    ) {
        pricing.delete(
                user(principal),
                effectiveFrom
        );

        return ResponseEntity.noContent().build();
    }

    private AppUser user(
            Principal principal
    ) {
        AppUser user = users.requireUser(principal);
        modules.requireEnabled(
                user,
                ModuleService.PAYROLL
        );
        return user;
    }
}
