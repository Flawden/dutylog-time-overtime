package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.ProductionCalendarDay;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.repo.ProductionCalendarDayRepository;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Employee-specific dated authority for weekly/rest-day identity.
 *
 * <p>This service deliberately does not classify statutory public holidays and
 * does not calculate money. Public-holiday authority belongs to P1B2; economic
 * HOLIDAY_PAY aggregation belongs to P1B3.</p>
 *
 * <p>Absence of a materialized dated roster fact is UNKNOWN, never a guessed
 * weekend. Saturday/Sunday is not an authority for rotating or continuous
 * schedules.</p>
 */
@Service
public class EmployeeRestDayAuthorityService {
    public static final String ROSTER_MISSING = "EMPLOYEE_REST_DAY_ROSTER_MISSING";
    public static final String ROSTER_AMBIGUOUS = "EMPLOYEE_REST_DAY_ROSTER_AMBIGUOUS";
    public static final String CANONICAL_OFF_CONTRADICTORY =
            "EMPLOYEE_REST_DAY_CANONICAL_OFF_CONTRADICTORY";
    public static final String TRANSFER_RULE_INVALID =
            "EMPLOYEE_REST_DAY_TRANSFER_RULE_INVALID";

    private static final String BASE = "BASE";
    private static final String LOCAL = "LOCAL_OVERRIDE";
    private static final String CANONICAL_OFF_NAME = "Выходной";

    private final DayEntryRepository dayEntries;
    private final ProductionCalendarDayRepository productionDays;

    public EmployeeRestDayAuthorityService(
            DayEntryRepository dayEntries,
            ProductionCalendarDayRepository productionDays
    ) {
        this.dayEntries = Objects.requireNonNull(
                dayEntries,
                "Day entry repository is required"
        );
        this.productionDays = Objects.requireNonNull(
                productionDays,
                "Production calendar repository is required"
        );
    }

    @Transactional(readOnly = true)
    public Resolution resolve(
            AppUser owner,
            LocalDate date
    ) {
        Objects.requireNonNull(owner, "Rest-day authority requires owner");
        Objects.requireNonNull(date, "Rest-day authority requires date");

        ProductionCalendarDay effectiveTransfer =
                effectiveTransfer(owner, date);

        if (effectiveTransfer != null) {
            Resolution transfer =
                    resolveTransfer(date, effectiveTransfer);

            if (transfer != null) {
                return transfer;
            }
        }

        DayEntry entry =
                dayEntries.findByOwnerAndDate(owner, date)
                        .orElse(null);

        if (entry == null
                || entry.getShiftType() == null) {
            return Resolution.unresolved(
                    date,
                    ROSTER_MISSING + ":" + date
            );
        }

        ShiftType shift =
                entry.getShiftType();

        if (isCanonicalOff(shift)) {
            if (contradictoryCanonicalOff(shift)) {
                return Resolution.unresolved(
                        date,
                        CANONICAL_OFF_CONTRADICTORY + ":" + date
                );
            }

            return Resolution.roster(
                    Status.REST_DAY,
                    date,
                    entry.getId(),
                    shift.getId()
            );
        }

        if (shift.isBuiltin()
                || hasPositiveOrTimedWork(shift)) {
            return Resolution.roster(
                    Status.WORKING_DAY,
                    date,
                    entry.getId(),
                    shift.getId()
            );
        }

        return Resolution.unresolved(
                date,
                ROSTER_AMBIGUOUS + ":" + date
        );
    }

    private ProductionCalendarDay effectiveTransfer(
            AppUser owner,
            LocalDate date
    ) {
        ProductionCalendarDay local =
                productionDays
                        .findByOwnerAndDateAndLayer(
                                owner,
                                date,
                                LOCAL
                        )
                        .orElse(null);

        ProductionCalendarDay effective =
                local != null
                        ? local
                        : productionDays
                                .findByOwnerAndDateAndLayer(
                                        owner,
                                        date,
                                        BASE
                                )
                                .orElse(null);

        if (effective == null) {
            return null;
        }

        return switch (effective.getDayKind()) {
            case "TRANSFERRED_DAY_OFF",
                 "TRANSFERRED_WORKDAY" -> effective;
            default -> null;
        };
    }

