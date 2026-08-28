package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayrollCombinationEpisodeFact;
import ru.daniil.shifts.repo.CompensationComponentRepository;
import ru.daniil.shifts.repo.PayrollCombinationEpisodeFactRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Explicit authority for observed COMBINATION source-line facts.
 *
 * <p>The service preserves only facts explicitly supplied by a trusted source:
 * source period, qualified minutes, observed money, currency and optional
 * agreed percentage. It never reconstructs the substituted employee salary,
 * never derives money from the percentage, and never treats a missing episode
 * as evidence that no combination occurred.</p>
 */
@Service
public class PayrollCombinationEpisodeFactService {

    private final PayrollCombinationEpisodeFactRepository facts;
    private final CompensationComponentRepository components;

    public PayrollCombinationEpisodeFactService(
            PayrollCombinationEpisodeFactRepository facts,
            CompensationComponentRepository components
    ) {
        this.facts = Objects.requireNonNull(
                facts,
                "Combination episode repository is required"
        );
        this.components = Objects.requireNonNull(
                components,
                "Compensation component repository is required"
        );
    }

    /**
     * Returns only explicitly stored facts for the requested payroll month.
     * Empty means "no explicit evidence available", not "no episode existed".
     */
    @Transactional(readOnly = true)
    public List<EpisodeFact> resolveMonth(
            AppUser user,
            YearMonth month
    ) {
        Objects.requireNonNull(
                user,
                "Combination episode facts require user"
        );
        Objects.requireNonNull(
                month,
                "Combination episode facts require payroll month"
        );

        List<PayrollCombinationEpisodeFact> source =
                facts.findByOwnerAndPeriodFromBetweenOrderByComponentIdAscPeriodFromAscPeriodToAscIdAsc(
                        user,
                        month.atDay(1),
                        month.atEndOfMonth()
                );

        if (source == null) {
            throw new IllegalStateException(
                    "Combination episode repository returned null month"
            );
        }

        validateMonthResult(
                source,
                month
        );

        return source.stream()
                .map(PayrollCombinationEpisodeFactService::toFact)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EpisodeFact> resolveComponent(
            AppUser user,
            long componentId
    ) {
        requireComponentIdentity(
                user,
                componentId
        );

        List<PayrollCombinationEpisodeFact> source =
                loadComponentHistory(
                        user,
                        componentId
                );

        return source.stream()
                .map(PayrollCombinationEpisodeFactService::toFact)
                .toList();
    }

    @Transactional
    public PayrollCombinationEpisodeFact create(
            AppUser user,
            long componentId,
            LocalDate periodFrom,
            LocalDate periodTo,
            long qualifiedMinutes,
            long amountMinor,
            String currencyCode,
            Integer agreedRateBps
    ) {
        requireComponentIdentity(
                user,
                componentId
        );

        if (components.findByOwnerAndId(user, componentId).isEmpty()) {
            throw new IllegalArgumentException(
                    "Combination episode component not found"
            );
        }

        PayrollCombinationEpisodeFact candidate =
                new PayrollCombinationEpisodeFact(
                        user,
                        componentId,
                        periodFrom,
                        periodTo,
                        qualifiedMinutes,
                        amountMinor,
                        currencyCode,
                        agreedRateBps
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
    public PayrollCombinationEpisodeFact update(
            AppUser user,
            Long id,
            LocalDate periodFrom,
            LocalDate periodTo,
            long qualifiedMinutes,
            long amountMinor,
            String currencyCode,
            Integer agreedRateBps
    ) {
        Objects.requireNonNull(
                user,
                "Combination episode fact requires user"
        );

        if (id == null) {
            throw new IllegalArgumentException(
                    "Combination episode fact id is required"
            );
        }

        PayrollCombinationEpisodeFact current =
                facts.findByOwnerAndId(user, id)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Combination episode fact not found"
                                )
                        );

        PayrollCombinationEpisodeFact validatedShape =
                new PayrollCombinationEpisodeFact(
                        user,
                        current.getComponentId(),
                        periodFrom,
                        periodTo,
                        qualifiedMinutes,
                        amountMinor,
                        currencyCode,
                        agreedRateBps
                );

        assertNoOverlap(
                loadComponentHistory(user, current.getComponentId()),
                id,
                validatedShape.getPeriodFrom(),
                validatedShape.getPeriodTo()
        );

        current.update(
                periodFrom,
                periodTo,
                qualifiedMinutes,
                amountMinor,
                currencyCode,
                agreedRateBps
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
                "Combination episode fact requires user"
        );

        if (id == null) {
            throw new IllegalArgumentException(
                    "Combination episode fact id is required"
            );
        }

        PayrollCombinationEpisodeFact current =
                facts.findByOwnerAndId(user, id)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Combination episode fact not found"
                                )
                        );

