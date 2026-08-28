package ru.daniil.shifts.service;

import ru.daniil.shifts.model.PayrollSnapshotBonusAverageEarningsFact;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Canonical SHA-256 integrity identity for one immutable snapshot F1 fact set. */
public final class PayrollBonusAverageEarningsFingerprint {

    private PayrollBonusAverageEarningsFingerprint() {
    }

    public static String calculate(
            int sourceFactCount,
            List<PayrollSnapshotBonusAverageEarningsFact> facts
    ) {
        if (sourceFactCount < 0) {
            throw new IllegalArgumentException(
                    "Snapshot bonus source fact count must be non-negative"
            );
        }

        Objects.requireNonNull(
                facts,
                "Snapshot bonus average-earnings facts are required"
        );

        if (facts.size() > sourceFactCount) {
            throw new IllegalArgumentException(
                    "Snapshot bonus average-earnings facts exceed source fact count"
            );
        }

        StringBuilder canonical = new StringBuilder();
        token(canonical, "PAYROLL_BONUS_AVERAGE_EARNINGS_V1");
        token(canonical, sourceFactCount);
        token(canonical, facts.size());

        for (int index = 0; index < facts.size(); index++) {
            PayrollSnapshotBonusAverageEarningsFact fact =
                    Objects.requireNonNull(
                            facts.get(index),
                            "Snapshot bonus average-earnings fact cannot be null"
                    );

            if (fact.getFactIndex() != index) {
                throw new IllegalArgumentException(
                        "Snapshot bonus average-earnings facts must be contiguous and ordered"
                );
            }

            token(canonical, fact.getFactIndex());
            token(canonical, fact.getBonusSourceFactId());
            token(canonical, fact.getBonusAverageFactId());
            token(canonical, fact.getComponentId());
            token(canonical, fact.getEarningKind());
            token(canonical, fact.getSourcePeriodFrom());
            token(canonical, fact.getSourcePeriodTo());
            token(canonical, fact.getAmountMinor());
            token(canonical, fact.getCurrencyCode());
            token(canonical, fact.getIndicatorKey());
            token(canonical, fact.getAwardPeriodFrom());
            token(canonical, fact.getAwardPeriodTo());
            token(canonical, fact.getAnnualResult());
            token(canonical, fact.getAccruedForActualWorkTime());
            token(canonical, fact.getProratedForPartialAwardPeriod());
        }

        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(canonical.toString().getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static void token(StringBuilder target, Object value) {
        String text = value == null
                ? "<NULL>"
                : value instanceof LocalDate date
                ? date.toString()
                : String.valueOf(value);

        target.append(text.length()).append(':').append(text).append('|');
    }
}
