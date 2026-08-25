package ru.daniil.shifts.service;

import ru.daniil.shifts.model.PayrollSnapshotEarningLine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Canonical SHA-256 identity of one ordered frozen semantic earning set.
 *
 * The fingerprint covers every machine-owned historical field. It is not a
 * payroll formula and does not infer any semantic identity.
 */
public final class PayrollSemanticEarningFingerprint {

    private PayrollSemanticEarningFingerprint() {
    }

    public static String calculate(
            List<PayrollSnapshotEarningLine> lines
    ) {
        Objects.requireNonNull(
                lines,
                "Semantic earning lines are required"
        );

        StringBuilder canonical =
                new StringBuilder();

        token(
                canonical,
                "PAYROLL_SEMANTIC_EARNINGS_V1"
        );

        token(
                canonical,
                lines.size()
        );

        for (int index = 0;
                index < lines.size();
                index++) {

            PayrollSnapshotEarningLine line =
                    Objects.requireNonNull(
                            lines.get(index),
                            "Semantic earning line cannot be null"
                    );

            if (line.getLineIndex()
                    != index) {
                throw new IllegalArgumentException(
                        "Semantic earning lines must be contiguous and ordered"
                );
            }

            token(
                    canonical,
                    line.getLineIndex()
            );

            token(
                    canonical,
                    line.getEarningKind()
            );

            token(
                    canonical,
                    line.getEarningPhase()
            );

            token(
                    canonical,
                    line.getAmountMinor()
            );

            token(
                    canonical,
                    line.getQualifiedQuantityValue()
            );

            token(
                    canonical,
                    line.getQualifiedQuantityUnit()
            );

            token(
                    canonical,
                    line.getEarningPeriodFrom()
            );

            token(
                    canonical,
                    line.getEarningPeriodTo()
            );

            token(
                    canonical,
                    line.getCoverageFrom()
            );

            token(
                    canonical,
                    line.getCoverageTo()
            );
        }

        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest
                                    .getInstance(
                                            "SHA-256"
                                    )
                                    .digest(
                                            canonical
                                                    .toString()
                                                    .getBytes(
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

    private static void token(
            StringBuilder target,
            Object value
    ) {
        String text =
                value == null
                        ? "<NULL>"
                        : value instanceof LocalDate date
                        ? date.toString()
                        : String.valueOf(
                                value
                        );

        target.append(
                text.length()
        );

        target.append(
                ':'
        );

        target.append(
                text
        );

        target.append(
                '|'
        );
    }
}
