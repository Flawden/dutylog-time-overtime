package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.EmploymentPeriod;
import ru.daniil.shifts.repo.EmploymentPeriodRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Explicit employment-history authority.
 *
 * <p>No employment boundary is inferred from account creation, compensation
 * configuration, DayEntry, Actual Work or Payroll snapshots.</p>
 *
 * <p>Zero stored rows means UNCONFIGURED history. One or more stored rows are
 * an explicit assertion of the complete employment history represented by
 * this system; gaps between periods therefore mean not employed.</p>
 */
@Service
public class EmploymentHistoryService {

    private final EmploymentPeriodRepository periods;

    public EmploymentHistoryService(
            EmploymentPeriodRepository periods
    ) {
        this.periods =
                Objects.requireNonNull(
                        periods,
                        "Employment period repository is required"
                );
    }

    /**
     * Resolves explicit employment coverage intersecting an inclusive range.
     *
     * <p>A configured history may validly resolve to zero slices when the
     * requested range lies wholly outside all employment periods.</p>
     */
    @Transactional(readOnly = true)
    public Resolution resolve(
            AppUser user,
            LocalDate from,
            LocalDate to
    ) {
        requireRange(
                user,
                from,
                to
        );

        List<EmploymentPeriod> history =
                loadValidated(
                        user
                );

        if (history.isEmpty()) {
            return Resolution.unconfigured(
                    from,
                    to
            );
        }

        List<CoverageSlice> slices =
                new ArrayList<>();

        for (EmploymentPeriod period
                : history) {

            if (!intersects(
                    period.getStartDate(),
                    period.getEndDate(),
                    from,
                    to
            )) {
                continue;
            }

            LocalDate overlapFrom =
                    period
                            .getStartDate()
                            .isAfter(
                                    from
                            )
                            ? period.getStartDate()
                            : from;

            LocalDate overlapTo =
                    period.getEndDate() == null
                            || period
                            .getEndDate()
                            .isAfter(
                                    to
                            )
                            ? to
                            : period.getEndDate();

            slices.add(
                    new CoverageSlice(
                            period.getId(),
                            period.getStartDate(),
                            period.getEndDate(),
                            overlapFrom,
                            overlapTo
                    )
            );
        }

        return Resolution.configured(
                from,
                to,
                slices
        );
    }

    @Transactional
    public EmploymentPeriod create(
            AppUser user,
            LocalDate startDate,
            LocalDate endDate
    ) {
        Objects.requireNonNull(
                user,
                "Employment period requires user"
        );

        validateDates(
                startDate,
                endDate
        );

        List<EmploymentPeriod> history =
                loadValidated(
                        user
                );

        assertNoOverlap(
                history,
                null,
                startDate,
                endDate
        );

        return periods.saveAndFlush(
                new EmploymentPeriod(
                        user,
                        startDate,
                        endDate
                )
        );
    }

    @Transactional
    public EmploymentPeriod update(
            AppUser user,
            Long id,
            LocalDate startDate,
            LocalDate endDate
    ) {
        Objects.requireNonNull(
                user,
                "Employment period requires user"
        );

        if (id == null) {
            throw new IllegalArgumentException(
                    "Employment period id is required"
            );
        }

        validateDates(
                startDate,
                endDate
        );

        EmploymentPeriod current =
                periods
                        .findByOwnerAndId(
                                user,
                                id
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Employment period not found"
                                )
                        );

        List<EmploymentPeriod> history =
                loadValidated(
                        user
                );

        assertNoOverlap(
                history,
                id,
                startDate,
                endDate
        );

        current.update(
                startDate,
                endDate
        );

