package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.WorkTimeAccountingMode;
import ru.daniil.shifts.model.WorkTimeAccountingTerm;
import ru.daniil.shifts.repo.WorkTimeAccountingTermRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Explicit effective-dated authority for historical work-time accounting mode.
 *
 * <p>The service never infers DAILY/SUMMARIZED from SALARY/HOURLY pay mode,
 * shift schedule shape, accounting-period closure state, worked quantities or
 * any other operational data. Missing persisted authority remains UNKNOWN.</p>
 *
 * <p>This stage owns regime FACT only. It does not derive worked/norm units,
 * freeze Payroll day facts, decide paragraph-15 policy or calculate money.</p>
 */
@Service
public class WorkTimeAccountingHistoryService {

    public static final String MODE_FACT_MISSING =
            "WORK_TIME_ACCOUNTING_MODE_FACT_MISSING";

    private final WorkTimeAccountingTermRepository terms;

    public WorkTimeAccountingHistoryService(
            WorkTimeAccountingTermRepository terms
    ) {
        this.terms = Objects.requireNonNull(
                terms,
                "Work-time accounting repository is required"
        );
    }

    @Transactional(readOnly = true)
    public Resolution resolveAt(
            AppUser user,
            LocalDate date
    ) {
        requireUserAndDate(user, date);

        WorkTimeAccountingTerm term = terms
                .findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        user,
                        date
                )
                .orElse(null);

        if (term == null) {
            return Resolution.blocked(
                    date,
                    MODE_FACT_MISSING + ":" + date
            );
        }

