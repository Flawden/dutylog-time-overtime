package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.Article153RestDayElection;
import ru.daniil.shifts.repo.Article153RestDayElectionRepository;
import ru.daniil.shifts.service.HolidayPayQualifiedCauseAuthorityService.QualifiedPiece;
import ru.daniil.shifts.service.OrdinaryWorkPremiumSourceService.SourceKind;
import ru.daniil.shifts.service.OrdinaryWorkPremiumSourceService.SourcePiece;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * P1B3B2B persistent authority for the employee's Article 153 election of
 * another rest day for one exact P1B3A-qualified work event/date.
 *
 * <p>The source event is always derived from
 * {@link HolidayPayQualifiedCauseAuthorityService}; this service never
 * reclassifies holiday/rest-day work independently.</p>
 *
 * <p>Existing overtime credits/usages/allocations are deliberately not used.
 * Their "отгул" semantics represent consumption of a time bank, not the legal
 * Article 153 election.</p>
 *
 * <p>Revocation in this authority is correction-only. A revoked row remains in
 * history and future economic resolution fails closed; this stage does not
 * decide whether a legal election may later be replaced, consumed, settled at
 * termination, or mapped to a scheduled rest day.</p>
 */
@Service
public class Article153RestDayElectionAuthorityService {
    public static final String QUALIFIED_MONTH_BLOCKED =
            "ARTICLE153_REST_DAY_ELECTION_QUALIFIED_MONTH_BLOCKED";
    public static final String QUALIFIED_SOURCE_NOT_FOUND =
            "ARTICLE153_REST_DAY_ELECTION_QUALIFIED_SOURCE_NOT_FOUND";
    public static final String SOURCE_EVENT_CAUSE_AMBIGUOUS =
            "ARTICLE153_REST_DAY_ELECTION_SOURCE_EVENT_CAUSE_AMBIGUOUS";
    public static final String SOURCE_EVENT_TIMEZONE_AMBIGUOUS =
            "ARTICLE153_REST_DAY_ELECTION_SOURCE_EVENT_TIMEZONE_AMBIGUOUS";
    public static final String SOURCE_CHANGED_AFTER_ELECTION =
            "ARTICLE153_REST_DAY_ELECTION_SOURCE_CHANGED";
    public static final String REVOKED_REQUIRES_REVIEW =
            "ARTICLE153_REST_DAY_ELECTION_REVOKED_REQUIRES_REVIEW";
    public static final String REELECTION_NOT_AUTHORIZED =
            "ARTICLE153_REST_DAY_ELECTION_REELECTION_NOT_AUTHORIZED";
    public static final String REVOCATION_REASON_REQUIRED =
            "ARTICLE153_REST_DAY_ELECTION_REVOCATION_REASON_REQUIRED";

    private static final String FINGERPRINT_SCHEMA =
            "ARTICLE153_REST_DAY_ELECTION_SOURCE_EVENT_V1";

    private final HolidayPayQualifiedCauseAuthorityService qualifiedAuthority;
    private final Article153RestDayElectionRepository elections;

    public Article153RestDayElectionAuthorityService(
            HolidayPayQualifiedCauseAuthorityService qualifiedAuthority,
            Article153RestDayElectionRepository elections
    ) {
        this.qualifiedAuthority = Objects.requireNonNull(
                qualifiedAuthority,
                "Article 153 rest-day election requires P1B3A qualified authority"
        );
        this.elections = Objects.requireNonNull(
                elections,
                "Article 153 rest-day election repository is required"
        );
    }