    private Resolution resolveTransfer(
            LocalDate date,
            ProductionCalendarDay rule
    ) {
        if (!"NORM_OVERRIDE".equals(
                rule.getScheduleEffect()
        )) {
            return Resolution.unresolved(
                    date,
                    TRANSFER_RULE_INVALID + ":" + date
            );
        }

        Integer minutes =
                rule.getNormMinutesOverride();

        if ("TRANSFERRED_DAY_OFF".equals(
                rule.getDayKind()
        )) {
            if (minutes == null
                    || minutes != 0) {
                return Resolution.unresolved(
                        date,
                        TRANSFER_RULE_INVALID + ":" + date
                );
            }

            return Resolution.transfer(
                    Status.REST_DAY,
                    date,
                    rule
            );
        }

        if ("TRANSFERRED_WORKDAY".equals(
                rule.getDayKind()
        )) {
            if (minutes == null
                    || minutes <= 0) {
                return Resolution.unresolved(
                        date,
                        TRANSFER_RULE_INVALID + ":" + date
                );
            }

            return Resolution.transfer(
                    Status.WORKING_DAY,
                    date,
                    rule
            );
        }

        return null;
    }

    private boolean isCanonicalOff(
            ShiftType shift
    ) {
        return shift.isBuiltin()
                && CANONICAL_OFF_NAME.equals(
                        shift.getName()
                );
    }

    private boolean contradictoryCanonicalOff(
            ShiftType shift
    ) {
        return shift.effectivePlannedHours() > 0.00001
                || shift.getStartTime() != null
                || shift.getEndTime() != null;
    }

    private boolean hasPositiveOrTimedWork(
            ShiftType shift
    ) {
        return shift.effectivePlannedHours() > 0.00001
                || (shift.getStartTime() != null
                    && shift.getEndTime() != null);
    }

    public enum Status {
        REST_DAY,
        WORKING_DAY,
        UNRESOLVED
    }

    public enum AuthorityKind {
        DATED_ROSTER,
        PRODUCTION_CALENDAR_TRANSFER,
        NONE
    }

    public record Resolution(
            Status status,
            AuthorityKind authorityKind,
            LocalDate date,
            Long dayEntryId,
            Long shiftTypeId,
            Long productionCalendarDayId,
            String sourceLayer,
            String sourceType,
            String sourceRef,
            String blockingReason
    ) {
        public Resolution {
            Objects.requireNonNull(status, "Rest-day status is required");
            Objects.requireNonNull(authorityKind, "Rest-day authority kind is required");
            Objects.requireNonNull(date, "Rest-day resolution date is required");

            if (status == Status.UNRESOLVED) {
                if (authorityKind != AuthorityKind.NONE
                        || blockingReason == null
                        || blockingReason.isBlank()) {
                    throw new IllegalArgumentException(
                            "Unresolved rest-day result requires blocker and no authority"
                    );
                }
            } else if (authorityKind == AuthorityKind.NONE
                    || blockingReason != null) {
                throw new IllegalArgumentException(
                        "Resolved rest-day result requires authority and no blocker"
                );
            }
        }

        static Resolution unresolved(
                LocalDate date,
                String blocker
        ) {
            return new Resolution(
                    Status.UNRESOLVED,
                    AuthorityKind.NONE,
                    date,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    blocker
            );
        }

        static Resolution roster(
                Status status,
                LocalDate date,
                Long dayEntryId,
                Long shiftTypeId
        ) {
            return new Resolution(
                    status,
                    AuthorityKind.DATED_ROSTER,
                    date,
                    dayEntryId,
                    shiftTypeId,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        static Resolution transfer(
                Status status,
                LocalDate date,
                ProductionCalendarDay rule
        ) {
            return new Resolution(
                    status,
                    AuthorityKind.PRODUCTION_CALENDAR_TRANSFER,
                    date,
                    null,
                    null,
                    rule.getId(),
                    rule.getLayer(),
                    rule.getSourceType(),
                    rule.getSourceRef(),
                    null
            );
        }

        public boolean ready() {
            return status != Status.UNRESOLVED;
        }

        public boolean restDay() {
            return status == Status.REST_DAY;
        }
    }
}
