package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import ru.daniil.shifts.model.*;
import ru.daniil.shifts.model.Article153ComponentAuthority.Decision;
import ru.daniil.shifts.model.Article153ComponentAuthority.SourceKind;
import ru.daniil.shifts.repo.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Article153ComponentAuthorityValidationTest {
    final CompensationComponentVersionRepository versions = mock(CompensationComponentVersionRepository.class);
    final CompensationComponentResolverService resolver = mock(CompensationComponentResolverService.class);
    final Article153ComponentAuthorityRepository authorities = mock(Article153ComponentAuthorityRepository.class);
    final Article153ComponentAuthorityService service = new Article153ComponentAuthorityService(versions,resolver,authorities);
    AppUser owner; CompensationComponentVersion version;
    final LocalDate date = LocalDate.of(2026,5,9);
    @BeforeEach void setup() {
        owner = new AppUser("a","b"); ReflectionTestUtils.setField(owner,"id",1L);
        var component = new CompensationComponent(owner); ReflectionTestUtils.setField(component,"id",2L);
        version = new CompensationComponentVersion(component, LocalDate.of(2026,1,1),"a",
                CompensationComponentVersion.CalculationType.FIXED_AMOUNT,null,null,1L,"RUB",true);
        ReflectionTestUtils.setField(version,"id",3L);
        when(resolver.resolve(owner, YearMonth.from(date))).thenReturn(List.of(version));
    }
    @Test void nullAndTransientInputsFailBeforeRepositoryAccess() {
        assertThrows(NullPointerException.class, () -> service.resolve(null,date));
        assertThrows(NullPointerException.class, () -> service.resolve(owner,null));
        assertThrows(IllegalArgumentException.class, () -> service.resolve(new AppUser("a","b"),date));
        for(Long id: Arrays.asList(null,0L,-1L)) {
            assertThrows(IllegalArgumentException.class, () -> service.certify(owner,id,Decision.INCLUDE,
                    SourceKind.LOCAL_NORMATIVE_ACT,"ref","rev","basis"));
        }
        verifyNoInteractions(versions,authorities);
    }
    @ParameterizedTest @ValueSource(strings={"foreignOwner","nullEffective","midMonth","future","missingVersion","missingComponent"})
    void malformedEffectiveIdentityCannotProduceReadyResult(String field) {
        switch(field) {
            case "foreignOwner" -> ReflectionTestUtils.setField(owner,"id",9L);
            case "nullEffective" -> ReflectionTestUtils.setField(version,"effectiveFrom",null);
            case "midMonth" -> ReflectionTestUtils.setField(version,"effectiveFrom",LocalDate.of(2026,1,2));
            case "future" -> ReflectionTestUtils.setField(version,"effectiveFrom",LocalDate.of(2026,6,1));
            case "missingVersion" -> ReflectionTestUtils.setField(version,"id",null);
            case "missingComponent" -> ReflectionTestUtils.setField(version.getComponent(),"id",null);
        }
        AppUser caller=owner;
        if (field.equals("foreignOwner")) {
            caller=new AppUser("c","d");ReflectionTestUtils.setField(caller,"id",1L);
            when(resolver.resolve(caller,YearMonth.from(date))).thenReturn(List.of(version));
        }
        AppUser requestOwner=caller;
        assertThrows(RuntimeException.class, () -> service.resolve(requestOwner,date));
    }
    @Test void duplicateEffectiveComponentFailsClosed() {
        when(resolver.resolve(owner,YearMonth.from(date))).thenReturn(List.of(version,version));
        ReflectionTestUtils.setField(version,"enabled",false);
        assertThrows(IllegalStateException.class, () -> service.resolve(owner,date));
    }
    @Test void mismatchedCertificationQueryCannotCertifyWrongVersion() {
        when(versions.findOwnedForArticle153Certification(owner,4L)).thenReturn(Optional.of(version));
        assertThrows(IllegalStateException.class, () -> service.certify(owner,4L,Decision.INCLUDE,
                SourceKind.LOCAL_NORMATIVE_ACT,"ref","rev","basis"));
    }
    @Test void wrongPersistedAuthorityVersionIsRejected() {
        var other = mock(CompensationComponentVersion.class); when(other.getId()).thenReturn(99L);
        var row = new Article153ComponentAuthority(other,Decision.INCLUDE,SourceKind.LOCAL_NORMATIVE_ACT,
                "ref","rev","basis","a".repeat(64),Instant.now());
        ReflectionTestUtils.setField(row,"id",4L);
        when(authorities.findByComponentVersion(version)).thenReturn(Optional.of(row));
        assertThrows(IllegalStateException.class, () -> service.resolve(owner,date));
    }
    @Test void matchingDisabledPersistedAuthorityCannotProduceActiveFact() {
        ReflectionTestUtils.setField(version,"enabled",false);
        var row = new Article153ComponentAuthority(version,Decision.INCLUDE,SourceKind.LOCAL_NORMATIVE_ACT,
                "ref","rev","basis",Article153ComponentAuthorityService.fingerprint(version),Instant.now());
        ReflectionTestUtils.setField(row,"id",4L);
        when(authorities.findByComponentVersion(version)).thenReturn(Optional.of(row));
        assertTrue(service.resolve(owner,date).components().isEmpty());
    }
    @Test void fingerprintIncludesStableAndEffectiveIdentity() {
        String initial=Article153ComponentAuthorityService.fingerprint(version);
        ReflectionTestUtils.setField(version,"id",4L);
        assertNotEquals(initial,Article153ComponentAuthorityService.fingerprint(version));
        ReflectionTestUtils.setField(version,"id",3L);
        ReflectionTestUtils.setField(version,"effectiveFrom",LocalDate.of(2026,2,1));
        assertNotEquals(initial,Article153ComponentAuthorityService.fingerprint(version));
    }
    @Test void sourceLimitsAndFingerprintAreValidated() {
        for(String text: Arrays.asList(null,""," ","x".repeat(501))) {
            assertThrows(IllegalArgumentException.class, () -> new Article153ComponentAuthority(version,
                    Decision.EXCLUDE,SourceKind.LOCAL_NORMATIVE_ACT,text,"r","b","a".repeat(64),Instant.now()));
        }
        assertThrows(IllegalArgumentException.class, () -> new Article153ComponentAuthority(version,
                Decision.EXCLUDE,SourceKind.LOCAL_NORMATIVE_ACT,"r","x".repeat(161),"b","a".repeat(64),Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> new Article153ComponentAuthority(version,
                Decision.EXCLUDE,SourceKind.LOCAL_NORMATIVE_ACT,"r","v","x".repeat(2001),"a".repeat(64),Instant.now()));
        for(String hash: List.of("short","A".repeat(64),"z".repeat(64))) {
            assertThrows(IllegalArgumentException.class, () -> new Article153ComponentAuthority(version,
                    Decision.INCLUDE,SourceKind.EMPLOYMENT_CONTRACT,"r","v","b",hash,Instant.now()));
        }
        var row=new Article153ComponentAuthority(version,Decision.INCLUDE,SourceKind.EMPLOYMENT_CONTRACT,
                "r","v","b","a".repeat(64),Instant.now());
        ReflectionTestUtils.setField(row,"componentFingerprint",null);
        assertThrows(IllegalArgumentException.class,row::validate);
    }
}