    /**
     * Persist an employee election for one exact qualifying source event.
     * Repeating the same request is idempotent while the source fingerprint is
     * unchanged. A changed source or a previously revoked election fails closed.
     */
    @Transactional
    public ElectionFact elect(
            AppUser user,
            LocalDate workDate,
            SourceKind sourceKind,
            long sourceEntityId
    ) {
        SourceEvent event = requireCurrentEvent(
                user,
                workDate,
                sourceKind,
                sourceEntityId
        );

        Article153RestDayElection existing =
                elections.findByOwnerAndWorkDateAndSourceIdentity(
                                user,
                                workDate,
                                event.sourceIdentity()
                        )
                        .orElse(null);

        if (existing != null) {
            validatePersisted(existing);
            assertSameCurrentSource(existing, event);

            if (Article153RestDayElection.STATUS_REVOKED.equals(existing.getStatus())) {
                throw new IllegalStateException(
                        REELECTION_NOT_AUTHORIZED + ":" + event.sourceIdentity()
                );
            }

            return fact(existing);
        }

        Instant now = Instant.now();
        Article153RestDayElection saved = elections.saveAndFlush(
                new Article153RestDayElection(
                        user,
                        event.workDate(),
                        event.sourceKind().name(),
                        event.sourceIdentity(),
                        event.sourceActualWorkIntervalId(),
                        event.sourceDayEntryId(),
                        event.evidenceStart(),
                        event.evidenceEnd(),
                        event.evidenceTimezone(),
                        event.cause().name(),
                        event.qualifiedMinutes(),
                        event.fingerprint(),
                        now
                )
        );

        validatePersisted(saved);
        return fact(saved);
    }

