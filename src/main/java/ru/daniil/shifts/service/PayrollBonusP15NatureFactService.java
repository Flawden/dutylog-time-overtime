package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayrollBonusAverageEarningsFact;
import ru.daniil.shifts.model.PayrollBonusP15Nature;
import ru.daniil.shifts.model.PayrollBonusP15NatureFact;
import ru.daniil.shifts.repo.PayrollBonusAverageEarningsFactRepository;
import ru.daniil.shifts.repo.PayrollBonusP15NatureFactRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Explicit FACT authority for the paragraph-15 reward family.
 *
 * <p>No legal inclusion or money is decided here. In particular, ONE_TIME_BONUS
 * is never promoted to WORK_PERIOD, ANNUAL_RESULT or SERVICE_LENGTH from its
 * display name, posting month or award-period length.</p>
 */
@Service
public class PayrollBonusP15NatureFactService {

    private final PayrollBonusP15NatureFactRepository facts;
    private final PayrollBonusAverageEarningsFactRepository averageFacts;

    public PayrollBonusP15NatureFactService(
            PayrollBonusP15NatureFactRepository facts,
            PayrollBonusAverageEarningsFactRepository averageFacts
    ) {
        this.facts = Objects.requireNonNull(facts, "P15 nature repository is required");
        this.averageFacts = Objects.requireNonNull(
                averageFacts,
                "Bonus average-earnings repository is required"
        );
    }

    @Transactional(readOnly = true)
    public List<NatureFact> resolveForAverageFacts(
            AppUser user,
            List<PayrollBonusAverageEarningsFactService.AverageFact> source
    ) {
        Objects.requireNonNull(user, "P15 nature facts require user");
        List<PayrollBonusAverageEarningsFactService.AverageFact> input =
                List.copyOf(Objects.requireNonNull(source, "Average facts are required"));

        if (input.isEmpty()) {
            return List.of();
        }

        Map<Long, PayrollBonusAverageEarningsFactService.AverageFact> byId =
                new HashMap<>();
        List<Long> ids = new ArrayList<>();

        for (PayrollBonusAverageEarningsFactService.AverageFact fact : input) {
            Objects.requireNonNull(fact, "Average fact is required");
            if (byId.put(fact.factId(), fact) != null) {
                throw new IllegalStateException("Average fact list contains duplicate identity");
            }
            ids.add(fact.factId());
        }

        List<PayrollBonusP15NatureFact> stored =
                facts.findByOwnerAndBonusAverageFactIdInOrderByBonusAverageFactIdAscIdAsc(
                        user,
                        ids
                );

        if (stored == null) {
            throw new IllegalStateException("P15 nature repository returned null facts");
        }

        List<NatureFact> result = new ArrayList<>();
        long previous = Long.MIN_VALUE;

        for (PayrollBonusP15NatureFact storedFact : stored) {
            validateStored(storedFact);
            if (storedFact.getBonusAverageFactId() <= previous) {
                throw new IllegalStateException("P15 nature repository order/uniqueness is invalid");
            }
            previous = storedFact.getBonusAverageFactId();

            PayrollBonusAverageEarningsFactService.AverageFact average =
                    byId.get(storedFact.getBonusAverageFactId());
            if (average == null) {
                throw new IllegalStateException("P15 nature repository leaked unrelated fact");
            }

            requireIdentity(storedFact, average);
            validateNature(storedFact, average);
            result.add(toFact(storedFact));
        }

        return List.copyOf(result);
    }

    @Transactional
    public PayrollBonusP15NatureFact create(
            AppUser user,
            long bonusAverageFactId,
            PayrollBonusP15Nature p15Nature
    ) {
        Objects.requireNonNull(user, "P15 nature fact requires user");
        Objects.requireNonNull(p15Nature, "P15 nature is required");
        if (bonusAverageFactId <= 0L) {
            throw new IllegalArgumentException("Bonus average fact id must be positive");
        }

        PayrollBonusAverageEarningsFact average =
                averageFacts.findByOwnerAndId(user, bonusAverageFactId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Bonus average-earnings fact not found"
                        ));

        if (facts.findByOwnerAndBonusAverageFactId(user, bonusAverageFactId).isPresent()
                || facts.findByOwnerAndBonusSourceFactId(
                user,
                average.getBonusSourceFactId()
        ).isPresent()) {
            throw new IllegalArgumentException("P15 nature fact already exists");
        }

