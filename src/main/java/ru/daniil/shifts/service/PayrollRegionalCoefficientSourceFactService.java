package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayrollRegionalCoefficientSourceFact;
import ru.daniil.shifts.repo.CompensationComponentRepository;
import ru.daniil.shifts.repo.PayrollRegionalCoefficientSourceFactRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Explicit authority for observed REGIONAL_COEFFICIENT source-line facts.
 *
 * <p>The service preserves only facts supplied by a trusted source: source
 * period, observed money and currency. It never reconstructs an eligible
 * earnings base, never allocates a monthly regional amount over other earning
 * source lines and never treats a missing fact as a whole-month period.</p>
 */
@Service
public class PayrollRegionalCoefficientSourceFactService {

    private final PayrollRegionalCoefficientSourceFactRepository facts;
    private final CompensationComponentRepository components;

    public PayrollRegionalCoefficientSourceFactService(
            PayrollRegionalCoefficientSourceFactRepository facts,
            CompensationComponentRepository components
    ) {
        this.facts = Objects.requireNonNull(
                facts,
                "Regional source repository is required"
        );
        this.components = Objects.requireNonNull(
                components,
                "Compensation component repository is required"
        );
    }

    /**
     * Returns only explicitly stored source facts for one payroll month.
     * Empty means "no exact period evidence available".
     */
    @Transactional(readOnly = true)
    public List<SourceFact> resolveMonth(
            AppUser user,
            YearMonth month
    ) {
        Objects.requireNonNull(
                user,
                "Regional source facts require user"
        );
        Objects.requireNonNull(
                month,
                "Regional source facts require payroll month"
        );

        List<PayrollRegionalCoefficientSourceFact> source =
                facts.findByOwnerAndPeriodFromBetweenOrderByComponentIdAscPeriodFromAscPeriodToAscIdAsc(
                        user,
                        month.atDay(1),
                        month.atEndOfMonth()
                );

        if (source == null) {
            throw new IllegalStateException(
                    "Regional source repository returned null month"
            );
        }

        validateMonthResult(source, month);

        return source.stream()
                .map(PayrollRegionalCoefficientSourceFactService::toFact)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SourceFact> resolveComponent(
            AppUser user,
            long componentId
    ) {
        requireComponentIdentity(user, componentId);

        return loadComponentHistory(user, componentId)
                .stream()
                .map(PayrollRegionalCoefficientSourceFactService::toFact)
                .toList();
    }

    @Transactional
    public PayrollRegionalCoefficientSourceFact create(
            AppUser user,
            long componentId,
            LocalDate periodFrom,
            LocalDate periodTo,
            long amountMinor,
            String currencyCode
    ) {
        requireComponentIdentity(user, componentId);

        if (components.findByOwnerAndId(user, componentId).isEmpty()) {
            throw new IllegalArgumentException(
                    "Regional source component not found"
            );
        }

        PayrollRegionalCoefficientSourceFact candidate =
                new PayrollRegionalCoefficientSourceFact(
                        user,
                        componentId,
                        periodFrom,
                        periodTo,
                        amountMinor,
                        currencyCode
                );

        assertNoOverlap(
                loadComponentHistory(user, componentId),
                null,
                candidate.getPeriodFrom(),
                candidate.getPeriodTo()
        );

        return facts.saveAndFlush(candidate);
    }

    @Transactional
    public PayrollRegionalCoefficientSourceFact update(
            AppUser user,
            Long id,
            LocalDate periodFrom,
            LocalDate periodTo,
            long amountMinor,
            String currencyCode
    ) {
        Objects.requireNonNull(
                user,
                "Regional source fact requires user"
        );

        if (id == null) {
            throw new IllegalArgumentException(
                    "Regional source fact id is required"
            );
        }

        PayrollRegionalCoefficientSourceFact current =
                facts.findByOwnerAndId(user, id)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Regional source fact not found"
                                )
                        );

        PayrollRegionalCoefficientSourceFact validatedShape =
                new PayrollRegionalCoefficientSourceFact(
                        user,
                        current.getComponentId(),
                        periodFrom,
                        periodTo,
                        amountMinor,
                        currencyCode
                );

        assertNoOverlap(
                loadComponentHistory(
                        user,
                        current.getComponentId()
                ),
                id,
                validatedShape.getPeriodFrom(),
                validatedShape.getPeriodTo()
        );

        current.update(
                periodFrom,
                periodTo,
                amountMinor,
                currencyCode
        );