        return Resolution.ready(
                date,
                fact(term)
        );
    }

    /**
     * Resolve exact regime ownership across an inclusive historical range.
     *
     * <p>Every returned slice carries the persisted term identity that owns it.
     * If the range starts before the first configured term, resolution blocks
     * and exposes no partial slices.</p>
     */
    @Transactional(readOnly = true)
    public RangeResolution resolveRange(
            AppUser user,
            LocalDate from,
            LocalDate to
    ) {
        requireRange(user, from, to);

        WorkTimeAccountingTerm base = terms
                .findFirstByOwnerAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        user,
                        from
                )
                .orElse(null);

        if (base == null) {
            return RangeResolution.blocked(
                    from,
                    to,
                    MODE_FACT_MISSING + ":" + from
            );
        }

        List<WorkTimeAccountingTerm> history = terms
                .findByOwnerOrderByEffectiveFromAscIdAsc(user);

        if (history == null) {
            throw new IllegalStateException(
                    "Work-time accounting repository returned null history"
            );
        }

        List<ModeSlice> slices = new ArrayList<>();
        WorkTimeAccountingTerm current = validated(base);
        LocalDate cursor = from;

        for (WorkTimeAccountingTerm candidate : history) {
            candidate = validated(candidate);

            LocalDate change = candidate.getEffectiveFrom();
            if (!change.isAfter(from) || change.isAfter(to)) {
                continue;
            }

            LocalDate sliceTo = change.minusDays(1);
            slices.add(slice(current, cursor, sliceTo));
            current = candidate;
            cursor = change;
        }

        slices.add(slice(current, cursor, to));

        return RangeResolution.ready(
                from,
                to,
                slices
        );
    }

    @Transactional(readOnly = true)
    public List<ModeFact> history(AppUser user) {
        requireUser(user);

        List<WorkTimeAccountingTerm> history = terms
                .findByOwnerOrderByEffectiveFromAscIdAsc(user);

        if (history == null) {
            throw new IllegalStateException(
                    "Work-time accounting repository returned null history"
            );
        }

        return history.stream()
                .map(this::fact)
                .toList();
    }

    @Transactional
    public ModeFact upsert(
            AppUser user,
            LocalDate effectiveFrom,
            WorkTimeAccountingMode mode
    ) {
        requireUserAndDate(user, effectiveFrom);
        Objects.requireNonNull(mode, "Work-time accounting mode is required");

        WorkTimeAccountingTerm term = terms
                .findByOwnerAndEffectiveFrom(user, effectiveFrom)
                .orElseGet(() -> new WorkTimeAccountingTerm(
                        user,
                        effectiveFrom,
                        mode
                ));

        term.setAccountingMode(mode);
        return fact(terms.saveAndFlush(term));
    }

    @Transactional
    public void delete(
            AppUser user,
            LocalDate effectiveFrom
    ) {
        requireUserAndDate(user, effectiveFrom);

        terms.findByOwnerAndEffectiveFrom(user, effectiveFrom)
                .ifPresent(terms::delete);
        terms.flush();
    }

    private ModeFact fact(WorkTimeAccountingTerm raw) {
        WorkTimeAccountingTerm term = validated(raw);
        return new ModeFact(
                term.getId(),
                term.getEffectiveFrom(),
                term.getAccountingMode()
        );
    }

    private ModeSlice slice(
            WorkTimeAccountingTerm raw,
            LocalDate from,
            LocalDate to
    ) {
        WorkTimeAccountingTerm term = validated(raw);
        return new ModeSlice(
                term.getId(),
                term.getEffectiveFrom(),
                term.getAccountingMode(),
                from,
                to
        );
    }

    private WorkTimeAccountingTerm validated(WorkTimeAccountingTerm term) {
        if (term == null
                || term.getId() == null
                || term.getEffectiveFrom() == null
                || term.getAccountingMode() == null) {
            throw new IllegalStateException(
                    "Persisted work-time accounting term is incomplete"
            );
        }
        return term;
    }

    private void requireRange(
            AppUser user,
            LocalDate from,
            LocalDate to
    ) {
        requireUserAndDate(user, from);
        Objects.requireNonNull(to, "Work-time accounting range end is required");
        if (to.isBefore(from)) {
            throw new IllegalArgumentException(
                    "Work-time accounting range end must not precede start"
            );
        }
    }

    private void requireUserAndDate(
            AppUser user,
            LocalDate date
    ) {
        requireUser(user);
        Objects.requireNonNull(date, "Work-time accounting date is required");
    }

    private void requireUser(AppUser user) {
        Objects.requireNonNull(user, "Work-time accounting authority requires user");
    }

    public record ModeFact(
            long termId,
            LocalDate effectiveFrom,
            WorkTimeAccountingMode mode
    ) {
        public ModeFact {
            if (termId <= 0L) {
                throw new IllegalArgumentException(
                        "Work-time accounting term identity must be positive"
                );
            }
            Objects.requireNonNull(effectiveFrom, "Work-time accounting effective date is required");
            Objects.requireNonNull(mode, "Work-time accounting mode is required");
        }
    }

    public record Resolution(
            LocalDate date,
            boolean ready,
            String blockingReason,
            ModeFact fact
    ) {
        public Resolution {
            Objects.requireNonNull(date, "Work-time accounting resolution date is required");
            if (ready == (blockingReason != null)) {
                throw new IllegalArgumentException(
                        "Work-time accounting resolution state is invalid"
                );
            }
            if (ready != (fact != null)) {
                throw new IllegalArgumentException(
                        "Work-time accounting fact exposure is invalid"
                );
            }
        }

        public static Resolution ready(LocalDate date, ModeFact fact) {
            return new Resolution(date, true, null, Objects.requireNonNull(fact));
        }

        public static Resolution blocked(LocalDate date, String reason) {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("Blocking reason is required");
            }
            return new Resolution(date, false, reason, null);
        }
    }

    public record ModeSlice(
            long termId,
            LocalDate termEffectiveFrom,
            WorkTimeAccountingMode mode,
            LocalDate overlapFrom,
            LocalDate overlapTo
    ) {
        public ModeSlice {
            if (termId <= 0L) {
                throw new IllegalArgumentException(
                        "Work-time accounting slice requires term identity"
                );
            }
            Objects.requireNonNull(termEffectiveFrom, "Work-time accounting term date is required");
            Objects.requireNonNull(mode, "Work-time accounting slice mode is required");
            Objects.requireNonNull(overlapFrom, "Work-time accounting overlap start is required");
            Objects.requireNonNull(overlapTo, "Work-time accounting overlap end is required");
            if (overlapTo.isBefore(overlapFrom)) {
                throw new IllegalArgumentException(
                        "Work-time accounting overlap is invalid"
                );
            }
            if (termEffectiveFrom.isAfter(overlapFrom)) {
                throw new IllegalArgumentException(
                        "Work-time accounting term cannot start after owned slice"
                );
            }
        }
    }

    public record RangeResolution(
            LocalDate from,
            LocalDate to,
            boolean ready,
            String blockingReason,
            List<ModeSlice> slices
    ) {
        public RangeResolution {
            Objects.requireNonNull(from, "Work-time accounting range start is required");
            Objects.requireNonNull(to, "Work-time accounting range end is required");
            if (to.isBefore(from)) {
                throw new IllegalArgumentException(
                        "Work-time accounting range is invalid"
                );
            }
            slices = List.copyOf(Objects.requireNonNull(
                    slices,
                    "Work-time accounting slices are required"
            ));
            if (ready == (blockingReason != null)) {
                throw new IllegalArgumentException(
                        "Work-time accounting range state is invalid"
                );
            }
            if (!ready && !slices.isEmpty()) {
                throw new IllegalArgumentException(
                        "Blocked work-time accounting range cannot expose partial slices"
                );
            }
            if (ready && slices.isEmpty()) {
                throw new IllegalArgumentException(
                        "Ready work-time accounting range requires slices"
                );
            }
        }

        public static RangeResolution ready(
                LocalDate from,
                LocalDate to,
                List<ModeSlice> slices
        ) {
            return new RangeResolution(from, to, true, null, slices);
        }

        public static RangeResolution blocked(
                LocalDate from,
                LocalDate to,
                String reason
        ) {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("Blocking reason is required");
            }
            return new RangeResolution(from, to, false, reason, List.of());
        }
    }
}
