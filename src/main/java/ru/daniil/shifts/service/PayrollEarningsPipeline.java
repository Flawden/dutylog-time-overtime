package ru.daniil.shifts.service;

import ru.daniil.shifts.model.PayrollEarningPhase;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic ordered assembly of already-calculated payroll earnings.
 *
 * This pipeline does not calculate earning formulas, infer semantic kinds,
 * or define eligible calculation bases. It only orders explicit earning
 * amounts by PayrollEarningPhase and sums them exactly.
 */
public final class PayrollEarningsPipeline {

    private PayrollEarningsPipeline() {
    }

    public static Result assemble(List<Earning> source) {
        Objects.requireNonNull(
                source,
                "Payroll earnings are required"
        );

        List<Earning> ordered =
                source.stream()
                        .map(item -> Objects.requireNonNull(
                                item,
                                "Payroll earnings cannot contain null"
                        ))
                        .sorted(
                                Comparator.comparingInt(
                                        item -> item.phase().ordinal()
                                )
                        )
                        .toList();

        long total = 0L;

        for (Earning earning : ordered) {
            total =
                    Math.addExact(
                            total,
                            earning.amountMinor()
                    );
        }

        return new Result(
                total,
                ordered
        );
    }

    public record Earning(
            PayrollEarningPhase phase,
            long amountMinor
    ) {
        public Earning {
            Objects.requireNonNull(
                    phase,
                    "Payroll earning phase is required"
            );
        }
    }

    public record Result(
            long totalAmountMinor,
            List<Earning> orderedEarnings
    ) {
        public Result {
            orderedEarnings =
                    List.copyOf(
                            Objects.requireNonNull(
                                    orderedEarnings,
                                    "Ordered payroll earnings are required"
                            )
                    );
        }
    }
}
