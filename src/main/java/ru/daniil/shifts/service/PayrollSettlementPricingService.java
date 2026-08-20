package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.OvertimeSettlement;
import ru.daniil.shifts.repo.OvertimeSettlementRepository;
import ru.daniil.shifts.service.OvertimeSettlementPricingService.SettlementMoneyProjection;
import ru.daniil.shifts.service.exception.ApiException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * Payroll-month read model for explicit overtime cash settlements.
 *
 * Membership is defined only by OvertimeSettlement.settlementDate.
 *
 * Each settlement remains its own monetary transaction and its own rounding
 * boundary. This service sums already-priced settlement projections; it never
 * merges raw provenance/pricing slices across settlements.
 *
 * Nothing is persisted here.
 */
@Service
public class PayrollSettlementPricingService {

    private final OvertimeSettlementRepository settlements;
    private final OvertimeSettlementPricingService pricing;

    public PayrollSettlementPricingService(
            OvertimeSettlementRepository settlements,
            OvertimeSettlementPricingService pricing
    ) {
        this.settlements = settlements;
        this.pricing = pricing;
    }

    @Transactional(readOnly = true)
    public PayrollSettlementPricing project(
            AppUser user,
            YearMonth payrollMonth
    ) {
        if (user == null) {
            throw new IllegalArgumentException(
                    "Payroll settlement pricing requires user"
            );
        }

        if (payrollMonth == null) {
            throw new IllegalArgumentException(
                    "Payroll settlement pricing requires month"
            );
        }

        LocalDate from =
                payrollMonth.atDay(1);

        LocalDate to =
                payrollMonth.atEndOfMonth();

        List<OvertimeSettlement> monthSettlements =
                settlements
                        .findByOwnerAndSettlementDateBetweenOrderBySettlementDateAscIdAsc(
                                user,
                                from,
                                to
                        );

        if (monthSettlements.isEmpty()) {
            return new PayrollSettlementPricing(
                    payrollMonth,
                    null,
                    0,
                    0,
                    0L,
                    0L,
                    0L,
                    List.of()
            );
        }

        String currency = null;
        int totalMinutes = 0;
        long baseAmount = 0L;
        long premiumAmount = 0L;
        long totalAmount = 0L;

        List<SettlementLine> lines =
                new ArrayList<>();

        for (OvertimeSettlement settlement :
                monthSettlements) {

            if (settlement == null
                    || settlement.getId() == null
                    || settlement.getSettlementDate() == null) {
                throw new IllegalStateException(
                        "Payroll settlement query returned incomplete identity"
                );
            }

            if (settlement.getSettlementDate()
                    .isBefore(from)
                    || settlement.getSettlementDate()
                    .isAfter(to)) {
                throw new IllegalStateException(
                        "Payroll settlement query returned date outside requested month"
                );
            }

            SettlementMoneyProjection priced =
                    pricing.price(
                            user,
                            settlement.getId()
                    );

            if (!settlement.getId()
                    .equals(
                            priced.settlementId()
                    )) {
                throw new IllegalStateException(
                        "Settlement pricing returned another settlement identity"
                );
            }

            if (!settlement.getSettlementDate()
                    .equals(
                            priced.settlementDate()
                    )) {
                throw new IllegalStateException(
                        "Settlement pricing returned another settlement date"
                );
            }

            if (settlement.getRequestedMinutes()
                    != priced.minutes()) {
                throw new IllegalStateException(
                        "Settlement pricing changed requested minute total"
                );
            }

            if (currency == null) {
                currency =
                        priced.currencyCode();
            } else if (!currency.equals(
                    priced.currencyCode()
            )) {
                throw ApiException.conflict(
                        "PAYROLL_SETTLEMENT_CURRENCY_MISMATCH",
                        "В одном расчётном месяце settlement использует разные валюты: "
                                + currency
                                + " и "
                                + priced.currencyCode()
                );
            }

            try {
                totalMinutes =
                        Math.addExact(
                                totalMinutes,
                                priced.minutes()
                        );

                baseAmount =
                        Math.addExact(
                                baseAmount,
                                priced.baseAmountMinor()
                        );

                premiumAmount =
                        Math.addExact(
                                premiumAmount,
                                priced.premiumAmountMinor()
                        );

                totalAmount =
                        Math.addExact(
                                totalAmount,
                                priced.totalAmountMinor()
                        );
            } catch (ArithmeticException ex) {
                throw ApiException.badRequest(
                        "PAYROLL_AMOUNT_OVERFLOW",
                        "Сумма settlement за расчётный месяц слишком велика"
                );
            }

            lines.add(
                    new SettlementLine(
                            priced.settlementId(),
                            priced.settlementDate(),
                            priced.currencyCode(),
                            priced.minutes(),
                            priced.baseAmountMinor(),
                            priced.premiumAmountMinor(),
                            priced.totalAmountMinor(),
                            priced.pricingFingerprint()
                    )
            );
        }

        if (currency == null) {
            throw new IllegalStateException(
                    "Payroll settlement currency was not resolved"
            );
        }

        try {
            if (Math.addExact(
                    baseAmount,
                    premiumAmount
            ) != totalAmount) {
                throw new IllegalStateException(
                        "Payroll settlement money components do not sum"
                );
            }
        } catch (ArithmeticException ex) {
            throw ApiException.badRequest(
                    "PAYROLL_AMOUNT_OVERFLOW",
                    "Сумма settlement за расчётный месяц слишком велика"
            );
        }

        return new PayrollSettlementPricing(
                payrollMonth,
                currency,
                lines.size(),
                totalMinutes,
                baseAmount,
                premiumAmount,
                totalAmount,
                List.copyOf(lines)
        );
    }