    /**
     * Correction-only lifecycle transition. The row is retained and is never
     * deleted or converted into an overtime-bank operation.
     */
    @Transactional
    public ElectionFact revokeForCorrection(
            AppUser user,
            LocalDate workDate,
            SourceKind sourceKind,
            long sourceEntityId,
            String reason
    ) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(REVOCATION_REASON_REQUIRED);
        }

        SourceEvent event = requireCurrentEvent(
                user,
                workDate,
                sourceKind,
                sourceEntityId
        );

        Article153RestDayElection existing =
                elections.findByOwnerAndWorkDateAndSourceIdentity(
                                user,
                                workDate,
                                event.sourceIdentity()
                        )
                        .orElseThrow(() -> new IllegalArgumentException(
                                QUALIFIED_SOURCE_NOT_FOUND
                                        + ":NO_ELECTION:"
                                        + event.sourceIdentity()
                        ));

        validatePersisted(existing);
        assertSameCurrentSource(existing, event);

        if (Article153RestDayElection.STATUS_REVOKED.equals(existing.getStatus())) {
            return fact(existing);
        }

        existing.revoke(reason, Instant.now());
        Article153RestDayElection saved = elections.saveAndFlush(existing);
        validatePersisted(saved);
        return fact(saved);
    }

    /**
     * Resolve the current election state against current P1B3A source evidence.
     * NONE is a positive statement only that this table contains no active
     * other-rest-day election for the exact qualifying event. This stage does
     * not yet map NONE to payable money.
     */
    @Transactional(readOnly = true)
    public Resolution resolve(
            AppUser user,
            LocalDate workDate,
            SourceKind sourceKind,
            long sourceEntityId
    ) {
        EventResolution eventResolution = resolveCurrentEvent(
                user,
                workDate,
                sourceKind,
                sourceEntityId
        );

        if (!eventResolution.ready()) {
            return Resolution.blocked(
                    workDate,
                    sourceIdentity(sourceKind, sourceEntityId),
                    eventResolution.blockingReason()
            );
        }

        SourceEvent event = eventResolution.event();
        Article153RestDayElection existing =
                elections.findByOwnerAndWorkDateAndSourceIdentity(
                                user,
                                workDate,
                                event.sourceIdentity()
                        )
                        .orElse(null);

        if (existing == null) {
            return Resolution.none(
                    workDate,
                    event.sourceIdentity(),
                    event.fingerprint()
            );
        }

        try {
            validatePersisted(existing);
            assertSameCurrentSource(existing, event);
        } catch (RuntimeException ex) {
            return Resolution.blocked(
                    workDate,
                    event.sourceIdentity(),
                    SOURCE_CHANGED_AFTER_ELECTION + ":" + event.sourceIdentity()
            );
        }

        if (Article153RestDayElection.STATUS_REVOKED.equals(existing.getStatus())) {
            return Resolution.blocked(
                    workDate,
                    event.sourceIdentity(),
                    REVOKED_REQUIRES_REVIEW + ":" + existing.getId()
            );
        }

        return Resolution.elected(
                workDate,
                event.sourceIdentity(),
                event.fingerprint(),
                fact(existing)
        );
    }

    @Transactional(readOnly = true)
    public List<ElectionFact> history(
            AppUser user,
            LocalDate from,
            LocalDate to
    ) {
        Objects.requireNonNull(user, "Article 153 election history requires user");
        Objects.requireNonNull(from, "Article 153 election history requires from date");
        Objects.requireNonNull(to, "Article 153 election history requires to date");
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("Article 153 election history range is invalid");
        }

        List<Article153RestDayElection> rows =
                elections.findByOwnerAndWorkDateBetweenOrderByWorkDateAscIdAsc(
                        user,
                        from,
                        to
                );

        if (rows == null) {
            throw new IllegalStateException("Article 153 election repository returned null history");
        }

        return rows.stream()
                .map(row -> {
                    validatePersisted(row);
                    return fact(row);
                })
                .toList();
    }

    private SourceEvent requireCurrentEvent(
            AppUser user,
            LocalDate workDate,
            SourceKind sourceKind,
            long sourceEntityId
    ) {
        EventResolution resolved = resolveCurrentEvent(
                user,
                workDate,
                sourceKind,
                sourceEntityId
        );
        if (!resolved.ready()) {
            throw new IllegalStateException(resolved.blockingReason());
        }
        return resolved.event();
    }

    private EventResolution resolveCurrentEvent(
            AppUser user,
            LocalDate workDate,
            SourceKind sourceKind,
            long sourceEntityId
    ) {
        requireSource(user, workDate, sourceKind, sourceEntityId);

        HolidayPayQualifiedCauseAuthorityService.Resolution month =
                Objects.requireNonNull(
                        qualifiedAuthority.resolve(user, YearMonth.from(workDate)),
                        "P1B3A qualified authority cannot return null"
                );

        if (!month.ready()) {
            String blockers = month.blockers().stream()
                    .map(blocker -> blocker.kind() + "=" + blocker.reason())
                    .sorted()
                    .reduce((a, b) -> a + ";" + b)
                    .orElse("UNKNOWN");
            return EventResolution.blocked(
                    QUALIFIED_MONTH_BLOCKED + ":" + blockers
            );
        }

        String identity = sourceIdentity(sourceKind, sourceEntityId);
        List<QualifiedPiece> matching = month.pieces().stream()
                .filter(piece -> workDate.equals(piece.payrollDate()))
                .filter(piece -> sourceMatches(piece.sourcePiece(), sourceKind, sourceEntityId))
                .toList();

        if (matching.isEmpty()) {
            return EventResolution.blocked(
                    QUALIFIED_SOURCE_NOT_FOUND + ":" + workDate + ":" + identity
            );
        }

        HolidayPayQualifiedCauseAuthorityService.Cause cause =
                matching.get(0).cause();
        if (matching.stream().anyMatch(piece -> piece.cause() != cause)) {
            return EventResolution.blocked(
                    SOURCE_EVENT_CAUSE_AMBIGUOUS + ":" + identity
            );
        }

        String timezone = matching.get(0).sourcePiece().sourceEvidenceTimezone();
        if (matching.stream().anyMatch(piece ->
                !Objects.equals(timezone, piece.sourcePiece().sourceEvidenceTimezone()))) {
            return EventResolution.blocked(
                    SOURCE_EVENT_TIMEZONE_AMBIGUOUS + ":" + identity
            );
        }

        Instant start = matching.stream()
                .map(piece -> piece.sourcePiece().sourceEvidenceStartInstant())
                .min(Comparator.naturalOrder())
                .orElseThrow();
        Instant end = matching.stream()
                .map(piece -> piece.sourcePiece().sourceEvidenceEndInstant())
                .max(Comparator.naturalOrder())
                .orElseThrow();
        int minutes = matching.stream()
                .mapToInt(QualifiedPiece::minutes)
                .reduce(0, Math::addExact);

        Long actualId = sourceKind == SourceKind.EXPLICIT ? sourceEntityId : null;
        Long dayEntryId = sourceKind == SourceKind.PLAN_DERIVED ? sourceEntityId : null;

        return EventResolution.ready(
                new SourceEvent(
                        workDate,
                        sourceKind,
                        identity,
                        actualId,
                        dayEntryId,
                        start,
                        end,
                        timezone,
                        cause,
                        minutes,
                        fingerprint(workDate, identity, cause, matching)
                )
        );
    }

    private boolean sourceMatches(
            SourcePiece piece,
            SourceKind sourceKind,
            long sourceEntityId
    ) {
        if (piece.sourceKind() != sourceKind) {
            return false;
        }
        return switch (sourceKind) {
            case EXPLICIT -> Objects.equals(
                    piece.sourceActualWorkIntervalId(),
                    sourceEntityId
            );
            case PLAN_DERIVED -> Objects.equals(
                    piece.sourceDayEntryId(),
                    sourceEntityId
            );
        };
    }

    private String fingerprint(
            LocalDate workDate,
            String sourceIdentity,
            HolidayPayQualifiedCauseAuthorityService.Cause cause,
            List<QualifiedPiece> pieces
    ) {
        List<QualifiedPiece> ordered = new ArrayList<>(pieces);
        ordered.sort(
                Comparator.comparing((QualifiedPiece p) -> p.sourcePiece().sourceEvidenceStartInstant())
                        .thenComparing(p -> p.sourcePiece().sourceEvidenceEndInstant())
                        .thenComparingInt(QualifiedPiece::minutes)
                        .thenComparing(p -> p.sourcePiece().night())
                        .thenComparing(p -> p.sourcePiece().holiday())
        );

        StringBuilder canonical = new StringBuilder()
                .append(FINGERPRINT_SCHEMA).append('|')
                .append(workDate).append('|')
                .append(sourceIdentity).append('|')
                .append(cause.name());

        for (QualifiedPiece piece : ordered) {
            SourcePiece source = piece.sourcePiece();
            StatutoryPublicHolidayAuthorityService.Resolution statutory =
                    piece.statutoryResolution();
            EmployeeRestDayAuthorityService.Resolution rest =
                    piece.restDayResolution();
            StatutoryPublicHolidayAuthorityService.Provenance provenance =
                    statutory.provenance();

            canonical.append("||")
                    .append(source.sourceKind()).append('|')
                    .append(token(source.sourceActualWorkIntervalId())).append('|')
                    .append(token(source.sourceDayEntryId())).append('|')
                    .append(source.sourceEvidenceStartInstant()).append('|')
                    .append(source.sourceEvidenceEndInstant()).append('|')
                    .append(source.sourceEvidenceTimezone()).append('|')
                    .append(source.minutes()).append('|')
                    .append(source.night()).append('|')
                    .append(source.holiday()).append('|')
                    .append(statutory.status()).append('|')
                    .append(provenance.jurisdictionTermId()).append('|')
                    .append(provenance.jurisdictionCode()).append('|')
                    .append(token(provenance.regionCode())).append('|')
                    .append(provenance.authorityKind()).append('|')
                    .append(provenance.legalRegime()).append('|')
                    .append(provenance.legalBasis()).append('|')
                    .append(provenance.sourceRevision()).append('|')
                    .append(provenance.sourceReference()).append('|')
                    .append(token(provenance.holidayCode())).append('|')
                    .append(token(provenance.regionalDatasetId())).append('|')
                    .append(token(provenance.regionalDatasetFingerprint())).append('|')
                    .append(token(provenance.regionalDatasetComplete())).append('|')
                    .append(token(provenance.regionalSourcePackSchema())).append('|')
                    .append(token(provenance.regionalSourcePackSha256())).append('|')
                    .append(token(provenance.regionalCompletenessEvidence())).append('|')
                    .append(token(provenance.regionalDateFactId())).append('|')
                    .append(rest.status()).append('|')
                    .append(rest.authorityKind()).append('|')
                    .append(token(rest.dayEntryId())).append('|')
                    .append(token(rest.shiftTypeId())).append('|')
                    .append(token(rest.productionCalendarDayId())).append('|')
                    .append(token(rest.sourceLayer())).append('|')
                    .append(token(rest.sourceType())).append('|')
                    .append(token(rest.sourceRef()));
        }

        return sha256(canonical.toString());
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private String token(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private void assertSameCurrentSource(
            Article153RestDayElection persisted,
            SourceEvent current
    ) {
        if (!Objects.equals(persisted.getSourceEventFingerprint(), current.fingerprint())
                || persisted.getQualifiedMinutes() != current.qualifiedMinutes()
                || !Objects.equals(persisted.getQualifiedCause(), current.cause().name())
                || !Objects.equals(persisted.getSourceEvidenceStartInstant(), current.evidenceStart())
                || !Objects.equals(persisted.getSourceEvidenceEndInstant(), current.evidenceEnd())
                || !Objects.equals(persisted.getSourceEvidenceTimezone(), current.evidenceTimezone())) {
            throw new IllegalStateException(
                    SOURCE_CHANGED_AFTER_ELECTION + ":" + current.sourceIdentity()
            );
        }
    }

    private void validatePersisted(Article153RestDayElection row) {
        if (row == null
                || row.getId() == null
                || row.getId() <= 0L
                || row.getOwner() == null
                || row.getWorkDate() == null
                || row.getSourceIdentity() == null
                || row.getSourceEventFingerprint() == null
                || !row.getSourceEventFingerprint().matches("[0-9a-f]{64}")
                || row.getQualifiedMinutes() <= 0) {
            throw new IllegalStateException(
                    "Persisted Article 153 rest-day election lacks immutable identity"
            );
        }
    }

    private ElectionFact fact(Article153RestDayElection row) {
        return new ElectionFact(
                row.getId(),
                row.getWorkDate(),
                row.getSourceKind(),
                row.getSourceIdentity(),
                row.getSourceActualWorkIntervalId(),
                row.getSourceDayEntryId(),
                row.getSourceEvidenceStartInstant(),
                row.getSourceEvidenceEndInstant(),
                row.getSourceEvidenceTimezone(),
                row.getQualifiedCause(),
                row.getQualifiedMinutes(),
                row.getSourceEventFingerprint(),
                row.getStatus(),
                row.getElectedAt(),
                row.getRevokedAt(),
                row.getRevocationReason(),
                row.getCreatedAt(),
                row.getUpdatedAt()
        );
    }

    private void requireSource(
            AppUser user,
            LocalDate workDate,
            SourceKind sourceKind,
            long sourceEntityId
    ) {
        Objects.requireNonNull(user, "Article 153 rest-day election requires user");
        Objects.requireNonNull(workDate, "Article 153 rest-day election requires work date");
        Objects.requireNonNull(sourceKind, "Article 153 rest-day election requires source kind");
        if (sourceEntityId <= 0L) {
            throw new IllegalArgumentException(
                    "Article 153 rest-day election source id must be positive"
            );
        }
    }

    private String sourceIdentity(SourceKind kind, long sourceEntityId) {
        Objects.requireNonNull(kind, "Article 153 rest-day source kind is required");
        if (sourceEntityId <= 0L) {
            throw new IllegalArgumentException("Article 153 rest-day source id must be positive");
        }
        return kind.name() + ":" + sourceEntityId;
    }

    private record SourceEvent(
            LocalDate workDate,
            SourceKind sourceKind,
            String sourceIdentity,
            Long sourceActualWorkIntervalId,
            Long sourceDayEntryId,
            Instant evidenceStart,
            Instant evidenceEnd,
            String evidenceTimezone,
            HolidayPayQualifiedCauseAuthorityService.Cause cause,
            int qualifiedMinutes,
            String fingerprint
    ) {
        private SourceEvent {
            Objects.requireNonNull(workDate);
            Objects.requireNonNull(sourceKind);
            Objects.requireNonNull(sourceIdentity);
            Objects.requireNonNull(evidenceStart);
            Objects.requireNonNull(evidenceEnd);
            Objects.requireNonNull(evidenceTimezone);
            Objects.requireNonNull(cause);
            Objects.requireNonNull(fingerprint);
            if (qualifiedMinutes <= 0 || !fingerprint.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Article 153 rest-day source event is invalid");
            }
        }
    }

    private record EventResolution(
            boolean ready,
            SourceEvent event,
            String blockingReason
    ) {
        private EventResolution {
            if (ready != (event != null) || ready == (blockingReason != null)) {
                throw new IllegalArgumentException("Article 153 source event resolution is invalid");
            }
        }

        static EventResolution ready(SourceEvent event) {
            return new EventResolution(true, Objects.requireNonNull(event), null);
        }

        static EventResolution blocked(String reason) {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("Article 153 source event blocker is required");
            }
            return new EventResolution(false, null, reason);
        }
    }

    public enum State {
        NONE,
        ELECTED,
        BLOCKED
    }

    public record ElectionFact(
            long electionId,
            LocalDate workDate,
            String sourceKind,
            String sourceIdentity,
            Long sourceActualWorkIntervalId,
            Long sourceDayEntryId,
            Instant sourceEvidenceStartInstant,
            Instant sourceEvidenceEndInstant,
            String sourceEvidenceTimezone,
            String qualifiedCause,
            int qualifiedMinutes,
            String sourceEventFingerprint,
            String status,
            Instant electedAt,
            Instant revokedAt,
            String revocationReason,
            Instant createdAt,
            Instant updatedAt
    ) {
        public ElectionFact {
            if (electionId <= 0L) {
                throw new IllegalArgumentException("Article 153 election id must be positive");
            }
            Objects.requireNonNull(workDate);
            Objects.requireNonNull(sourceKind);
            Objects.requireNonNull(sourceIdentity);
            Objects.requireNonNull(sourceEvidenceStartInstant);
            Objects.requireNonNull(sourceEvidenceEndInstant);
            Objects.requireNonNull(sourceEvidenceTimezone);
            Objects.requireNonNull(qualifiedCause);
            Objects.requireNonNull(sourceEventFingerprint);
            Objects.requireNonNull(status);
            Objects.requireNonNull(electedAt);
            Objects.requireNonNull(createdAt);
            Objects.requireNonNull(updatedAt);
        }
    }

    public record Resolution(
            LocalDate workDate,
            String sourceIdentity,
            State state,
            String currentSourceFingerprint,
            ElectionFact fact,
            String blockingReason
    ) {
        public Resolution {
            Objects.requireNonNull(workDate);
            Objects.requireNonNull(sourceIdentity);
            Objects.requireNonNull(state);
            if (state == State.BLOCKED) {
                if (blockingReason == null || blockingReason.isBlank() || fact != null) {
                    throw new IllegalArgumentException("Blocked Article 153 election resolution is invalid");
                }
            } else {
                if (blockingReason != null || currentSourceFingerprint == null
                        || !currentSourceFingerprint.matches("[0-9a-f]{64}")) {
                    throw new IllegalArgumentException("Ready Article 153 election resolution is invalid");
                }
                if ((state == State.ELECTED) != (fact != null)) {
                    throw new IllegalArgumentException("Article 153 election fact/state mismatch");
                }
            }
        }

        static Resolution none(LocalDate date, String identity, String fingerprint) {
            return new Resolution(date, identity, State.NONE, fingerprint, null, null);
        }

        static Resolution elected(
                LocalDate date,
                String identity,
                String fingerprint,
                ElectionFact fact
        ) {
            return new Resolution(date, identity, State.ELECTED, fingerprint, fact, null);
        }

        static Resolution blocked(LocalDate date, String identity, String reason) {
            return new Resolution(date, identity, State.BLOCKED, null, null, reason);
        }

        public boolean ready() {
            return state != State.BLOCKED;
        }

        public boolean otherRestDayElected() {
            return state == State.ELECTED;
        }
    }
}
