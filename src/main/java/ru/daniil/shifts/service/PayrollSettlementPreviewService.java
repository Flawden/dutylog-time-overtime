package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.PayrollSettlementPricingService.PayrollSettlementPricing;
import ru.daniil.shifts.service.PayrollSettlementPricingService.SettlementLine;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.YearMonth;
import java.util.List;
import java.util.Set;

/**
 * Soft Payroll-facing adapter around strict settlement pricing.
 *
 * Operational GET /payroll/periods/{month} must remain readable when old
 * overtime minutes are not automatically priceable yet.
 *
 * Expected configuration/provenance problems become an explicit blocked
 * preview state.
 *
 * Structural corruption, programming errors and money overflow still escape
 * normally: fail-closed does not mean hiding broken invariants.
 */
@Service
public class PayrollSettlementPreviewService {

    private static final Set<String> SOFT_BLOCKING_CODES =
            Set.of(
                    "PAY_PRICING_PROVENANCE_REQUIRED",
                    "PAY_PRICING_RULES_REQUIRED",
                    "PAYROLL_COMPENSATION_REQUIRED",
                    "PAYROLL_PRODUCTION_NORM_INCOMPLETE",
                    "PAYROLL_PRODUCTION_NORM_REQUIRED",
                    "PAY_PRICING_CURRENCY_MISMATCH",
                    "PAYROLL_SETTLEMENT_CURRENCY_MISMATCH"
            );

    private final PayrollSettlementPricingService pricing;

    public PayrollSettlementPreviewService(
            PayrollSettlementPricingService pricing
    ) {
        this.pricing = pricing;
    }

    @Transactional(readOnly = true)
    public SettlementPreview preview(
            AppUser user,
            YearMonth month,
            String payrollCurrency
    ) {
        if (user == null) {
            throw new IllegalArgumentException(
                    "Payroll settlement preview requires user"
            );
        }

        if (month == null) {
            throw new IllegalArgumentException(
                    "Payroll settlement preview requires month"
            );
        }

        if (payrollCurrency == null
                || payrollCurrency.isBlank()) {
            throw new IllegalArgumentException(
                    "Payroll settlement preview requires payroll currency"
            );
        }

        try {
            PayrollSettlementPricing resolved =
                    pricing.project(
                            user,
                            month
                    );

            if (!resolved.empty()
                    && !payrollCurrency.equals(
                            resolved.currencyCode()
                    )) {
                throw ApiException.conflict(
                        "PAYROLL_SETTLEMENT_CURRENCY_MISMATCH",
                        "Валюта settlement "
                                + resolved.currencyCode()
                                + " не совпадает с валютой Payroll "
                                + payrollCurrency
                );
            }

            return SettlementPreview.ready(
                    resolved
            );
        } catch (ApiException ex) {
            String code =
                    ex.getCode();

            if (code == null
                    || !SOFT_BLOCKING_CODES.contains(
                            code
                    )) {
                throw ex;
            }

            return SettlementPreview.blocked(
                    month,
                    code,
                    ex.getMessage()
            );
        }
    }

    public record SettlementPreview(
            YearMonth month,
            boolean ready,
            String blockingReason,
            String blockingMessage,
            int settlementCount,
            int minutes,
            long baseAmountMinor,
            long premiumAmountMinor,
            long totalAmountMinor,
            String pricingFingerprint,
            List<SettlementLine> settlements
    ) {
        public SettlementPreview {
            if (month == null
                    || settlementCount < 0
                    || minutes < 0
                    || baseAmountMinor < 0
                    || premiumAmountMinor < 0
                    || totalAmountMinor < 0) {
                throw new IllegalArgumentException(
                        "Invalid Payroll settlement preview"
                );
            }

            settlements =
                    settlements == null
                            ? List.of()
                            : List.copyOf(
                                    settlements
                            );

            if (ready) {
                if (blockingReason != null
                        || blockingMessage != null) {
                    throw new IllegalArgumentException(
                            "Ready settlement preview cannot contain blocker"
                    );
                }

                if (settlementCount
                        != settlements.size()) {
                    throw new IllegalArgumentException(
                            "Ready settlement preview count mismatch"
                    );
                }

                if (settlementCount == 0
                        && pricingFingerprint != null) {
                    throw new IllegalArgumentException(
                            "Empty ready settlement preview cannot contain fingerprint"
                    );
                }

                if (settlementCount > 0
                        && (pricingFingerprint == null
                        || !pricingFingerprint.matches(
                                "[0-9a-f]{64}"
                        ))) {
                    throw new IllegalArgumentException(
                            "Ready settlement preview requires pricing fingerprint"
                    );
                }

                try {
                    if (Math.addExact(
                            baseAmountMinor,
                            premiumAmountMinor
                    ) != totalAmountMinor) {
                        throw new IllegalArgumentException(
                                "Ready settlement preview money mismatch"
                        );
                    }
                } catch (ArithmeticException ex) {
                    throw new IllegalArgumentException(
                            "Ready settlement preview overflow",
                            ex
                    );
                }
            } else {
                if (blockingReason == null
                        || blockingReason.isBlank()) {
                    throw new IllegalArgumentException(
                            "Blocked settlement preview requires reason"
                    );
                }

                if (settlementCount != 0
                        || minutes != 0
                        || baseAmountMinor != 0
                        || premiumAmountMinor != 0
                        || totalAmountMinor != 0
                        || pricingFingerprint != null
                        || !settlements.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Blocked settlement preview cannot expose partial money"
                    );
                }
            }
        }

        public static SettlementPreview ready(
                PayrollSettlementPricing value
        ) {
            if (value == null) {
                throw new IllegalArgumentException(
                        "Settlement pricing is required"
                );
            }

            return new SettlementPreview(
                    value.month(),
                    true,
                    null,
                    null,
                    value.settlementCount(),
                    value.minutes(),
                    value.baseAmountMinor(),
                    value.premiumAmountMinor(),
                    value.totalAmountMinor(),
                    value.pricingFingerprint(),
                    value.settlements()
            );
        }

        public static SettlementPreview blocked(
                YearMonth month,
                String reason,
                String message
        ) {
            return new SettlementPreview(
                    month,
                    false,
                    reason,
                    message,
                    0,
                    0,
                    0L,
                    0L,
                    0L,
                    null,
                    List.of()
            );
        }
    }
}