    private static String shallowLineFingerprint(
            Long settlementId,
            LocalDate settlementDate,
            String currencyCode,
            int minutes,
            long baseAmountMinor,
            long premiumAmountMinor,
            long totalAmountMinor
    ) {
        StringBuilder canonical =
                new StringBuilder();

        token(
                canonical,
                "DUTYLOG_SETTLEMENT_LINE_COMPAT_V1"
        );
        token(
                canonical,
                settlementId
        );
        token(
                canonical,
                settlementDate
        );
        token(
                canonical,
                currencyCode
        );
        token(
                canonical,
                minutes
        );
        token(
                canonical,
                baseAmountMinor
        );
        token(
                canonical,
                premiumAmountMinor
        );
        token(
                canonical,
                totalAmountMinor
        );

        return sha256(
                canonical.toString()
        );
    }

    private static String monthlyFingerprint(
            List<SettlementLine> source
    ) {
        if (source == null
                || source.isEmpty()) {
            return null;
        }

        List<SettlementLine> ordered =
                new ArrayList<>(
                        source
                );

        ordered.sort(
                Comparator
                        .comparing(
                                SettlementLine::settlementDate
                        )
                        .thenComparing(
                                SettlementLine::settlementId
                        )
        );

        StringBuilder canonical =
                new StringBuilder();

        token(
                canonical,
                "DUTYLOG_PAYROLL_SETTLEMENT_MONTH_V1"
        );

        token(
                canonical,
                ordered.size()
        );

        for (SettlementLine line :
                ordered) {

            token(
                    canonical,
                    line.settlementId()
            );
            token(
                    canonical,
                    line.settlementDate()
            );
            token(
                    canonical,
                    line.currencyCode()
            );
            token(
                    canonical,
                    line.minutes()
            );
            token(
                    canonical,
                    line.baseAmountMinor()
            );
            token(
                    canonical,
                    line.premiumAmountMinor()
            );
            token(
                    canonical,
                    line.totalAmountMinor()
            );
            token(
                    canonical,
                    line.pricingFingerprint()
            );
        }

        return sha256(
                canonical.toString()
        );
    }

    private static void token(
            StringBuilder target,
            Object value
    ) {
        if (value == null) {
            target.append(
                    "-1:|"
            );
            return;
        }

        String text =
                String.valueOf(
                        value
                );

        target
                .append(
                        text.length()
                )
                .append(
                        ':'
                )
                .append(
                        text
                )
                .append(
                        '|'
                );
    }