        return periods.saveAndFlush(
                current
        );
    }

    @Transactional
    public void delete(
            AppUser user,
            Long id
    ) {
        Objects.requireNonNull(
                user,
                "Employment period requires user"
        );

        if (id == null) {
            throw new IllegalArgumentException(
                    "Employment period id is required"
            );
        }

        EmploymentPeriod current =
                periods
                        .findByOwnerAndId(
                                user,
                                id
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Employment period not found"
                                )
                        );

        periods.delete(
                current
        );

        periods.flush();
    }

    private List<EmploymentPeriod> loadValidated(
            AppUser user
    ) {
        List<EmploymentPeriod> history =
                periods
                        .findByOwnerOrderByStartDateAscIdAsc(
                                user
                        );

        if (history == null) {
            throw new IllegalStateException(
                    "Employment repository returned null history"
            );
        }

        EmploymentPeriod previous =
                null;

        for (EmploymentPeriod current
                : history) {

            validatePersisted(
                    current
            );

            if (previous != null
                    && overlaps(
                    previous.getStartDate(),
                    previous.getEndDate(),
                    current.getStartDate(),
                    current.getEndDate()
            )) {
                throw new IllegalStateException(
                        "Persisted employment periods overlap"
                );
            }

            previous =
                    current;
        }

        return List.copyOf(
                history
        );
    }

    private void validatePersisted(
            EmploymentPeriod period
    ) {
        if (period == null) {
            throw new IllegalStateException(
                    "Employment repository returned null period"
            );
        }

        if (period.getId() == null) {
            throw new IllegalStateException(
                    "Persisted employment period lacks identity"
            );
        }

        validateDates(
                period.getStartDate(),
                period.getEndDate()
        );
    }

    private void assertNoOverlap(
            List<EmploymentPeriod> history,
            Long excludeId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        for (EmploymentPeriod current
                : history) {

            if (excludeId != null
                    && excludeId.equals(
                    current.getId()
            )) {
                continue;
            }

            if (overlaps(
                    startDate,
                    endDate,
                    current.getStartDate(),
                    current.getEndDate()
            )) {
                throw new IllegalArgumentException(
                        "Employment periods must not overlap"
                );
            }
        }
    }

    private boolean intersects(
            LocalDate firstStart,
            LocalDate firstEnd,
            LocalDate secondStart,
            LocalDate secondEnd
    ) {
        return overlaps(
                firstStart,
                firstEnd,
                secondStart,
                secondEnd
        );
    }

    private boolean overlaps(
            LocalDate firstStart,
            LocalDate firstEnd,
            LocalDate secondStart,
            LocalDate secondEnd
    ) {
        boolean firstEndsBeforeSecond =
                firstEnd != null
                        && firstEnd.isBefore(
                        secondStart
                );

        boolean secondEndsBeforeFirst =
                secondEnd != null
                        && secondEnd.isBefore(
                        firstStart
                );

        return !firstEndsBeforeSecond
                && !secondEndsBeforeFirst;
    }

    private void requireRange(
            AppUser user,
            LocalDate from,
            LocalDate to
    ) {
        Objects.requireNonNull(
                user,
                "Employment history requires user"
        );

        validateDates(
                from,
                to
        );
    }

    private void validateDates(
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (startDate == null) {
            throw new IllegalArgumentException(
                    "Employment start date is required"
            );
        }

        if (endDate != null
                && endDate.isBefore(
                startDate
        )) {
            throw new IllegalArgumentException(
                    "Employment end date must not precede start date"
            );
        }
    }

    public record CoverageSlice(
            Long periodId,
            LocalDate sourceFrom,
            LocalDate sourceTo,
            LocalDate overlapFrom,
            LocalDate overlapTo
    ) {
        public CoverageSlice {
            Objects.requireNonNull(
                    periodId,
                    "Employment period identity is required"
            );

            Objects.requireNonNull(
                    sourceFrom,
                    "Employment source start is required"
            );

            Objects.requireNonNull(
                    overlapFrom,
                    "Employment overlap start is required"
            );

            Objects.requireNonNull(
                    overlapTo,
                    "Employment overlap end is required"
            );

            if (sourceTo != null
                    && sourceTo.isBefore(
                    sourceFrom
            )) {
                throw new IllegalArgumentException(
                        "Employment source period is invalid"
                );
            }

            if (overlapTo.isBefore(
                    overlapFrom
            )) {
                throw new IllegalArgumentException(
                        "Employment overlap is invalid"
                );
            }
        }
    }

    public record Resolution(
            LocalDate from,
            LocalDate to,
            boolean configured,
            List<CoverageSlice> slices
    ) {
        public Resolution {
            Objects.requireNonNull(
                    from,
                    "Employment resolution start is required"
            );

            Objects.requireNonNull(
                    to,
                    "Employment resolution end is required"
            );

            if (to.isBefore(
                    from
            )) {
                throw new IllegalArgumentException(
                        "Employment resolution range is invalid"
                );
            }

            slices = List.copyOf(
                    Objects.requireNonNull(
                            slices,
                            "Employment resolution slices are required"
                    )
            );

            if (!configured
                    && !slices.isEmpty()) {
                throw new IllegalArgumentException(
                        "Unconfigured employment resolution cannot contain slices"
                );
            }
        }

        public static Resolution unconfigured(
                LocalDate from,
                LocalDate to
        ) {
            return new Resolution(
                    from,
                    to,
                    false,
                    List.of()
            );
        }

        public static Resolution configured(
                LocalDate from,
                LocalDate to,
                List<CoverageSlice> slices
        ) {
            return new Resolution(
                    from,
                    to,
                    true,
                    slices
            );
        }

        public boolean ready() {
            return configured;
        }
    }
}