        return facts.saveAndFlush(current);
    }

    @Transactional
    public void delete(
            AppUser user,
            Long id
    ) {
        Objects.requireNonNull(
                user,
                "Regional source fact requires user"
        );

        if (id == null) {
            throw new IllegalArgumentException(
                    "Regional source fact id is required"
            );
        }

        PayrollRegionalCoefficientSourceFact current =
                facts.findByOwnerAndId(user, id)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Regional source fact not found"
                                )
                        );

        facts.delete(current);
        facts.flush();
    }

    private List<PayrollRegionalCoefficientSourceFact> loadComponentHistory(
            AppUser user,
            long componentId
    ) {
        List<PayrollRegionalCoefficientSourceFact> history =
                facts.findByOwnerAndComponentIdOrderByPeriodFromAscPeriodToAscIdAsc(
                        user,
                        componentId
                );

        if (history == null) {
            throw new IllegalStateException(
                    "Regional source repository returned null history"
            );
        }

        validateComponentHistory(history, componentId);
        return List.copyOf(history);
    }

    private void validateMonthResult(
            List<PayrollRegionalCoefficientSourceFact> source,
            YearMonth month
    ) {
        Map<Long, List<PayrollRegionalCoefficientSourceFact>> byComponent =
                new HashMap<>();

        long previousComponent = Long.MIN_VALUE;
        LocalDate previousFrom = null;
        LocalDate previousTo = null;

        for (PayrollRegionalCoefficientSourceFact fact : source) {
            validatePersisted(fact);

            if (!YearMonth.from(fact.getPeriodFrom()).equals(month)
                    || !YearMonth.from(fact.getPeriodTo()).equals(month)) {
                throw new IllegalStateException(
                        "Regional source repository returned fact outside requested month"
                );
            }

            long componentId = fact.getComponentId();

            if (componentId < previousComponent
                    || (componentId == previousComponent
                    && previousFrom != null
                    && fact.getPeriodFrom().isBefore(previousFrom))
                    || (componentId == previousComponent
                    && previousFrom != null
                    && fact.getPeriodFrom().equals(previousFrom)
                    && previousTo != null
                    && fact.getPeriodTo().isBefore(previousTo))) {
                throw new IllegalStateException(
                        "Regional source repository order is invalid"
                );
            }

            previousComponent = componentId;
            previousFrom = fact.getPeriodFrom();
            previousTo = fact.getPeriodTo();

            byComponent.computeIfAbsent(
                    componentId,
                    ignored -> new ArrayList<>()
            ).add(fact);
        }

        for (Map.Entry<Long, List<PayrollRegionalCoefficientSourceFact>> entry
                : byComponent.entrySet()) {
            validateComponentHistory(
                    entry.getValue(),
                    entry.getKey()
            );
        }
    }

    private void validateComponentHistory(
            List<PayrollRegionalCoefficientSourceFact> history,
            long componentId
    ) {
        PayrollRegionalCoefficientSourceFact previous = null;

        for (PayrollRegionalCoefficientSourceFact current : history) {
            validatePersisted(current);

            if (current.getComponentId() != componentId) {
                throw new IllegalStateException(
                        "Regional source repository mixed component identities"
                );
            }

            if (previous != null
                    && overlaps(
                            previous.getPeriodFrom(),
                            previous.getPeriodTo(),
                            current.getPeriodFrom(),
                            current.getPeriodTo()
                    )) {
                throw new IllegalStateException(
                        "Persisted regional source facts overlap"
                );
            }

            previous = current;
        }
    }

    private void validatePersisted(
            PayrollRegionalCoefficientSourceFact fact
    ) {
        if (fact == null || fact.getId() == null) {
            throw new IllegalStateException(
                    "Persisted regional source fact lacks identity"
            );
        }

        if (fact.getComponentId() <= 0L
                || fact.getPeriodFrom() == null
                || fact.getPeriodTo() == null
                || fact.getPeriodTo().isBefore(fact.getPeriodFrom())
                || !YearMonth.from(fact.getPeriodFrom())
                        .equals(YearMonth.from(fact.getPeriodTo()))
                || fact.getAmountMinor() <= 0L
                || fact.getAmountMinor() > 1_000_000_000_000L
                || fact.getCurrencyCode() == null
                || !fact.getCurrencyCode().matches("[A-Z]{3}")) {
            throw new IllegalStateException(
                    "Persisted regional source fact is invalid"
            );
        }
    }

    private void assertNoOverlap(
            List<PayrollRegionalCoefficientSourceFact> history,
            Long excludeId,
            LocalDate periodFrom,
            LocalDate periodTo
    ) {
        for (PayrollRegionalCoefficientSourceFact current : history) {
            if (excludeId != null
                    && excludeId.equals(current.getId())) {
                continue;
            }

            if (overlaps(
                    periodFrom,
                    periodTo,
                    current.getPeriodFrom(),
                    current.getPeriodTo()
            )) {
                throw new IllegalArgumentException(
                        "Regional source facts for one component must not overlap"
                );
            }
        }
    }

    private boolean overlaps(
            LocalDate firstFrom,
            LocalDate firstTo,
            LocalDate secondFrom,
            LocalDate secondTo
    ) {
        return !firstTo.isBefore(secondFrom)
                && !secondTo.isBefore(firstFrom);
    }

    private void requireComponentIdentity(
            AppUser user,
            long componentId
    ) {
        Objects.requireNonNull(
                user,
                "Regional source facts require user"
        );

        if (componentId <= 0L) {
            throw new IllegalArgumentException(
                    "Regional source component id must be positive"
            );
        }
    }

    private static SourceFact toFact(
            PayrollRegionalCoefficientSourceFact fact
    ) {
        return new SourceFact(
                fact.getId(),
                fact.getComponentId(),
                fact.getPeriodFrom(),
                fact.getPeriodTo(),
                fact.getAmountMinor(),
                fact.getCurrencyCode()
        );
    }

    public record SourceFact(
            long factId,
            long componentId,
            LocalDate periodFrom,
            LocalDate periodTo,
            long amountMinor,
            String currencyCode
    ) {
        public SourceFact {
            if (factId <= 0L
                    || componentId <= 0L
                    || periodFrom == null
                    || periodTo == null
                    || periodTo.isBefore(periodFrom)
                    || !YearMonth.from(periodFrom)
                            .equals(YearMonth.from(periodTo))
                    || amountMinor <= 0L
                    || amountMinor > 1_000_000_000_000L
                    || currencyCode == null
                    || !currencyCode.matches("[A-Z]{3}")) {
                throw new IllegalArgumentException(
                        "Regional source fact is invalid"
                );
            }
        }
    }
}
