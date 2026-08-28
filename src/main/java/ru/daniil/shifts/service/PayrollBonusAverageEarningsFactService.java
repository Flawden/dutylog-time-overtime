package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayrollBonusAverageEarningsFact;
import ru.daniil.shifts.model.PayrollBonusSourceFact;
import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.repo.PayrollBonusAverageEarningsFactRepository;
import ru.daniil.shifts.repo.PayrollBonusSourceFactRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Explicit factual authority needed by future PP-540 paragraph-15 policy.
 *
 * <p>This service deliberately does not classify or calculate paragraph-15
 * inclusion. It also never derives award period or annual-result semantics
 * from source period, posting month, component configuration or display text.</p>
 */
@Service
public class PayrollBonusAverageEarningsFactService {

    private final PayrollBonusAverageEarningsFactRepository facts;
    private final PayrollBonusSourceFactRepository bonusSources;

    public PayrollBonusAverageEarningsFactService(
            PayrollBonusAverageEarningsFactRepository facts,
            PayrollBonusSourceFactRepository bonusSources
    ) {
        this.facts = Objects.requireNonNull(
                facts,
                "Bonus average-earnings repository is required"
        );
        this.bonusSources = Objects.requireNonNull(
                bonusSources,
                "Bonus source repository is required"
        );
    }

    @Transactional(readOnly = true)
    public List<AverageFact> resolveForBonusFacts(
            AppUser user,
            List<PayrollBonusSourceFactService.BonusFact> sourceFacts
    ) {
        Objects.requireNonNull(user, "Bonus average-earnings facts require user");

        List<PayrollBonusSourceFactService.BonusFact> source =
                List.copyOf(Objects.requireNonNull(
                        sourceFacts,
                        "Bonus source facts are required"
                ));

        if (source.isEmpty()) {
            return List.of();
        }

        Map<Long, PayrollBonusSourceFactService.BonusFact> byId = new HashMap<>();
        List<Long> ids = new ArrayList<>();

        for (PayrollBonusSourceFactService.BonusFact bonus : source) {
            Objects.requireNonNull(bonus, "Bonus source fact is required");
            if (byId.put(bonus.factId(), bonus) != null) {
                throw new IllegalStateException(
                        "Bonus source fact list contains duplicate identity"
                );
            }
            ids.add(bonus.factId());
        }

        List<PayrollBonusAverageEarningsFact> stored =
                facts.findByOwnerAndBonusSourceFactIdInOrderByBonusSourceFactIdAscIdAsc(
                        user,
                        ids
                );

        if (stored == null) {
            throw new IllegalStateException(
                    "Bonus average-earnings repository returned null facts"
            );
        }

        List<AverageFact> result = new ArrayList<>();
        long previousSourceId = Long.MIN_VALUE;

        for (PayrollBonusAverageEarningsFact fact : stored) {
            validatePersisted(fact);

            if (fact.getBonusSourceFactId() <= previousSourceId) {
                throw new IllegalStateException(
                        "Bonus average-earnings repository order/uniqueness is invalid"
                );
            }
            previousSourceId = fact.getBonusSourceFactId();

            PayrollBonusSourceFactService.BonusFact bonus =
                    byId.get(fact.getBonusSourceFactId());

            if (bonus == null) {
                throw new IllegalStateException(
                        "Bonus average-earnings repository leaked unrelated source fact"
                );
            }

            requireSourceIdentity(fact, bonus);
            result.add(toFact(fact));
        }

        return List.copyOf(result);
    }

    @Transactional(readOnly = true)
    public AverageFact resolveRequired(
            AppUser user,
            long bonusSourceFactId
    ) {
        Objects.requireNonNull(user, "Bonus average-earnings fact requires user");
        if (bonusSourceFactId <= 0L) {
            throw new IllegalArgumentException(
                    "Bonus source fact id must be positive"
            );
        }

        PayrollBonusAverageEarningsFact fact =
                facts.findByOwnerAndBonusSourceFactId(user, bonusSourceFactId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Bonus average-earnings fact is unconfigured"
                        ));

