package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayrollBonusSourceFact;
import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.repo.CompensationComponentRepository;
import ru.daniil.shifts.repo.PayrollBonusSourceFactRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Explicit authority for observed MONTHLY_BONUS / ONE_TIME_BONUS source-line
 * facts.
 *
 * <p>The service preserves only trusted source facts. It does not infer source
 * periods from payroll month, does not reconstruct a bonus calculation base
 * and does not implement average-earnings paragraph 15 treatment.</p>
 */
@Service
public class PayrollBonusSourceFactService {

    private final PayrollBonusSourceFactRepository facts;
    private final CompensationComponentRepository components;

    public PayrollBonusSourceFactService(
            PayrollBonusSourceFactRepository facts,
            CompensationComponentRepository components
    ) {
        this.facts = Objects.requireNonNull(
                facts,
                "Bonus source repository is required"
        );
        this.components = Objects.requireNonNull(
                components,
                "Compensation component repository is required"
        );
    }

    @Transactional(readOnly = true)
    public List<BonusFact> resolveMonth(
            AppUser user,
            YearMonth month
    ) {
        Objects.requireNonNull(user, "Bonus source facts require user");
        Objects.requireNonNull(month, "Bonus source facts require payroll month");

        List<PayrollBonusSourceFact> source =
                facts.findByOwnerAndPeriodFromBetweenOrderByEarningKindAscComponentIdAscPeriodFromAscPeriodToAscIdAsc(
                        user,
                        month.atDay(1),
                        month.atEndOfMonth()
                );

        if (source == null) {
            throw new IllegalStateException(
                    "Bonus source repository returned null month"
            );
        }

        validateMonthResult(source, month);

        return source.stream()
                .map(PayrollBonusSourceFactService::toFact)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BonusFact> resolveComponent(
            AppUser user,
            long componentId,
            PayrollEarningKind earningKind
    ) {
        requireComponentIdentity(user, componentId);
        PayrollEarningKind kind = requireBonusKind(earningKind);

        return loadHistory(user, componentId, kind)
                .stream()
                .map(PayrollBonusSourceFactService::toFact)
                .toList();
    }

    @Transactional
    public PayrollBonusSourceFact create(
            AppUser user,
            long componentId,
            PayrollEarningKind earningKind,
            LocalDate periodFrom,
            LocalDate periodTo,
            long amountMinor,
            String currencyCode
    ) {
        requireComponentIdentity(user, componentId);
        PayrollEarningKind kind = requireBonusKind(earningKind);

        if (components.findByOwnerAndId(user, componentId).isEmpty()) {
            throw new IllegalArgumentException(
                    "Bonus source component not found"
            );
        }

        PayrollBonusSourceFact candidate =
                new PayrollBonusSourceFact(
                        user,
                        componentId,
                        kind,
                        periodFrom,
                        periodTo,
                        amountMinor,
                        currencyCode
                );

        assertNoOverlap(
                loadHistory(user, componentId, kind),
                null,
                candidate.getPeriodFrom(),
                candidate.getPeriodTo()
        );

        return facts.saveAndFlush(candidate);
    }

    @Transactional
    public PayrollBonusSourceFact update(
            AppUser user,
            Long id,
            LocalDate periodFrom,
            LocalDate periodTo,
            long amountMinor,
            String currencyCode
    ) {
        Objects.requireNonNull(user, "Bonus source fact requires user");

        if (id == null) {
            throw new IllegalArgumentException(
                    "Bonus source fact id is required"
            );
        }

        PayrollBonusSourceFact current =
                facts.findByOwnerAndId(user, id)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Bonus source fact not found"
                                )
                        );

        PayrollBonusSourceFact validatedShape =
                new PayrollBonusSourceFact(
                        user,
                        current.getComponentId(),
                        current.getEarningKind(),
                        periodFrom,
                        periodTo,
                        amountMinor,
                        currencyCode
                );

        assertNoOverlap(
                loadHistory(
                        user,
                        current.getComponentId(),
                        current.getEarningKind()
                ),
                id,
                validatedShape.getPeriodFrom(),
                validatedShape.getPeriodTo()
        );