        PayrollBonusP15NatureFact candidate = fromAverage(user, average, p15Nature);
        return facts.saveAndFlush(candidate);
    }

    @Transactional
    public PayrollBonusP15NatureFact update(
            AppUser user,
            Long id,
            PayrollBonusP15Nature p15Nature
    ) {
        Objects.requireNonNull(user, "P15 nature fact requires user");
        Objects.requireNonNull(p15Nature, "P15 nature is required");
        if (id == null) {
            throw new IllegalArgumentException("P15 nature fact id is required");
        }

        PayrollBonusP15NatureFact current =
                facts.findByOwnerAndId(user, id)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "P15 nature fact not found"
                        ));

        PayrollBonusAverageEarningsFact average =
                averageFacts.findByOwnerAndId(user, current.getBonusAverageFactId())
                        .orElseThrow(() -> new IllegalStateException(
                                "Referenced bonus average-earnings fact is unavailable"
                        ));

        requireIdentity(current, average);

        current.update(
                p15Nature,
                average.getIndicatorKey(),
                average.getAwardPeriodFrom(),
                average.getAwardPeriodTo(),
                average.getAnnualResult()
        );

        return facts.saveAndFlush(current);
    }

    @Transactional
    public void delete(AppUser user, Long id) {
        Objects.requireNonNull(user, "P15 nature fact requires user");
        if (id == null) {
            throw new IllegalArgumentException("P15 nature fact id is required");
        }

        PayrollBonusP15NatureFact current =
                facts.findByOwnerAndId(user, id)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "P15 nature fact not found"
                        ));
        facts.delete(current);
        facts.flush();
    }

    private static PayrollBonusP15NatureFact fromAverage(
            AppUser user,
            PayrollBonusAverageEarningsFact average,
            PayrollBonusP15Nature nature
    ) {
        if (average.getId() == null) {
            throw new IllegalStateException("Bonus average-earnings fact identity is missing");
        }
        return new PayrollBonusP15NatureFact(
                user,
                average.getId(),
                average.getBonusSourceFactId(),
                average.getComponentId(),
                average.getEarningKind(),
                nature,
                average.getIndicatorKey(),
                average.getAwardPeriodFrom(),
                average.getAwardPeriodTo(),
                average.getAnnualResult()
        );
    }

    private static void validateStored(PayrollBonusP15NatureFact fact) {
        if (fact == null
                || fact.getId() == null
                || fact.getId() <= 0L
                || fact.getOwner() == null
                || fact.getP15Nature() == null) {
            throw new IllegalStateException("Persisted P15 nature fact is invalid");
        }
    }

    private static void requireIdentity(
            PayrollBonusP15NatureFact nature,
            PayrollBonusAverageEarningsFact average
    ) {
        if (average.getId() == null
                || nature.getBonusAverageFactId() != average.getId()
                || nature.getBonusSourceFactId() != average.getBonusSourceFactId()
                || nature.getComponentId() != average.getComponentId()
                || nature.getEarningKind() != average.getEarningKind()) {
            throw new IllegalStateException(
                    "P15 nature fact contradicts bonus average-earnings identity"
            );
        }
    }

    private static void requireIdentity(
            PayrollBonusP15NatureFact nature,
            PayrollBonusAverageEarningsFactService.AverageFact average
    ) {
        if (nature.getBonusAverageFactId() != average.factId()
                || nature.getBonusSourceFactId() != average.bonusSourceFactId()
                || nature.getComponentId() != average.componentId()
                || nature.getEarningKind() != average.earningKind()) {
            throw new IllegalStateException(
                    "P15 nature fact contradicts resolved average fact identity"
            );
        }
    }

    private static void validateNature(
            PayrollBonusP15NatureFact nature,
            PayrollBonusAverageEarningsFactService.AverageFact average
    ) {
        PayrollBonusP15Nature value = Objects.requireNonNull(
                nature.getP15Nature(),
                "Persisted P15 nature is required"
        );

        switch (value) {
            case MONTHLY -> {
                if (average.earningKind() != ru.daniil.shifts.model.PayrollEarningKind.MONTHLY_BONUS
                        || !java.time.YearMonth.from(average.awardPeriodFrom())
                        .equals(java.time.YearMonth.from(average.awardPeriodTo()))
                        || Boolean.TRUE.equals(average.annualResult())) {
                    throw new IllegalStateException(
                            "Persisted MONTHLY P15 nature contradicts current F1 fact"
                    );
                }
            }
            case WORK_PERIOD -> {
                if (average.earningKind() != ru.daniil.shifts.model.PayrollEarningKind.ONE_TIME_BONUS
                        || Boolean.TRUE.equals(average.annualResult())
                        || !average.awardPeriodTo().isAfter(
                        average.awardPeriodFrom().plusMonths(1).minusDays(1)
                )) {
                    throw new IllegalStateException(
                            "Persisted WORK_PERIOD P15 nature contradicts current F1 fact"
                    );
                }
            }
            case ANNUAL_RESULT -> {
                if (average.earningKind() != ru.daniil.shifts.model.PayrollEarningKind.ONE_TIME_BONUS
                        || !Boolean.TRUE.equals(average.annualResult())) {
                    throw new IllegalStateException(
                            "Persisted ANNUAL_RESULT P15 nature contradicts current F1 fact"
                    );
                }
            }
            case SERVICE_LENGTH -> {
                if (average.earningKind() != ru.daniil.shifts.model.PayrollEarningKind.ONE_TIME_BONUS
                        || Boolean.TRUE.equals(average.annualResult())) {
                    throw new IllegalStateException(
                            "Persisted SERVICE_LENGTH P15 nature contradicts current F1 fact"
                    );
                }
            }
        }
    }

    private static NatureFact toFact(PayrollBonusP15NatureFact fact) {
        return new NatureFact(
                fact.getId(),
                fact.getBonusAverageFactId(),
                fact.getBonusSourceFactId(),
                fact.getComponentId(),
                fact.getEarningKind(),
                fact.getP15Nature()
        );
    }

    public record NatureFact(
            long factId,
            long bonusAverageFactId,
            long bonusSourceFactId,
            long componentId,
            ru.daniil.shifts.model.PayrollEarningKind earningKind,
            PayrollBonusP15Nature p15Nature
    ) {
        public NatureFact {
            if (factId <= 0L
                    || bonusAverageFactId <= 0L
                    || bonusSourceFactId <= 0L
                    || componentId <= 0L) {
                throw new IllegalArgumentException("P15 nature fact identity is invalid");
            }
            Objects.requireNonNull(earningKind, "P15 nature earning kind is required");
            Objects.requireNonNull(p15Nature, "P15 nature is required");
        }
    }
}
