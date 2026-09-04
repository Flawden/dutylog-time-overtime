package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AppUser;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StatutoryPublicHolidayAuthorityServiceTest {
    private static final String DATASET_SHA =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String PACK_SHA =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    WorkJurisdictionHistoryService jurisdiction;
    RegionalStatutoryHolidayDatasetService regional;
    StatutoryPublicHolidayAuthorityService service;
    AppUser owner;

    @BeforeEach
    void setUp() {
        jurisdiction = mock(WorkJurisdictionHistoryService.class);
        regional = mock(RegionalStatutoryHolidayDatasetService.class);
        service = new StatutoryPublicHolidayAuthorityService(jurisdiction, regional);
        owner = new AppUser("statutory-holiday-owner", "{noop}irrelevant");
    }

    @Test
    void missingJurisdictionPropagatesFailClosed() {
        LocalDate date = LocalDate.of(2026,5,9);
        String blocker = WorkJurisdictionHistoryService.JURISDICTION_FACT_MISSING + ":" + date;
        when(jurisdiction.resolveAt(owner,date)).thenReturn(WorkJurisdictionHistoryService.Resolution.blocked(date,blocker));
        var result = service.resolve(owner,date);
        assertFalse(result.ready());
        assertEquals(blocker,result.blockingReason());
        verifyNoInteractions(regional);
    }

    @Test
    void federalHolidayResolvesWithoutRegionalDataset() {
        LocalDate date = LocalDate.of(2026,5,9);
        when(jurisdiction.resolveAt(owner,date)).thenReturn(readyJurisdiction(date,"RU",null));
        var result = service.resolve(owner,date);
        assertTrue(result.ready());
        assertTrue(result.nonWorkingPublicHoliday());
        assertEquals(RuFederalStatutoryHolidayPolicy.HolidayCode.VICTORY_DAY.name(),result.provenance().holidayCode());
        assertEquals(StatutoryPublicHolidayAuthorityService.AuthorityKind.FEDERAL_ARTICLE_112,result.provenance().authorityKind());
        verifyNoInteractions(regional);
    }

    @Test
    void federalHolidayResolvesEvenWhenRegionExists() {
        LocalDate date = LocalDate.of(2026,6,12);
        when(jurisdiction.resolveAt(owner,date)).thenReturn(readyJurisdiction(date,"RU","RU-KYA"));
        var result = service.resolve(owner,date);
        assertTrue(result.ready());
        assertEquals("RU-KYA",result.provenance().regionCode());
        verifyNoInteractions(regional);
    }

    @Test
    void defensiveUnsupportedReadyJurisdictionFailsClosed() {
        LocalDate date = LocalDate.of(2026,5,9);
        when(jurisdiction.resolveAt(owner,date)).thenReturn(readyJurisdiction(date,"DE",null));
        var result = service.resolve(owner,date);
        assertFalse(result.ready());
        assertEquals(StatutoryPublicHolidayAuthorityService.JURISDICTION_UNSUPPORTED + ":DE",result.blockingReason());
    }

    @Test
    void ordinaryDateWithoutRegionFailsClosed() {
        LocalDate date = LocalDate.of(2026,7,15);
        when(jurisdiction.resolveAt(owner,date)).thenReturn(readyJurisdiction(date,"RU",null));
        var result = service.resolve(owner,date);
        assertFalse(result.ready());
        assertEquals(StatutoryPublicHolidayAuthorityService.REGIONAL_AUTHORITY_MISSING + ":" + date,result.blockingReason());
        verifyNoInteractions(regional);
    }

    @Test
    void missingRegionalDatasetBlockerPropagatesFailClosed() {
        LocalDate date = LocalDate.of(2026,7,15);
        when(jurisdiction.resolveAt(owner,date)).thenReturn(readyJurisdiction(date,"RU","RU-KYA"));
        when(regional.resolve("RU","RU-KYA",date)).thenReturn(
                RegionalStatutoryHolidayDatasetService.Decision.unresolved(date,
                        RegionalStatutoryHolidayDatasetService.DATASET_MISSING + ":RU-KYA:" + date));
        var result = service.resolve(owner,date);
        assertFalse(result.ready());
        assertTrue(result.blockingReason().startsWith(RegionalStatutoryHolidayDatasetService.DATASET_MISSING));
    }

    @Test
    void regionalPositiveFactResolvesAsPublicHoliday() {
        LocalDate date = LocalDate.of(2026,6,24);
        when(jurisdiction.resolveAt(owner,date)).thenReturn(readyJurisdiction(date,"RU","RU-KYA"));
        when(regional.resolve("RU","RU-KYA",date)).thenReturn(regionalPositive(date));
        var result = service.resolve(owner,date);
        assertTrue(result.ready());
        assertTrue(result.nonWorkingPublicHoliday());
        assertEquals(StatutoryPublicHolidayAuthorityService.AuthorityKind.REGIONAL_DATASET,result.provenance().authorityKind());
        assertEquals("REGIONAL_RELIGIOUS_HOLIDAY",result.provenance().holidayCode());
        assertEquals(501L,result.provenance().regionalDatasetId());
        assertEquals(601L,result.provenance().regionalDateFactId());
    }

    @Test
    void completeRegionalDatasetCanProveOrdinaryDateNegative() {
        LocalDate date = LocalDate.of(2026,7,15);
        when(jurisdiction.resolveAt(owner,date)).thenReturn(readyJurisdiction(date,"RU","RU-KYA"));
        when(regional.resolve("RU","RU-KYA",date)).thenReturn(regionalNegative(date));
        var result = service.resolve(owner,date);
        assertTrue(result.ready());
        assertTrue(result.provenNotPublicHoliday());
        assertFalse(result.nonWorkingPublicHoliday());
        assertNull(result.provenance().holidayCode());
        assertNull(result.provenance().regionalDateFactId());
        assertEquals(501L,result.provenance().regionalDatasetId());
    }

    @Test
    void transferredRestDayDoesNotMasqueradeAsStatutoryHoliday() {
        LocalDate date = LocalDate.of(2026,1,9);
        when(jurisdiction.resolveAt(owner,date)).thenReturn(readyJurisdiction(date,"RU","RU-KYA"));
        when(regional.resolve("RU","RU-KYA",date)).thenReturn(regionalNegative(date));
        var result = service.resolve(owner,date);
        assertTrue(result.ready());
        assertTrue(result.provenNotPublicHoliday());
    }

    @Test
    void unsupportedLegalWindowAndResolutionInvariantsFailClosed() {
        LocalDate outside = LocalDate.of(2027,1,1);
        when(jurisdiction.resolveAt(owner,outside)).thenReturn(readyJurisdiction(outside,"RU","RU-KYA"));
        var unresolved = service.resolve(owner,outside);
        assertFalse(unresolved.ready());
        assertEquals(StatutoryPublicHolidayAuthorityService.LEGAL_WINDOW_UNSUPPORTED + ":" + outside,unresolved.blockingReason());
        verifyNoInteractions(regional);
        LocalDate date = LocalDate.of(2026,5,9);
        assertThrows(IllegalArgumentException.class,
                () -> new StatutoryPublicHolidayAuthorityService.Resolution(date,StatutoryPublicHolidayAuthorityService.Status.UNRESOLVED,null,null));
    }

    @Test
    void regionalPositiveProvenanceCarriesExactSourcePackIdentity() {
        LocalDate date = LocalDate.of(2026,6,24);
        var resolution = StatutoryPublicHolidayAuthorityService.Resolution.regionalHoliday(
                date,
                readyJurisdiction(date,"RU","RU-KYA").fact(),
                regionalPositive(date));
        assertEquals(RegionalStatutoryHolidayDatasetService.SOURCE_PACK_SCHEMA_V1,
                resolution.provenance().regionalSourcePackSchema());
        assertEquals(PACK_SHA,resolution.provenance().regionalSourcePackSha256());
        assertEquals("EXHAUSTIVE REGIONAL LEGAL REVIEW 2026",
                resolution.provenance().regionalCompletenessEvidence());
    }

    @Test
    void regionalNegativeProvenanceCarriesSameExactSourcePackIdentity() {
        LocalDate date = LocalDate.of(2026,7,15);
        var resolution = StatutoryPublicHolidayAuthorityService.Resolution.regionalNotHoliday(
                date,
                readyJurisdiction(date,"RU","RU-KYA").fact(),
                regionalNegative(date));
        assertTrue(resolution.provenNotPublicHoliday());
        assertEquals(PACK_SHA,resolution.provenance().regionalSourcePackSha256());
        assertNull(resolution.provenance().regionalDateFactId());
    }

    private RegionalStatutoryHolidayDatasetService.Decision regionalPositive(LocalDate date) {
        return new RegionalStatutoryHolidayDatasetService.Decision(
                date,
                RegionalStatutoryHolidayDatasetService.Status.NON_WORKING_PUBLIC_HOLIDAY,
                null,
                regionalProvenance(false),
                new RegionalStatutoryHolidayDatasetService.HolidayFact(
                        601L,"REGIONAL_RELIGIOUS_HOLIDAY","Regional holiday","REGIONAL HOLIDAY LEGAL BASIS","REGIONAL HOLIDAY SOURCE"));
    }
    private RegionalStatutoryHolidayDatasetService.Decision regionalNegative(LocalDate date) {
        return RegionalStatutoryHolidayDatasetService.Decision.notHoliday(date,regionalProvenance(true));
    }
    private RegionalStatutoryHolidayDatasetService.DatasetProvenance regionalProvenance(boolean complete) {
        return new RegionalStatutoryHolidayDatasetService.DatasetProvenance(
                501L,"RU","RU-KYA",LocalDate.of(2026,1,1),LocalDate.of(2026,12,31),
                "RU_KYA_2026_V1","REGIONAL LEGAL BASIS","REVISION-2026-01","REGIONAL DATASET SOURCE",complete,
                DATASET_SHA,
                RegionalStatutoryHolidayDatasetService.SOURCE_PACK_SCHEMA_V1,
                PACK_SHA,
                "EXHAUSTIVE REGIONAL LEGAL REVIEW 2026");
    }
    private WorkJurisdictionHistoryService.Resolution readyJurisdiction(LocalDate date,String jurisdictionCode,String regionCode) {
        return WorkJurisdictionHistoryService.Resolution.ready(date,
                new WorkJurisdictionHistoryService.JurisdictionFact(91L,LocalDate.of(2026,1,1),jurisdictionCode,regionCode));
    }
}