        validatePersisted(fact);
        return toFact(fact);
    }

    @Transactional
    public PayrollBonusAverageEarningsFact create(
            AppUser user,
            long bonusSourceFactId,
            String indicatorKey,
            LocalDate awardPeriodFrom,
            LocalDate awardPeriodTo,
            Boolean annualResult,
            Boolean accruedForActualWorkTime,
            Boolean proratedForPartialAwardPeriod
    ) {
        Objects.requireNonNull(user, "Bonus average-earnings fact requires user");

        if (bonusSourceFactId <= 0L) {
            throw new IllegalArgumentException(
                    "Bonus source fact id must be positive"
            );
        }

        PayrollBonusSourceFact source =
                bonusSources.findByOwnerAndId(user, bonusSourceFactId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Bonus source fact not found"
                        ));

        if (facts.findByOwnerAndBonusSourceFactId(user, bonusSourceFactId)
                .isPresent()) {
            throw new IllegalArgumentException(
                    "Bonus average-earnings fact already exists"
            );
        }

        PayrollBonusAverageEarningsFact candidate =
                new PayrollBonusAverageEarningsFact(
                        user,
                        source.getId(),
                        source.getComponentId(),
                        source.getEarningKind(),
                        indicatorKey,
                        awardPeriodFrom,
                        awardPeriodTo,
                        annualResult,
                        accruedForActualWorkTime,
                        proratedForPartialAwardPeriod
                );

        return facts.saveAndFlush(candidate);
    }

    @Transactional
    public PayrollBonusAverageEarningsFact update(
            AppUser user,
            Long id,
            String indicatorKey,
            LocalDate awardPeriodFrom,
            LocalDate awardPeriodTo,
            Boolean annualResult,
            Boolean accruedForActualWorkTime,
            Boolean proratedForPartialAwardPeriod
    ) {
        Objects.requireNonNull(user, "Bonus average-earnings fact requires user");

        if (id == null) {
            throw new IllegalArgumentException(
                    "Bonus average-earnings fact id is required"
            );
        }

        PayrollBonusAverageEarningsFact current =
                facts.findByOwnerAndId(user, id)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Bonus average-earnings fact not found"
                        ));

        validatePersisted(current);

        PayrollBonusSourceFact source =
                bonusSources.findByOwnerAndId(
                                user,
                                current.getBonusSourceFactId()
                        )
                        .orElseThrow(() -> new IllegalStateException(
                                "Referenced bonus source fact is unavailable"
                        ));

        requireSourceIdentity(current, source);

        PayrollBonusAverageEarningsFact validated =
                new PayrollBonusAverageEarningsFact(
                        user,
                        current.getBonusSourceFactId(),
                        current.getComponentId(),
                        current.getEarningKind(),
                        indicatorKey,
                        awardPeriodFrom,
                        awardPeriodTo,
                        annualResult,
                        accruedForActualWorkTime,
                        proratedForPartialAwardPeriod
                );

        current.update(
                validated.getIndicatorKey(),
                validated.getAwardPeriodFrom(),
                validated.getAwardPeriodTo(),
                validated.getAnnualResult(),
                validated.getAccruedForActualWorkTime(),
                validated.getProratedForPartialAwardPeriod()
        );

        return facts.saveAndFlush(current);
    }

    @Transactional
    public void delete(AppUser user, Long id) {
        Objects.requireNonNull(user, "Bonus average-earnings fact requires user");
        if (id == null) {
            throw new IllegalArgumentException(
                    "Bonus average-earnings fact id is required"
            );
        }

        PayrollBonusAverageEarningsFact current =
                facts.findByOwnerAndId(user, id)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Bonus average-earnings fact not found"
                        ));

        facts.delete(current);
        facts.flush();
    }

    private static void validatePersisted(PayrollBonusAverageEarningsFact fact) {
        if (fact == null
                || fact.getOwner() == null
                || fact.getId() == null
                || fact.getId() <= 0L) {
            throw new IllegalStateException(
                    "Persisted bonus average-earnings fact lacks identity"
            );
        }

        try {
            new PayrollBonusAverageEarningsFact(
                    fact.getOwner(),
                    fact.getBonusSourceFactId(),
                    fact.getComponentId(),
                    fact.getEarningKind(),
                    fact.getIndicatorKey(),
                    fact.getAwardPeriodFrom(),
                    fact.getAwardPeriodTo(),
                    fact.getAnnualResult(),
                    fact.getAccruedForActualWorkTime(),
                    fact.getProratedForPartialAwardPeriod()
            );
        } catch (RuntimeException ex) {
            throw new IllegalStateException(
                    "Persisted bonus average-earnings fact is invalid",
                    ex
            );
        }
    }

    private static void requireSourceIdentity(
            PayrollBonusAverageEarningsFact average,
            PayrollBonusSourceFactService.BonusFact source
    ) {
        if (average.getBonusSourceFactId() != source.factId()
                || average.getComponentId() != source.componentId()
                || average.getEarningKind() != source.earningKind()) {
            throw new IllegalStateException(
                    "Bonus average-earnings fact contradicts source identity"
            );
        }
    }

    private static void requireSourceIdentity(
            PayrollBonusAverageEarningsFact average,
            PayrollBonusSourceFact source
    ) {
        if (source.getId() == null
                || average.getBonusSourceFactId() != source.getId()
                || average.getComponentId() != source.getComponentId()
                || average.getEarningKind() != source.getEarningKind()) {
            throw new IllegalStateException(
                    "Bonus average-earnings fact contradicts persisted source identity"
            );
        }
    }

    private static AverageFact toFact(PayrollBonusAverageEarningsFact fact) {
        return new AverageFact(
                fact.getId(),
                fact.getBonusSourceFactId(),
                fact.getComponentId(),
                fact.getEarningKind(),
                fact.getIndicatorKey(),
                fact.getAwardPeriodFrom(),
                fact.getAwardPeriodTo(),
                fact.getAnnualResult(),
                fact.getAccruedForActualWorkTime(),
                fact.getProratedForPartialAwardPeriod()
        );
    }

    public record AverageFact(
            long factId,
            long bonusSourceFactId,
            long componentId,
            PayrollEarningKind earningKind,
            String indicatorKey,
            LocalDate awardPeriodFrom,
            LocalDate awardPeriodTo,
            Boolean annualResult,
            Boolean accruedForActualWorkTime,
            Boolean proratedForPartialAwardPeriod
    ) {
        public AverageFact {
            if (factId <= 0L || bonusSourceFactId <= 0L || componentId <= 0L
                    || (earningKind != PayrollEarningKind.MONTHLY_BONUS
                    && earningKind != PayrollEarningKind.ONE_TIME_BONUS)) {
                throw new IllegalArgumentException(
                        "Bonus average-earnings fact identity is invalid"
                );
            }

            if (indicatorKey == null
                    || !indicatorKey.matches("[A-Z0-9][A-Z0-9._:-]{0,95}")
                    || awardPeriodFrom == null
                    || awardPeriodTo == null
                    || awardPeriodTo.isBefore(awardPeriodFrom)) {
                throw new IllegalArgumentException(
                        "Bonus average-earnings fact shape is invalid"
                );
            }

            if (Boolean.TRUE.equals(annualResult)
                    && (earningKind != PayrollEarningKind.ONE_TIME_BONUS
                    || awardPeriodFrom.getMonthValue() != 1
                    || awardPeriodFrom.getDayOfMonth() != 1
                    || awardPeriodTo.getMonthValue() != 12
                    || awardPeriodTo.getDayOfMonth() != 31
                    || awardPeriodFrom.getYear() != awardPeriodTo.getYear())) {
                throw new IllegalArgumentException(
                        "Annual-result bonus fact shape is invalid"
                );
            }
        }
    }
}