        current.update(periodFrom, periodTo, amountMinor, currencyCode);
        return facts.saveAndFlush(current);
    }

    @Transactional
    public void delete(
            AppUser user,
            Long id
    ) {
        Objects.requireNonNull(user, "Bonus source fact requires user");

        if (id == null) {
            throw new IllegalArgumentException(
                    "Bonus source fact id is required"
            );
        }

        PayrollBonusSourceFact current =
                facts.findByOwnerAndId(user, id)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Bonus source fact not found"
                                )
                        );

        facts.delete(current);
        facts.flush();
    }

    private List<PayrollBonusSourceFact> loadHistory(
            AppUser user,
            long componentId,
            PayrollEarningKind earningKind
    ) {
        List<PayrollBonusSourceFact> history =
                facts.findByOwnerAndComponentIdAndEarningKindOrderByPeriodFromAscPeriodToAscIdAsc(
                        user,
                        componentId,
                        earningKind
                );

        if (history == null) {
            throw new IllegalStateException(
                    "Bonus source repository returned null history"
            );
        }

        validateHistory(history, componentId, earningKind);
        return List.copyOf(history);
    }

    private void validateMonthResult(
            List<PayrollBonusSourceFact> source,
            YearMonth month
    ) {
        Map<BonusIdentity, List<PayrollBonusSourceFact>> byIdentity =
                new HashMap<>();

        PayrollEarningKind previousKind = null;
        long previousComponent = Long.MIN_VALUE;
        LocalDate previousFrom = null;
        LocalDate previousTo = null;

        for (PayrollBonusSourceFact fact : source) {
            validatePersisted(fact);

            if (!YearMonth.from(fact.getPeriodFrom()).equals(month)
                    || !YearMonth.from(fact.getPeriodTo()).equals(month)) {
                throw new IllegalStateException(
                        "Bonus source repository returned fact outside requested month"
                );
            }

            PayrollEarningKind kind = fact.getEarningKind();
            long componentId = fact.getComponentId();

            int kindOrder = previousKind == null
                    ? 1
                    : kind.name().compareTo(previousKind.name());

            if (previousKind != null
                    && (kindOrder < 0
                    || (kindOrder == 0 && componentId < previousComponent)
                    || (kindOrder == 0 && componentId == previousComponent
                    && previousFrom != null
                    && fact.getPeriodFrom().isBefore(previousFrom))
                    || (kindOrder == 0 && componentId == previousComponent
                    && previousFrom != null
                    && fact.getPeriodFrom().equals(previousFrom)
                    && previousTo != null
                    && fact.getPeriodTo().isBefore(previousTo)))) {
                throw new IllegalStateException(
                        "Bonus source repository order is invalid"
                );
            }

            previousKind = kind;
            previousComponent = componentId;
            previousFrom = fact.getPeriodFrom();
            previousTo = fact.getPeriodTo();

            BonusIdentity identity = new BonusIdentity(componentId, kind);
            byIdentity.computeIfAbsent(
                    identity,
                    ignored -> new ArrayList<>()
            ).add(fact);
        }

        for (Map.Entry<BonusIdentity, List<PayrollBonusSourceFact>> entry
                : byIdentity.entrySet()) {
            validateHistory(
                    entry.getValue(),
                    entry.getKey().componentId(),
                    entry.getKey().earningKind()
            );
        }
    }

    private void validateHistory(
            List<PayrollBonusSourceFact> history,
            long componentId,
            PayrollEarningKind earningKind
    ) {
        PayrollBonusSourceFact previous = null;

        for (PayrollBonusSourceFact current : history) {
            validatePersisted(current);

            if (current.getComponentId() != componentId
                    || current.getEarningKind() != earningKind) {
                throw new IllegalStateException(
                        "Bonus source repository mixed semantic identities"
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
                        "Persisted bonus source facts overlap"
                );
            }

            previous = current;
        }
    }

    private void validatePersisted(PayrollBonusSourceFact fact) {
        if (fact == null || fact.getId() == null) {
            throw new IllegalStateException(
                    "Persisted bonus source fact lacks identity"
            );
        }

        PayrollEarningKind kind = fact.getEarningKind();

        if (fact.getComponentId() <= 0L
                || (kind != PayrollEarningKind.MONTHLY_BONUS
                && kind != PayrollEarningKind.ONE_TIME_BONUS)
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
                    "Persisted bonus source fact is invalid"
            );
        }
    }

    private void assertNoOverlap(
            List<PayrollBonusSourceFact> history,
            Long excludeId,
            LocalDate periodFrom,
            LocalDate periodTo
    ) {
        for (PayrollBonusSourceFact current : history) {
            if (excludeId != null && excludeId.equals(current.getId())) {
                continue;
            }

            if (overlaps(
                    periodFrom,
                    periodTo,
                    current.getPeriodFrom(),
                    current.getPeriodTo()
            )) {
                throw new IllegalArgumentException(
                        "Bonus source facts for one semantic component must not overlap"
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

    private void requireComponentIdentity(AppUser user, long componentId) {
        Objects.requireNonNull(user, "Bonus source facts require user");

        if (componentId <= 0L) {
            throw new IllegalArgumentException(
                    "Bonus source component id must be positive"
            );
        }
    }

    private static PayrollEarningKind requireBonusKind(
            PayrollEarningKind earningKind
    ) {
        if (earningKind != PayrollEarningKind.MONTHLY_BONUS
                && earningKind != PayrollEarningKind.ONE_TIME_BONUS) {
            throw new IllegalArgumentException(
                    "Bonus source kind must be MONTHLY_BONUS or ONE_TIME_BONUS"
            );
        }
        return earningKind;
    }

    private static BonusFact toFact(PayrollBonusSourceFact fact) {
        return new BonusFact(
                fact.getId(),
                fact.getComponentId(),
                fact.getEarningKind(),
                fact.getPeriodFrom(),
                fact.getPeriodTo(),
                fact.getAmountMinor(),
                fact.getCurrencyCode()
        );
    }

    private record BonusIdentity(
            long componentId,
            PayrollEarningKind earningKind
    ) {
    }

    public record BonusFact(
            long factId,
            long componentId,
            PayrollEarningKind earningKind,
            LocalDate periodFrom,
            LocalDate periodTo,
            long amountMinor,
            String currencyCode
    ) {
        public BonusFact {
            if (factId <= 0L
                    || componentId <= 0L
                    || (earningKind != PayrollEarningKind.MONTHLY_BONUS
                    && earningKind != PayrollEarningKind.ONE_TIME_BONUS)
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
                        "Bonus source fact is invalid"
                );
            }
        }
    }
}
