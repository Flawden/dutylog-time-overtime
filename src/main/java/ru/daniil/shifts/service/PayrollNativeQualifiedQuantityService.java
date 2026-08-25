package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.model.PayrollQualifiedQuantity;
import ru.daniil.shifts.model.PayrollQuantityUnit;
import ru.daniil.shifts.service.OrdinaryWorkPremiumSourceService.OrdinaryPremiumSource;
import ru.daniil.shifts.service.OrdinaryWorkPremiumSourceService.SourcePiece;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Evidence-driven native qualified-quantity projection.
 *
 * Current production support is intentionally narrow:
 *
 * NIGHT_PREMIUM
 *   -> ordinary-work source pieces whose NIGHT dimension is true
 *   -> MINUTES
 *
 * HOLIDAY_PAY is deliberately not mapped yet. Real payroll evidence names
 * "holiday and weekend" work, while the current native classifier exposes
 * HOLIDAY as one payroll dimension. Equating those concepts without proof
 * would silently invent payroll semantics.
 *
 * HARMFUL_CONDITIONS is also deliberately unsupported. Real split-period
 * payroll evidence proves that its qualified minutes cannot simply be copied
 * from BASE_PAY.
 *
 * This service resolves quantity only. It does not calculate money, choose
 * eligible earnings bases, change Time Bank semantics or persist snapshots.
 */
@Service
public class PayrollNativeQualifiedQuantityService {

    private final OrdinaryWorkPremiumSourceService ordinarySource;

    public PayrollNativeQualifiedQuantityService(
            OrdinaryWorkPremiumSourceService ordinarySource
    ) {
        this.ordinarySource =
                Objects.requireNonNull(
                        ordinarySource,
                        "Ordinary work premium source is required"
                );
    }

    @Transactional(readOnly = true)
    public Resolution resolve(
            AppUser user,
            YearMonth payrollMonth,
            PayrollEarningKind earningKind
    ) {
        Objects.requireNonNull(
                user,
                "Payroll qualified quantity requires user"
        );

        Objects.requireNonNull(
                payrollMonth,
                "Payroll qualified quantity requires month"
        );

        Objects.requireNonNull(
                earningKind,
                "Payroll qualified quantity requires earning kind"
        );

        if (earningKind
                != PayrollEarningKind.NIGHT_PREMIUM) {
            throw new IllegalArgumentException(
                    "Native qualified quantity is not proven for "
                            + earningKind
            );
        }

        long qualifiedMinutes = 0L;

        List<BlockingDay> blockers =
                new ArrayList<>();

        for (
                LocalDate date = payrollMonth.atDay(1);
                !date.isAfter(payrollMonth.atEndOfMonth());
                date = date.plusDays(1)
        ) {
            OrdinaryPremiumSource source =
                    Objects.requireNonNull(
                            ordinarySource.project(
                                    user,
                                    date
                            ),
                            "Ordinary source cannot return null"
                    );

            if (!date.equals(
                    source.payrollDate()
            )) {
                throw new IllegalStateException(
                        "Ordinary source returned another payroll date"
                );
            }

            if (!source.ready()) {
                blockers.add(
                        new BlockingDay(
                                date,
                                source.blockingReason()
                        )
                );

                continue;
            }

            for (SourcePiece piece :
                    source.pieces()) {

                if (!piece.night()) {
                    continue;
                }

                qualifiedMinutes =
                        Math.addExact(
                                qualifiedMinutes,
                                piece.minutes()
                        );
            }
        }

        if (!blockers.isEmpty()) {
            return new Resolution(
                    payrollMonth,
                    earningKind,
                    false,
                    null,
                    blockers
            );
        }

        return new Resolution(
                payrollMonth,
                earningKind,
                true,
                PayrollQualifiedQuantity.minutes(
                        qualifiedMinutes
                ),
                List.of()
        );
    }

    public record BlockingDay(
            LocalDate date,
            String reason
    ) {
        public BlockingDay {
            Objects.requireNonNull(
                    date,
                    "Blocking payroll date is required"
            );

            if (reason == null
                    || reason.isBlank()) {
                throw new IllegalArgumentException(
                        "Blocking payroll quantity reason is required"
                );
            }
        }
    }

    public record Resolution(
            YearMonth payrollMonth,
            PayrollEarningKind earningKind,
            boolean ready,
            PayrollQualifiedQuantity quantity,
            List<BlockingDay> blockers
    ) {
        public Resolution {
            Objects.requireNonNull(
                    payrollMonth,
                    "Payroll quantity result month is required"
            );

            Objects.requireNonNull(
                    earningKind,
                    "Payroll quantity result earning kind is required"
            );

            blockers =
                    List.copyOf(
                            Objects.requireNonNull(
                                    blockers,
                                    "Payroll quantity blockers are required"
                            )
                    );

            if (ready) {
                if (!blockers.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Ready payroll quantity cannot contain blockers"
                    );
                }

                Objects.requireNonNull(
                        quantity,
                        "Ready payroll quantity requires quantity"
                );

                if (quantity.unit()
                        != PayrollQuantityUnit.MINUTES) {
                    throw new IllegalArgumentException(
                            "Native NIGHT quantity must use MINUTES"
                    );
                }
            } else {
                if (quantity != null
                        || blockers.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Blocked payroll quantity requires blockers and no partial quantity"
                    );
                }
            }
        }
    }
}
