package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.PayPricingRuleDto;
import ru.daniil.shifts.dto.Dtos.PayPricingRuleRequest;
import ru.daniil.shifts.dto.Dtos.PayPricingTermDto;
import ru.daniil.shifts.dto.Dtos.PayPricingTermRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayPricingRule;
import ru.daniil.shifts.model.PayPricingTerm;
import ru.daniil.shifts.repo.PayPricingTermRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/** User-facing effective-dated Pay Pricing configuration boundary. */
@Service
public class PayPricingConfigurationService {

    private final PayPricingTermRepository terms;

    public PayPricingConfigurationService(
            PayPricingTermRepository terms
    ) {
        this.terms = terms;
    }

    @Transactional(readOnly = true)
    public List<PayPricingTermDto> history(
            AppUser user
    ) {
        requireUser(user);

        return terms
                .findByOwnerOrderByEffectiveFromDescIdDesc(user)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public PayPricingTermDto upsert(
            AppUser user,
            String effectiveFromText,
            PayPricingTermRequest request
    ) {
        requireUser(user);

        if (request == null) {
            throw ApiException.badRequest(
                    "PAY_PRICING_INVALID",
                    "Некорректный JSON правил оплаты"
            );
        }

        LocalDate effectiveFrom =
                parseEffectiveDate(effectiveFromText);

        if (request.rules() == null) {
            throw ApiException.badRequest(
                    "PAY_PRICING_INVALID",
                    "Список правил оплаты обязателен"
            );
        }

        List<PayPricingRuleRequest> requestedRules =
                request.rules();

        validateRules(user, effectiveFrom, requestedRules);

        PayPricingTerm term =
                terms
                        .findByOwnerAndEffectiveFrom(
                                user,
                                effectiveFrom
                        )
                        .orElseGet(() ->
                                new PayPricingTerm(
                                        user,
                                        effectiveFrom
                                )
                        );

        if (term.getId() != null) {
            term.clearRules();
            terms.saveAndFlush(term);
        }

        for (PayPricingRuleRequest rule : requestedRules) {
            addRule(term, rule);
        }

        return toDto(
                terms.saveAndFlush(term)
        );
    }

    @Transactional
    public void delete(
            AppUser user,
            String effectiveFromText
    ) {
        requireUser(user);

        LocalDate effectiveFrom =
                parseEffectiveDate(effectiveFromText);

        terms.findByOwnerAndEffectiveFrom(
                        user,
                        effectiveFrom
                )
                .ifPresent(terms::delete);

        terms.flush();
    }

    private void validateRules(
            AppUser user,
            LocalDate effectiveFrom,
            List<PayPricingRuleRequest> rules
    ) {
        PayPricingTerm probe =
                new PayPricingTerm(
                        user,
                        effectiveFrom
                );

        try {
            for (PayPricingRuleRequest rule : rules) {
                addRule(probe, rule);
            }
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(
                    "PAY_PRICING_INVALID",
                    ex.getMessage()
            );
        }
    }

    private void addRule(
            PayPricingTerm term,
            PayPricingRuleRequest rule
    ) {
        if (rule == null) {
            throw ApiException.badRequest(
                    "PAY_PRICING_INVALID",
                    "Правило оплаты не может быть null"
            );
        }

        try {
            term.addRule(
                    rule.code(),
                    rule.dimension(),
                    rule.premiumBps() == null
                            ? -1
                            : rule.premiumBps(),
                    rule.fromMinute() == null
                            ? -1
                            : rule.fromMinute(),
                    rule.toMinuteExclusive(),
                    rule.exclusiveGroup()
            );
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(
                    "PAY_PRICING_INVALID",
                    ex.getMessage()
            );
        }
    }

    private LocalDate parseEffectiveDate(
            String raw
    ) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest(
                    "PAY_PRICING_INVALID",
                    "Нужно указать дату вступления правил оплаты"
            );
        }

        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            throw ApiException.badRequest(
                    "PAY_PRICING_INVALID",
                    "Дата вступления должна быть в формате yyyy-MM-dd"
            );
        }
    }

    private PayPricingTermDto toDto(
            PayPricingTerm term
    ) {
        return new PayPricingTermDto(
                term.getId(),
                term.getEffectiveFrom().toString(),
                term.getRules()
                        .stream()
                        .map(this::toDto)
                        .toList(),
                term.getUpdatedAt().toString()
        );
    }

    private PayPricingRuleDto toDto(
            PayPricingRule rule
    ) {
        return new PayPricingRuleDto(
                rule.getCode(),
                rule.getDimension(),
                rule.getPremiumBps(),
                rule.getFromMinute(),
                rule.getToMinuteExclusive(),
                rule.getExclusiveGroup()
        );
    }

    private void requireUser(AppUser user) {
        if (user == null) {
            throw new IllegalArgumentException(
                    "Pricing configuration requires user"
            );
        }
    }
}
