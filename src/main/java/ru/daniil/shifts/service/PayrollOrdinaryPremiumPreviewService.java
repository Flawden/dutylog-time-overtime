package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.OrdinaryWorkPremiumPricingService.BlockingDay;
import ru.daniil.shifts.service.OrdinaryWorkPremiumPricingService.MonthPremiumProjection;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.YearMonth;
import java.util.List;
import java.util.Set;

/**
 * Soft Payroll-facing adapter around strict ordinary-work premium pricing.
 *
 * GET /payroll/periods/{month} must remain readable when NIGHT / HOLIDAY
 * premium money cannot yet be derived from historical configuration or clock
 * truth.
 *
 * Expected configuration/source problems become an explicit blocked preview.
 * Structural corruption, programming errors and arithmetic overflow still
 * escape normally.
 */
@Service
public class PayrollOrdinaryPremiumPreviewService {

    public static final String PAYROLL_CURRENCY_MISMATCH =
            "PAYROLL_ORDINARY_PREMIUM_CURRENCY_MISMATCH";

    private static final Set<String> SOFT_BLOCKING_CODES =
            Set.of(
                    "PAY_PRICING_RULES_REQUIRED",
                    "PAYROLL_COMPENSATION_REQUIRED",
                    "PAYROLL_PRODUCTION_NORM_INCOMPLETE",
                    "PAYROLL_PRODUCTION_NORM_REQUIRED",
                    "PAY_PRICING_CURRENCY_MISMATCH",
                    PAYROLL_CURRENCY_MISMATCH
            );

    private final OrdinaryWorkPremiumPricingService pricing;

    public PayrollOrdinaryPremiumPreviewService(
            OrdinaryWorkPremiumPricingService pricing
    ) {
        this.pricing = pricing;
    }

    @Transactional(readOnly = true)
    public OrdinaryPremiumPreview preview(
            AppUser user,
            YearMonth month,
            String payrollCurrency
    ) {
        if (user == null) {
            throw new IllegalArgumentException(
                    "Payroll ordinary premium preview requires user"
            );
        }

        if (month == null) {
            throw new IllegalArgumentException(
                    "Payroll ordinary premium preview requires month"
            );
        }

        if (payrollCurrency == null
                || payrollCurrency.isBlank()) {
            throw new IllegalArgumentException(
                    "Payroll ordinary premium preview requires payroll currency"
            );
        }

        try {
            MonthPremiumProjection resolved =
                    pricing.priceMonth(
                            user,
                            month
                    );

            if (!resolved.ready()) {
                return OrdinaryPremiumPreview.blocked(
                        month,
                        resolved.blockingReason(),
                        "Не удалось однозначно определить источник обычных NIGHT / HOLIDAY доплат",
                        resolved.ordinaryMinutes(),
                        resolved.blockers()
                );
            }

            if (resolved.ordinaryMinutes() > 0
                    && !payrollCurrency.equals(
                            resolved.currencyCode()
                    )) {
                throw ApiException.conflict(
                        PAYROLL_CURRENCY_MISMATCH,
                        "Валюта обычных NIGHT / HOLIDAY доплат "
                                + resolved.currencyCode()
                                + " не совпадает с валютой Payroll "
                                + payrollCurrency
                );
            }

            return OrdinaryPremiumPreview.ready(
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

            return OrdinaryPremiumPreview.blocked(
                    month,
                    code,
                    ex.getMessage(),
                    0,
                    List.of()
            );
        }
    }

    public record OrdinaryPremiumPreview(
            YearMonth month,
            boolean ready,
            String blockingReason,
            String blockingMessage,
            int ordinaryMinutes,
            long referenceBaseAmountMinor,
            long premiumAmountMinor,
            boolean pricingIdentityRequired,
            String pricingFingerprint,
            List<BlockingDay> blockers
    ) {
        public OrdinaryPremiumPreview {
            if (month == null
                    || ordinaryMinutes < 0
                    || referenceBaseAmountMinor < 0
                    || premiumAmountMinor < 0) {
                throw new IllegalArgumentException(
                        "Invalid Payroll ordinary premium preview"
                );
            }

            blockers =
                    blockers == null
                            ? List.of()
                            : List.copyOf(
                                    blockers
                            );

            if (ready) {
                if (blockingReason != null
                        || blockingMessage != null
                        || !blockers.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Ready ordinary premium preview cannot contain blocker"
                    );
                }

                if (pricingIdentityRequired
                        != (pricingFingerprint != null)) {
                    throw new IllegalArgumentException(
                            "Ordinary premium pricing identity and fingerprint disagree"
                    );
                }

                if (pricingFingerprint != null
                        && !pricingFingerprint.matches(
                                "[0-9a-f]{64}"
                        )) {
                    throw new IllegalArgumentException(
                            "Ordinary premium pricing fingerprint is invalid"
                    );
                }
            } else {
                if (blockingReason == null
                        || blockingReason.isBlank()
                        || blockingMessage == null
                        || blockingMessage.isBlank()
                        || referenceBaseAmountMinor != 0
                        || premiumAmountMinor != 0
                        || pricingIdentityRequired
                        || pricingFingerprint != null) {
                    throw new IllegalArgumentException(
                            "Blocked ordinary premium preview cannot contain speculative money"
                    );
                }
            }
        }

        static OrdinaryPremiumPreview ready(
                MonthPremiumProjection source
        ) {
            String fingerprint =
                    source.pricingFingerprint();

            boolean pricingIdentityRequired =
                    fingerprint != null;

            return new OrdinaryPremiumPreview(
                    source.payrollMonth(),
                    true,
                    null,
                    null,
                    source.ordinaryMinutes(),
                    source.referenceBaseAmountMinor(),
                    source.premiumAmountMinor(),
                    pricingIdentityRequired,
                    fingerprint,
                    List.of()
            );
        }

        static OrdinaryPremiumPreview blocked(
                YearMonth month,
                String reason,
                String message,
                int ordinaryMinutes,
                List<BlockingDay> blockers
        ) {
            return new OrdinaryPremiumPreview(
                    month,
                    false,
                    reason,
                    message,
                    ordinaryMinutes,
                    0L,
                    0L,
                    false,
                    null,
                    blockers
            );
        }
    }
}