        facts.delete(current);
        facts.flush();
    }

    private List<PayrollCombinationEpisodeFact> loadComponentHistory(
            AppUser user,
            long componentId
    ) {
        List<PayrollCombinationEpisodeFact> history =
                facts.findByOwnerAndComponentIdOrderByPeriodFromAscPeriodToAscIdAsc(
                        user,
                        componentId
                );

        if (history == null) {
            throw new IllegalStateException(
                    "Combination episode repository returned null history"
            );
        }

        validateComponentHistory(
                history,
                componentId
        );

        return List.copyOf(history);
    }

    private void validateMonthResult(
            List<PayrollCombinationEpisodeFact> source,
            YearMonth month
    ) {
        Map<Long, List<PayrollCombinationEpisodeFact>> byComponent =
                new HashMap<>();

        long previousComponent = Long.MIN_VALUE;
        LocalDate previousFrom = null;
        LocalDate previousTo = null;

        for (PayrollCombinationEpisodeFact fact : source) {
            validatePersisted(fact);

            if (!YearMonth.from(fact.getPeriodFrom()).equals(month)
                    || !YearMonth.from(fact.getPeriodTo()).equals(month)) {
                throw new IllegalStateException(
                        "Combination episode repository returned fact outside requested month"
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
                        "Combination episode repository order is invalid"
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

        for (Map.Entry<Long, List<PayrollCombinationEpisodeFact>> entry
                : byComponent.entrySet()) {
            validateComponentHistory(
                    entry.getValue(),
                    entry.getKey()
            );
        }
    }

    private void validateComponentHistory(
            List<PayrollCombinationEpisodeFact> history,
            long componentId
    ) {
        PayrollCombinationEpisodeFact previous = null;

        for (PayrollCombinationEpisodeFact current : history) {
            validatePersisted(current);

            if (current.getComponentId() != componentId) {
                throw new IllegalStateException(
                        "Combination episode repository mixed component identities"
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
                        "Persisted combination episode facts overlap"
                );
            }

            previous = current;
        }
    }

    private void validatePersisted(
            PayrollCombinationEpisodeFact fact
    ) {
        if (fact == null || fact.getId() == null) {
            throw new IllegalStateException(
                    "Persisted combination episode fact lacks identity"
            );
        }

        if (fact.getComponentId() <= 0L
                || fact.getPeriodFrom() == null
                || fact.getPeriodTo() == null
                || fact.getPeriodTo().isBefore(fact.getPeriodFrom())
                || !YearMonth.from(fact.getPeriodFrom())
                        .equals(YearMonth.from(fact.getPeriodTo()))
                || fact.getQualifiedMinutes() <= 0L
                || fact.getAmountMinor() <= 0L
                || fact.getAmountMinor() > 1_000_000_000_000L
                || fact.getCurrencyCode() == null
                || !fact.getCurrencyCode().matches("[A-Z]{3}")
                || (fact.getAgreedRateBps() != null
                && (fact.getAgreedRateBps() < 1
                || fact.getAgreedRateBps() > 10_000_000))) {
            throw new IllegalStateException(
                    "Persisted combination episode fact is invalid"
            );
        }
    }

    private void assertNoOverlap(
            List<PayrollCombinationEpisodeFact> history,
            Long excludeId,
            LocalDate periodFrom,
            LocalDate periodTo
    ) {
        for (PayrollCombinationEpisodeFact current : history) {
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
                        "Combination episode facts for one component must not overlap"
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
                "Combination episode facts require user"
        );

        if (componentId <= 0L) {
            throw new IllegalArgumentException(
                    "Combination episode component id must be positive"
            );
        }
    }

    private static EpisodeFact toFact(
            PayrollCombinationEpisodeFact fact
    ) {
        return new EpisodeFact(
                fact.getId(),
                fact.getComponentId(),
                fact.getPeriodFrom(),
                fact.getPeriodTo(),
                fact.getQualifiedMinutes(),
                fact.getAmountMinor(),
                fact.getCurrencyCode(),
                fact.getAgreedRateBps()
        );
    }

    public record EpisodeFact(
            long factId,
            long componentId,
            LocalDate periodFrom,
            LocalDate periodTo,
            long qualifiedMinutes,
            long amountMinor,
            String currencyCode,
            Integer agreedRateBps
    ) {
        public EpisodeFact {
            if (factId <= 0L
                    || componentId <= 0L
                    || periodFrom == null
                    || periodTo == null
                    || periodTo.isBefore(periodFrom)
                    || !YearMonth.from(periodFrom)
                            .equals(YearMonth.from(periodTo))
                    || qualifiedMinutes <= 0L
                    || amountMinor <= 0L
                    || amountMinor > 1_000_000_000_000L
                    || currencyCode == null
                    || !currencyCode.matches("[A-Z]{3}")
                    || (agreedRateBps != null
                    && (agreedRateBps < 1
                    || agreedRateBps > 10_000_000))) {
                throw new IllegalArgumentException(
                        "Combination episode fact is invalid"
                );
            }
        }
    }
}
