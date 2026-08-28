package ru.daniil.shifts.service;

import ru.daniil.shifts.model.PayrollSnapshotBonusP15NatureFact;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Canonical SHA-256 integrity identity for one immutable snapshot F3A nature set. */
public final class PayrollBonusP15NatureFingerprint {

    private PayrollBonusP15NatureFingerprint() {
    }

    public static String calculate(int averageFactCount, List<PayrollSnapshotBonusP15NatureFact> facts) {
        if (averageFactCount < 0) throw new IllegalArgumentException("Average fact count must be non-negative");
        Objects.requireNonNull(facts, "Snapshot P15 nature facts are required");
        if (facts.size() > averageFactCount) throw new IllegalArgumentException("P15 nature facts exceed average fact count");

        StringBuilder canonical = new StringBuilder();
        token(canonical, "PAYROLL_BONUS_P15_NATURE_V1");
        token(canonical, averageFactCount);
        token(canonical, facts.size());

        for (int index = 0; index < facts.size(); index++) {
            PayrollSnapshotBonusP15NatureFact fact = Objects.requireNonNull(facts.get(index), "Snapshot P15 nature fact cannot be null");
            if (fact.getFactIndex() != index) throw new IllegalArgumentException("Snapshot P15 nature facts must be contiguous and ordered");
            token(canonical, fact.getFactIndex());
            token(canonical, fact.getBonusSourceFactId());
            token(canonical, fact.getBonusAverageFactId());
            token(canonical, fact.getBonusNatureFactId());
            token(canonical, fact.getComponentId());
            token(canonical, fact.getEarningKind());
            token(canonical, fact.getP15Nature());
        }

        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static void token(StringBuilder target, Object value) {
        String text = value == null ? "<NULL>" : String.valueOf(value);
        target.append(text.length()).append(':').append(text).append('|');
    }
}
