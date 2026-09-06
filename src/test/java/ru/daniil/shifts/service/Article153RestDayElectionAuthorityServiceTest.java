package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.Article153RestDayElection;
import ru.daniil.shifts.model.PayrollQualifiedQuantity;
import ru.daniil.shifts.repo.Article153RestDayElectionRepository;
import ru.daniil.shifts.service.HolidayPayQualifiedCauseAuthorityService.BlockerKind;
import ru.daniil.shifts.service.HolidayPayQualifiedCauseAuthorityService.BlockingDay;
import ru.daniil.shifts.service.HolidayPayQualifiedCauseAuthorityService.Cause;
import ru.daniil.shifts.service.HolidayPayQualifiedCauseAuthorityService.QualifiedPiece;
import ru.daniil.shifts.service.OrdinaryWorkPremiumSourceService.SourceKind;
import ru.daniil.shifts.service.OrdinaryWorkPremiumSourceService.SourcePiece;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class Article153RestDayElectionAuthorityServiceTest {
    private HolidayPayQualifiedCauseAuthorityService qualifiedAuthority;
    private Article153RestDayElectionRepository elections;
    private Article153RestDayElectionAuthorityService service;
    private AppUser user;
    private AtomicLong ids;
    private AtomicReference<Article153RestDayElection> lastSaved;

    @BeforeEach
    void setUp() {
        qualifiedAuthority = mock(HolidayPayQualifiedCauseAuthorityService.class);
        elections = mock(Article153RestDayElectionRepository.class);
        user = mock(AppUser.class);
        service = new Article153RestDayElectionAuthorityService(qualifiedAuthority, elections);
        ids = new AtomicLong(100L);
        lastSaved = new AtomicReference<>();

        when(elections.saveAndFlush(any(Article153RestDayElection.class)))
                .thenAnswer(invocation -> {
                    Article153RestDayElection row = invocation.getArgument(0);
                    if (row.getId() == null) {
                        setId(row, ids.incrementAndGet());
                    }
                    lastSaved.set(row);
                    return row;
                });
    }

    @Test
    void resolveWithoutElectionReturnsNone() {
        LocalDate date = LocalDate.of(2026, 1, 1);
        stubReady(date, explicit(date, 41L, Instant.parse("2026-01-01T07:00:00Z"), 60));
        when(elections.findByOwnerAndWorkDateAndSourceIdentity(user, date, "EXPLICIT:41"))
                .thenReturn(Optional.empty());

        var result = service.resolve(user, date, SourceKind.EXPLICIT, 41L);

        assertTrue(result.ready());
        assertEquals(Article153RestDayElectionAuthorityService.State.NONE, result.state());
        assertFalse(result.otherRestDayElected());
        assertNull(result.fact());
        assertNotNull(result.currentSourceFingerprint());
    }

    @Test
    void electExplicitPersistsSourceLockedFact() {
        LocalDate date = LocalDate.of(2026, 2, 23);
        Instant start = Instant.parse("2026-02-23T06:00:00Z");
        stubReady(date, explicit(date, 51L, start, 120));
        when(elections.findByOwnerAndWorkDateAndSourceIdentity(user, date, "EXPLICIT:51"))
                .thenReturn(Optional.empty());

        var fact = service.elect(user, date, SourceKind.EXPLICIT, 51L);

        assertEquals("EXPLICIT:51", fact.sourceIdentity());
        assertEquals(51L, fact.sourceActualWorkIntervalId());
        assertNull(fact.sourceDayEntryId());
        assertEquals(120, fact.qualifiedMinutes());
        assertEquals("PUBLIC_HOLIDAY", fact.qualifiedCause());
        assertEquals(Article153RestDayElection.STATUS_ELECTED, fact.status());
        assertTrue(fact.sourceEventFingerprint().matches("[0-9a-f]{64}"));
        verify(elections).saveAndFlush(any(Article153RestDayElection.class));
    }

    @Test
    void electPlanDerivedPersistsDayEntryIdentity() {
        LocalDate date = LocalDate.of(2026, 3, 8);
        stubReady(date, plan(date, 77L, Instant.parse("2026-03-08T05:00:00Z"), 90));
        when(elections.findByOwnerAndWorkDateAndSourceIdentity(user, date, "PLAN_DERIVED:77"))
                .thenReturn(Optional.empty());

        var fact = service.elect(user, date, SourceKind.PLAN_DERIVED, 77L);

        assertEquals("PLAN_DERIVED:77", fact.sourceIdentity());
        assertNull(fact.sourceActualWorkIntervalId());
        assertEquals(77L, fact.sourceDayEntryId());
        assertEquals(90, fact.qualifiedMinutes());
    }

    @Test
    void electAggregatesMultiplePiecesOfSameEvent() {
        LocalDate date = LocalDate.of(2026, 5, 9);
        SourcePiece first = explicit(date, 81L, Instant.parse("2026-05-09T06:00:00Z"), 30);
        SourcePiece second = explicit(date, 81L, Instant.parse("2026-05-09T06:30:00Z"), 45);
        stubReady(date, first, second);
        when(elections.findByOwnerAndWorkDateAndSourceIdentity(user, date, "EXPLICIT:81"))
                .thenReturn(Optional.empty());

        var fact = service.elect(user, date, SourceKind.EXPLICIT, 81L);

        assertEquals(75, fact.qualifiedMinutes());
        assertEquals(first.sourceEvidenceStartInstant(), fact.sourceEvidenceStartInstant());
        assertEquals(second.sourceEvidenceEndInstant(), fact.sourceEvidenceEndInstant());
    }

    @Test
    void repeatedElectIsIdempotentWhenFingerprintMatches() {
        LocalDate date = LocalDate.of(2026, 6, 12);
        stubReady(date, explicit(date, 91L, Instant.parse("2026-06-12T06:00:00Z"), 60));
        when(elections.findByOwnerAndWorkDateAndSourceIdentity(user, date, "EXPLICIT:91"))
                .thenReturn(Optional.empty());

        var first = service.elect(user, date, SourceKind.EXPLICIT, 91L);
        Article153RestDayElection persisted = lastSaved.get();
        when(elections.findByOwnerAndWorkDateAndSourceIdentity(user, date, "EXPLICIT:91"))
                .thenReturn(Optional.of(persisted));

        var second = service.elect(user, date, SourceKind.EXPLICIT, 91L);

        assertEquals(first.electionId(), second.electionId());
        assertEquals(first.sourceEventFingerprint(), second.sourceEventFingerprint());
        verify(elections, times(1)).saveAndFlush(any(Article153RestDayElection.class));
    }

    @Test
    void changedSourceFingerprintFailsClosed() {
        LocalDate date = LocalDate.of(2026, 7, 1);
        stubReady(date, explicit(date, 101L, Instant.parse("2026-07-01T06:00:00Z"), 60));
        when(elections.findByOwnerAndWorkDateAndSourceIdentity(user, date, "EXPLICIT:101"))
                .thenReturn(Optional.empty());
        service.elect(user, date, SourceKind.EXPLICIT, 101L);
        Article153RestDayElection persisted = lastSaved.get();

        stubReady(date, explicit(date, 101L, Instant.parse("2026-07-01T06:00:00Z"), 90));
        when(elections.findByOwnerAndWorkDateAndSourceIdentity(user, date, "EXPLICIT:101"))
                .thenReturn(Optional.of(persisted));

        var result = service.resolve(user, date, SourceKind.EXPLICIT, 101L);

        assertFalse(result.ready());
        assertEquals(Article153RestDayElectionAuthorityService.State.BLOCKED, result.state());
        assertTrue(result.blockingReason().startsWith(
                Article153RestDayElectionAuthorityService.SOURCE_CHANGED_AFTER_ELECTION));
    }

    @Test
    void resolveActiveElectionReturnsElected() {
        LocalDate date = LocalDate.of(2026, 8, 1);
        stubReady(date, explicit(date, 111L, Instant.parse("2026-08-01T06:00:00Z"), 60));
        when(elections.findByOwnerAndWorkDateAndSourceIdentity(user, date, "EXPLICIT:111"))
                .thenReturn(Optional.empty());
        service.elect(user, date, SourceKind.EXPLICIT, 111L);
        Article153RestDayElection persisted = lastSaved.get();
        when(elections.findByOwnerAndWorkDateAndSourceIdentity(user, date, "EXPLICIT:111"))
                .thenReturn(Optional.of(persisted));

        var result = service.resolve(user, date, SourceKind.EXPLICIT, 111L);

        assertTrue(result.ready());
        assertTrue(result.otherRestDayElected());
        assertEquals(Article153RestDayElectionAuthorityService.State.ELECTED, result.state());
        assertEquals(persisted.getId(), result.fact().electionId());
    }

    @Test
    void revokeRequiresReason() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        stubReady(date, explicit(date, 121L, Instant.parse("2026-09-01T06:00:00Z"), 60));

        var ex = assertThrows(IllegalArgumentException.class, () ->
                service.revokeForCorrection(user, date, SourceKind.EXPLICIT, 121L, " "));

        assertEquals(Article153RestDayElectionAuthorityService.REVOCATION_REASON_REQUIRED, ex.getMessage());
        verifyNoInteractions(elections);
    }

    @Test
    void revokePreservesRowAndMarksCorrectionState() {
        LocalDate date = LocalDate.of(2026, 9, 2);
        stubReady(date, explicit(date, 131L, Instant.parse("2026-09-02T06:00:00Z"), 60));
        when(elections.findByOwnerAndWorkDateAndSourceIdentity(user, date, "EXPLICIT:131"))
                .thenReturn(Optional.empty());
        var elected = service.elect(user, date, SourceKind.EXPLICIT, 131L);
        Article153RestDayElection persisted = lastSaved.get();
        when(elections.findByOwnerAndWorkDateAndSourceIdentity(user, date, "EXPLICIT:131"))
                .thenReturn(Optional.of(persisted));

        var revoked = service.revokeForCorrection(
                user, date, SourceKind.EXPLICIT, 131L, "clerical correction"
        );

        assertEquals(elected.electionId(), revoked.electionId());
        assertEquals(Article153RestDayElection.STATUS_REVOKED, revoked.status());
        assertEquals("clerical correction", revoked.revocationReason());
        assertNotNull(revoked.revokedAt());
        verify(elections, times(2)).saveAndFlush(any(Article153RestDayElection.class));
    }

    @Test
    void revokedElectionBlocksResolution() {
        LocalDate date = LocalDate.of(2026, 9, 3);
        stubReady(date, explicit(date, 141L, Instant.parse("2026-09-03T06:00:00Z"), 60));
        when(elections.findByOwnerAndWorkDateAndSourceIdentity(user, date, "EXPLICIT:141"))
                .thenReturn(Optional.empty());
        service.elect(user, date, SourceKind.EXPLICIT, 141L);
        Article153RestDayElection persisted = lastSaved.get();
        when(elections.findByOwnerAndWorkDateAndSourceIdentity(user, date, "EXPLICIT:141"))
                .thenReturn(Optional.of(persisted));
        service.revokeForCorrection(user, date, SourceKind.EXPLICIT, 141L, "correction");

        var result = service.resolve(user, date, SourceKind.EXPLICIT, 141L);

        assertFalse(result.ready());
        assertTrue(result.blockingReason().startsWith(
                Article153RestDayElectionAuthorityService.REVOKED_REQUIRES_REVIEW));
    }

    @Test
    void revokedElectionCannotBeReelected() {
        LocalDate date = LocalDate.of(2026, 9, 4);
        stubReady(date, explicit(date, 151L, Instant.parse("2026-09-04T06:00:00Z"), 60));
        when(elections.findByOwnerAndWorkDateAndSourceIdentity(user, date, "EXPLICIT:151"))
                .thenReturn(Optional.empty());
        service.elect(user, date, SourceKind.EXPLICIT, 151L);
        Article153RestDayElection persisted = lastSaved.get();
        when(elections.findByOwnerAndWorkDateAndSourceIdentity(user, date, "EXPLICIT:151"))
                .thenReturn(Optional.of(persisted));
        service.revokeForCorrection(user, date, SourceKind.EXPLICIT, 151L, "correction");

        var ex = assertThrows(IllegalStateException.class, () ->
                service.elect(user, date, SourceKind.EXPLICIT, 151L));

        assertTrue(ex.getMessage().startsWith(
                Article153RestDayElectionAuthorityService.REELECTION_NOT_AUTHORIZED));
    }

    @Test
    void blockedQualifiedMonthFailsClosed() {
        LocalDate date = LocalDate.of(2026, 10, 1);
        BlockingDay blocker = new BlockingDay(date, BlockerKind.SOURCE, "missing authority");
        when(qualifiedAuthority.resolve(user, YearMonth.from(date)))
                .thenReturn(new HolidayPayQualifiedCauseAuthorityService.Resolution(
                        YearMonth.from(date), false, null, List.of(), List.of(blocker)
                ));

        var result = service.resolve(user, date, SourceKind.EXPLICIT, 161L);

        assertFalse(result.ready());
        assertTrue(result.blockingReason().startsWith(
                Article153RestDayElectionAuthorityService.QUALIFIED_MONTH_BLOCKED));
        assertThrows(IllegalStateException.class, () ->
                service.elect(user, date, SourceKind.EXPLICIT, 161L));
    }

    @Test
    void missingQualifiedSourceFailsClosed() {
        LocalDate date = LocalDate.of(2026, 11, 1);
        stubReady(date, explicit(date, 171L, Instant.parse("2026-11-01T06:00:00Z"), 60));

        var result = service.resolve(user, date, SourceKind.EXPLICIT, 999L);

        assertFalse(result.ready());
        assertTrue(result.blockingReason().startsWith(
                Article153RestDayElectionAuthorityService.QUALIFIED_SOURCE_NOT_FOUND));
    }

    @Test
    void historyReturnsPersistedAuditFactsInOrder() {
        Article153RestDayElection first = row(
                LocalDate.of(2026, 1, 2), 201L, 301L, "a".repeat(64)
        );
        Article153RestDayElection second = row(
                LocalDate.of(2026, 2, 2), 202L, 302L, "b".repeat(64)
        );
        when(elections.findByOwnerAndWorkDateBetweenOrderByWorkDateAscIdAsc(
                user,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31)
        )).thenReturn(List.of(first, second));

        var history = service.history(
                user,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31)
        );

        assertEquals(List.of(201L, 202L), history.stream().map(
                Article153RestDayElectionAuthorityService.ElectionFact::electionId).toList());
        assertEquals(List.of("EXPLICIT:301", "EXPLICIT:302"), history.stream().map(
                Article153RestDayElectionAuthorityService.ElectionFact::sourceIdentity).toList());
    }

    private void stubReady(LocalDate date, SourcePiece... sourcePieces) {
        List<QualifiedPiece> pieces = java.util.Arrays.stream(sourcePieces)
                .map(piece -> qualified(date, piece))
                .toList();
        long minutes = pieces.stream().mapToLong(QualifiedPiece::minutes).sum();
        when(qualifiedAuthority.resolve(user, YearMonth.from(date)))
                .thenReturn(new HolidayPayQualifiedCauseAuthorityService.Resolution(
                        YearMonth.from(date),
                        true,
                        PayrollQualifiedQuantity.minutes(minutes),
                        pieces,
                        List.of()
                ));
    }

    private QualifiedPiece qualified(LocalDate date, SourcePiece source) {
        var provenance = new StatutoryPublicHolidayAuthorityService.Provenance(
                1L,
                "RU",
                null,
                StatutoryPublicHolidayAuthorityService.AuthorityKind.FEDERAL_ARTICLE_112,
                "TK_RF",
                "TK_RF_ARTICLE_112",
                "TK_RF_197_FZ_RED_2026_05_25",
                "https://example.invalid/article112",
                "FEDERAL_HOLIDAY",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        var statutory = new StatutoryPublicHolidayAuthorityService.Resolution(
                date,
                StatutoryPublicHolidayAuthorityService.Status.NON_WORKING_PUBLIC_HOLIDAY,
                null,
                provenance
        );
        var rest = new EmployeeRestDayAuthorityService.Resolution(
                EmployeeRestDayAuthorityService.Status.WORKING_DAY,
                EmployeeRestDayAuthorityService.AuthorityKind.DATED_ROSTER,
                date,
                10L,
                20L,
                null,
                null,
                null,
                null,
                null
        );
        return new QualifiedPiece(date, Cause.PUBLIC_HOLIDAY, source, statutory, rest);
    }

    private SourcePiece explicit(LocalDate date, long id, Instant start, int minutes) {
        return new SourcePiece(
                date,
                SourceKind.EXPLICIT,
                id,
                null,
                start,
                start.plusSeconds(minutes * 60L),
                "Europe/Moscow",
                minutes,
                false,
                false
        );
    }

    private SourcePiece plan(LocalDate date, long id, Instant start, int minutes) {
        return new SourcePiece(
                date,
                SourceKind.PLAN_DERIVED,
                null,
                id,
                start,
                start.plusSeconds(minutes * 60L),
                "Europe/Moscow",
                minutes,
                false,
                false
        );
    }

    private Article153RestDayElection row(
            LocalDate date,
            long electionId,
            long sourceId,
            String fingerprint
    ) {
        Instant start = date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        Article153RestDayElection row = new Article153RestDayElection(
                user,
                date,
                Article153RestDayElection.SOURCE_EXPLICIT,
                "EXPLICIT:" + sourceId,
                sourceId,
                null,
                start,
                start.plusSeconds(3600),
                "UTC",
                Cause.PUBLIC_HOLIDAY.name(),
                60,
                fingerprint,
                Instant.parse("2026-01-01T00:00:00Z")
        );
        setId(row, electionId);
        return row;
    }

    private static void setId(Article153RestDayElection row, long id) {
        try {
            Field field = Article153RestDayElection.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(row, id);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }
}