    private static String sha256(
            String value
    ) {
        try {
            return HexFormat
                    .of()
                    .formatHex(
                            MessageDigest
                                    .getInstance(
                                            "SHA-256"
                                    )
                                    .digest(
                                            value.getBytes(
                                                    StandardCharsets.UTF_8
                                            )
                                    )
                    );
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    ex
            );
        }
    }

    public record SettlementLine(
            Long settlementId,
            LocalDate settlementDate,
            String currencyCode,
            int minutes,
            long baseAmountMinor,
            long premiumAmountMinor,
            long totalAmountMinor,
            String pricingFingerprint
    ) {
        /**
         * Source-compatible constructor for unit/read-model callers that
         * predate deep pricing fingerprints.
         *
         * Production lines always receive the deep fingerprint from
         * SettlementMoneyProjection.
         */
        public SettlementLine(
                Long settlementId,
                LocalDate settlementDate,
                String currencyCode,
                int minutes,
                long baseAmountMinor,
                long premiumAmountMinor,
                long totalAmountMinor
        ) {
            this(
                    settlementId,
                    settlementDate,
                    currencyCode,
                    minutes,
                    baseAmountMinor,
                    premiumAmountMinor,
                    totalAmountMinor,
                    shallowLineFingerprint(
                            settlementId,
                            settlementDate,
                            currencyCode,
                            minutes,
                            baseAmountMinor,
                            premiumAmountMinor,
                            totalAmountMinor
                    )
            );
        }

        public SettlementLine {
            if (settlementId == null
                    || settlementDate == null
                    || currencyCode == null
                    || currencyCode.isBlank()
                    || minutes <= 0
                    || baseAmountMinor < 0
                    || premiumAmountMinor < 0
                    || totalAmountMinor < 0
                    || pricingFingerprint == null
                    || !pricingFingerprint.matches(
                            "[0-9a-f]{64}"
                    )) {
                throw new IllegalArgumentException(
                        "Invalid payroll settlement line"
                );
            }

            try {
                if (Math.addExact(
                        baseAmountMinor,
                        premiumAmountMinor
                ) != totalAmountMinor) {
                    throw new IllegalArgumentException(
                            "Settlement line money components do not sum"
                    );
                }
            } catch (ArithmeticException ex) {
                throw new IllegalArgumentException(
                        "Settlement line money overflow",
                        ex
                );
            }
        }
    }

    public record PayrollSettlementPricing(
            YearMonth month,
            String currencyCode,
            int settlementCount,
            int minutes,
            long baseAmountMinor,
            long premiumAmountMinor,
            long totalAmountMinor,
            List<SettlementLine> settlements
    ) {
        public PayrollSettlementPricing {
            if (month == null
                    || settlementCount < 0
                    || minutes < 0
                    || baseAmountMinor < 0
                    || premiumAmountMinor < 0
                    || totalAmountMinor < 0) {
                throw new IllegalArgumentException(
                        "Invalid payroll settlement pricing projection"
                );
            }

            settlements =
                    settlements == null
                            ? List.of()
                            : List.copyOf(
                                    settlements
                            );

            if (settlementCount
                    != settlements.size()) {
                throw new IllegalArgumentException(
                        "Settlement count does not match settlement lines"
                );
            }

            if (settlementCount == 0) {
                if (currencyCode != null
                        || minutes != 0
                        || baseAmountMinor != 0
                        || premiumAmountMinor != 0
                        || totalAmountMinor != 0) {
                    throw new IllegalArgumentException(
                            "Empty payroll settlement projection must contain zero money"
                    );
                }
            }

            if (settlementCount > 0
                    && (currencyCode == null
                    || currencyCode.isBlank()
                    || minutes <= 0)) {
                throw new IllegalArgumentException(
                        "Non-empty payroll settlement projection requires currency and minutes"
                );
            }

            int lineMinutes =
                    settlements.stream()
                            .mapToInt(
                                    SettlementLine::minutes
                            )
                            .sum();

            long lineBase = 0L;
            long linePremium = 0L;
            long lineTotal = 0L;

            try {
                for (SettlementLine line :
                        settlements) {
                    if (!currencyCode.equals(
                            line.currencyCode()
                    )) {
                        throw new IllegalArgumentException(
                                "Settlement line currency disagrees with payroll projection"
                        );
                    }

                    lineBase =
                            Math.addExact(
                                    lineBase,
                                    line.baseAmountMinor()
                            );

                    linePremium =
                            Math.addExact(
                                    linePremium,
                                    line.premiumAmountMinor()
                            );

                    lineTotal =
                            Math.addExact(
                                    lineTotal,
                                    line.totalAmountMinor()
                            );
                }
            } catch (ArithmeticException ex) {
                throw new IllegalArgumentException(
                        "Payroll settlement projection overflow",
                        ex
                );
            }

            if (lineMinutes != minutes
                    || lineBase != baseAmountMinor
                    || linePremium != premiumAmountMinor
                    || lineTotal != totalAmountMinor) {
                throw new IllegalArgumentException(
                        "Payroll settlement projection totals disagree with settlement lines"
                );
            }
        }

        /**
         * Fingerprint of all already-priced settlements in this Payroll month.
         * Null means there are no explicit settlement transactions.
         */
        public String pricingFingerprint() {
            return monthlyFingerprint(
                    settlements
            );
        }

        public boolean empty() {
            return settlementCount == 0;
        }
    }
}
