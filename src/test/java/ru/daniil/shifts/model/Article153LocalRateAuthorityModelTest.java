package ru.daniil.shifts.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class Article153LocalRateAuthorityModelTest {
    private final AppUser user = mock(AppUser.class);
    private final PayPricingTerm term = new PayPricingTerm(user, LocalDate.of(2026, 1, 1));
    private final Instant now = Instant.parse("2026-01-15T10:15:30Z");
    private final String sha = "a".repeat(64);

    @Test
    void validConstructorTrimsSourceTextAndKeepsAuditIdentity() {
        var row = new Article153LocalRateAuthority(
                user,
                term,
                Article153LocalRateAuthority.SOURCE_LOCAL_NORMATIVE_ACT,
                "  LNA-42  ",
                "  rev-1  ",
                sha,
                now
        );

        assertSame(user, row.getOwner());
        assertSame(term, row.getPricingTerm());
        assertEquals("LNA-42", row.getSourceReference());
        assertEquals("rev-1", row.getSourceRevision());
        assertEquals(sha, row.getHolidayPolicyFingerprint());
        assertEquals(now, row.getCertifiedAt());
        assertEquals(now, row.getCreatedAt());
    }

    @Test
    void invalidSourceKindRejected() {
        assertThrows(IllegalStateException.class, () ->
                new Article153LocalRateAuthority(user, term, "OTHER", "ref", "rev", sha, now));
        assertThrows(IllegalStateException.class, () ->
                new Article153LocalRateAuthority(user, term, null, "ref", "rev", sha, now));
    }

    @Test
    void invalidSourceReferenceRejected() {
        assertThrows(IllegalStateException.class, () ->
                new Article153LocalRateAuthority(
                        user, term,
                        Article153LocalRateAuthority.SOURCE_COLLECTIVE_AGREEMENT,
                        null, "rev", sha, now));
        assertThrows(IllegalStateException.class, () ->
                new Article153LocalRateAuthority(
                        user, term,
                        Article153LocalRateAuthority.SOURCE_COLLECTIVE_AGREEMENT,
                        "   ", "rev", sha, now));
        assertThrows(IllegalStateException.class, () ->
                new Article153LocalRateAuthority(
                        user, term,
                        Article153LocalRateAuthority.SOURCE_COLLECTIVE_AGREEMENT,
                        "x".repeat(501), "rev", sha, now));
    }

    @Test
    void invalidSourceRevisionRejected() {
        assertThrows(IllegalStateException.class, () ->
                new Article153LocalRateAuthority(
                        user, term,
                        Article153LocalRateAuthority.SOURCE_EMPLOYMENT_CONTRACT,
                        "ref", null, sha, now));
        assertThrows(IllegalStateException.class, () ->
                new Article153LocalRateAuthority(
                        user, term,
                        Article153LocalRateAuthority.SOURCE_EMPLOYMENT_CONTRACT,
                        "ref", "   ", sha, now));
        assertThrows(IllegalStateException.class, () ->
                new Article153LocalRateAuthority(
                        user, term,
                        Article153LocalRateAuthority.SOURCE_EMPLOYMENT_CONTRACT,
                        "ref", "x".repeat(161), sha, now));
    }

    @Test
    void invalidFingerprintRejected() {
        assertThrows(IllegalStateException.class, () ->
                new Article153LocalRateAuthority(
                        user, term,
                        Article153LocalRateAuthority.SOURCE_LOCAL_NORMATIVE_ACT,
                        "ref", "rev", null, now));
        assertThrows(IllegalStateException.class, () ->
                new Article153LocalRateAuthority(
                        user, term,
                        Article153LocalRateAuthority.SOURCE_LOCAL_NORMATIVE_ACT,
                        "ref", "rev", "not-a-sha", now));
    }

    @Test
    void nullRequiredRelationsAndTimestampRejected() {
        assertThrows(NullPointerException.class, () ->
                new Article153LocalRateAuthority(
                        null, term,
                        Article153LocalRateAuthority.SOURCE_LOCAL_NORMATIVE_ACT,
                        "ref", "rev", sha, now));
        assertThrows(NullPointerException.class, () ->
                new Article153LocalRateAuthority(
                        user, null,
                        Article153LocalRateAuthority.SOURCE_LOCAL_NORMATIVE_ACT,
                        "ref", "rev", sha, now));
        assertThrows(NullPointerException.class, () ->
                new Article153LocalRateAuthority(
                        user, term,
                        Article153LocalRateAuthority.SOURCE_LOCAL_NORMATIVE_ACT,
                        "ref", "rev", sha, null));
    }

    @Test
    void beforeInsertFillsBothMissingAuditTimestamps() {
        Article153LocalRateAuthority row = emptyValidRow();
        set(row, "certifiedAt", null);
        set(row, "createdAt", null);

        row.beforeInsert();

        assertNotNull(row.getCertifiedAt());
        assertEquals(row.getCertifiedAt(), row.getCreatedAt());
    }

    @Test
    void beforeInsertKeepsCertifiedTimestampAndFillsCreatedAt() {
        Article153LocalRateAuthority row = emptyValidRow();
        set(row, "certifiedAt", now);
        set(row, "createdAt", null);

        row.beforeInsert();

        assertEquals(now, row.getCertifiedAt());
        assertEquals(now, row.getCreatedAt());
    }

    private Article153LocalRateAuthority emptyValidRow() {
        Article153LocalRateAuthority row = new Article153LocalRateAuthority();
        set(row, "owner", user);
        set(row, "pricingTerm", term);
        set(row, "sourceKind", Article153LocalRateAuthority.SOURCE_LOCAL_NORMATIVE_ACT);
        set(row, "sourceReference", "ref");
        set(row, "sourceRevision", "rev");
        set(row, "holidayPolicyFingerprint", sha);
        return row;
    }

    private static void set(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }
}
