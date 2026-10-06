package ru.daniil.shifts.service;

import com.fasterxml.jackson.databind.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.*;
import ru.daniil.shifts.dto.Dtos.*;
import ru.daniil.shifts.model.*;
import ru.daniil.shifts.repo.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static ru.daniil.shifts.service.Article153RemunerationDocument.*;

/** Real roster/time/legal sources -> real pricing/freeze -> HTTP preview/history. No mocked domain beans. */
@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:article153-scenario;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional(isolation=Isolation.REPEATABLE_READ)
class Article153PayrollScenarioTest {
    static final LocalDate MONTH=LocalDate.of(2026,5,1), DATE=MONTH.withDayOfMonth(9);
    static final Source EVIDENCE=new Source(Article153ComponentAuthority.SourceKind.LOCAL_NORMATIVE_ACT,
            "TEST FIXTURE remuneration system", "fixture-v1", "Synthetic complete source review for regression testing");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired ShiftTypeRepository shifts;
    @Autowired DayEntryService days;
    @Autowired ActualWorkService actual;
    @Autowired WorkJurisdictionHistoryService jurisdiction;
    @Autowired ProductionCalendarService calendar;
    @Autowired PayPricingConfigurationService pricing;
    @Autowired Article153LocalRateAuthorityService local;
    @Autowired Article153RemunerationAuthorityService reviews;
    @Autowired Article153ComponentAuthorityService classification;
    @Autowired CompensationComponentConfigurationService components;
    @Autowired Article153PayrollIntegrationService integration;
    @Autowired PayrollService payroll;
    @Autowired LedgerIntegrityService ledger;
    @Autowired PayrollSnapshotRepository snapshots;
    @Autowired PayrollSnapshotArticle153PayableRepository payable;
    @Autowired PayrollSnapshotEarningLineRepository lines;
    @Autowired EntityManager em;
    @Autowired Article153RestDayElectionAuthorityService elections;
    @Autowired PayrollBonusSourceFactService bonusFacts;
    @Autowired PayrollRegionalCoefficientSourceFactService regionalFacts;
    AppUser owner; long actualId;
    @BeforeEach void setup() {
        owner=new AppUser("s-"+UUID.randomUUID(),"unused");owner.setWorkTimezone("UTC");owner.setOnboardingCompleted(true);owner=users.saveAndFlush(owner);
        jurisdiction.upsert(owner,MONTH,"RU",null);
        var shift=shifts.saveAndFlush(new ShiftType(owner,"Fixture night",1,"#123456",false,LocalTime.of(22,0),LocalTime.of(23,0),0,1.0));
        days.upsert(owner,DATE.toString(),new DayUpsertRequest(shift.getId(),null,null,null,null));
        actualId=actual.create(owner,new ActualWorkIntervalRequest(DATE.toString(),null,"22:00","23:00",0,"Scenario fixture")).id();
        calendar.upsertLocal(owner,DATE.toString(),new ProductionCalendarDayUpdateRequest("HOLIDAY","NONE",null,"HOLIDAY","Fixture holiday dimension"));
        payroll.upsertCompensationTerm(owner,"2026-05",new PayrollCompensationTermRequest("HOURLY","RUB",50000L,null));
        pricing.upsert(owner,MONTH.toString(),new PayPricingTermRequest(List.of(
            new PayPricingRuleRequest("HOLIDAY_FIXTURE","HOLIDAY",5000,0,null,null),
            new PayPricingRuleRequest("NIGHT_FIXTURE","NIGHT",500,0,null,null))));
        local.certify(owner,MONTH,Article153LocalRateAuthorityService.SourceKind.LOCAL_NORMATIVE_ACT,"TEST FIXTURE local tariff","fixture-v1");
    }
    void review(int expected, Rule...rules) {reviews.certify(owner,YearMonth.from(MONTH),expected,new Review("RUB",EVIDENCE,EVIDENCE,LocalRestDayRule.NOT_APPLICABLE,List.of(rules)));}
    JsonNode readPeriod() throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/payroll/periods/2026-05").with(user(owner.getUsername())))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsString());
    }
    JsonNode calculate() throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/payroll/periods/2026-05/calculate").with(user(owner.getUsername())).with(csrf()))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    @Test void reviewedRealSourcesReachHttpPreviewFrozenHistoryAndSemanticMoney() throws Exception {
        review(0);
        var open=readPeriod();assertFalse(open.path("canCalculate").asBoolean());
        var preview=open.path("preview");assertEquals(50000,preview.path("basePayMinor").asLong());
        assertEquals(52500,preview.path("ordinaryPremiumPayMinor").asLong());assertEquals(102500,preview.path("totalPayMinor").asLong());
        assertEquals("REVIEWED",preview.at("/article153/status").asText());assertEquals(60,preview.at("/article153/qualifiedMinutes").asLong());
        assertEquals(50000,preview.at("/article153/tariffPremiumMinor").asLong());assertEquals(2500,preview.at("/article153/preservedNightPremiumMinor").asLong());
        assertEquals(0,snapshots.findByOwnerAndPeriodMonthOrderByRevisionDesc(owner,MONTH).size());
        ledger.closePeriod(owner,"2026-05");var saved=calculate();assertEquals(preview.path("totalPayMinor"),saved.path("totalPayMinor"));
        assertEquals(preview.path("article153"),saved.path("article153"));assertEquals(1,saved.path("revision").asInt());
        long id=saved.path("id").asLong();assertEquals(25000,integration.load(owner,id).orElseThrow().replacedHolidayMinor());
        em.flush();em.clear();var history=readPeriod();assertEquals(saved.path("article153"),history.at("/snapshots/0/article153"));
        var semantic=lines.findBySnapshotOrderByLineIndexAsc(snapshots.findById(id).orElseThrow());
        assertEquals(50000,semantic.stream().filter(l->"HOLIDAY_PAY".equals(l.getEarningKind())).mapToLong(PayrollSnapshotEarningLine::getAmountMinor).sum());
        assertEquals(2500,semantic.stream().filter(l->"NIGHT_PREMIUM".equals(l.getEarningKind())).mapToLong(PayrollSnapshotEarningLine::getAmountMinor).sum());
    }
    @Test void includedComponentCountsMinutesOnceAndDoesNotDoubleBookHolidayPremium() throws Exception {
        var version=components.create(owner,new PayrollCompensationComponentCreateRequest("2026-05",
            new PayrollCompensationComponentVersionRequest("Fixture allowance","FIXED_AMOUNT",null,null,12500L,"RUB",null,true)));
        classification.certify(owner,version.versionId(),Article153ComponentAuthority.Decision.INCLUDE,EVIDENCE.kind(),EVIDENCE.reference(),EVIDENCE.revision(),EVIDENCE.basis());
        var v=em.find(CompensationComponentVersion.class,version.versionId());
        review(0,new Rule(v.getId(),Article153ComponentAuthorityService.fingerprint(v),"RUB",Article153RemunerationDocument.Period.HOUR,12500,60,10000,0,EVIDENCE));
        var preview=readPeriod().path("preview");assertEquals(60,preview.at("/article153/qualifiedMinutes").asLong());
        assertEquals(12500,preview.at("/article153/componentPremiumMinor").asLong());
        assertEquals(65000,preview.path("ordinaryPremiumPayMinor").asLong());assertEquals(127500,preview.path("totalPayMinor").asLong());
        ledger.closePeriod(owner,"2026-05");assertEquals(preview.path("article153"),calculate().path("article153"));
    }
    @Test void changedSettingsBlockNewRevisionButLeaveFrozenExplanationReadable() throws Exception {
        review(0);ledger.closePeriod(owner,"2026-05");var first=calculate();long id=first.path("id").asLong();
        payroll.upsertCompensationTerm(owner,"2026-05",new PayrollCompensationTermRequest("HOURLY","RUB",60000L,null));
        var period=readPeriod();assertFalse(period.path("canCalculate").asBoolean());
        assertEquals("PAYROLL_ARTICLE153_CONFIGURATION_CHANGED",period.path("blockingReason").asText());
        assertEquals("REVIEW_BLOCKED",period.at("/preview/article153/status").asText());assertTrue(period.at("/preview/article153/tariffPremiumMinor").isNull());
        assertEquals(first.path("article153"),period.at("/snapshots/0/article153"));assertEquals(first.path("totalPayMinor"),period.at("/snapshots/0/totalPayMinor"));
        mvc.perform(post("/api/v1/payroll/periods/2026-05/calculate").with(user(owner.getUsername())).with(csrf())).andExpect(status().isConflict());
        review(1);var next=calculate();assertEquals(2,next.path("revision").asInt());assertEquals(123000,next.path("totalPayMinor").asLong());
        assertNotEquals(first.path("calculationHash"),next.path("calculationHash"));
        assertEquals(first.path("article153"),json.readTree(json.writeValueAsString(integration.summary(owner,id))));
    }
    @Test void legacyMoneyIsVisibleWithoutClaimingReviewedHolidayIdentity() throws Exception {
        var p=readPeriod().path("preview");assertEquals(77500,p.path("totalPayMinor").asLong());
        assertEquals("LEGACY_UNREVIEWED",p.at("/article153/status").asText());assertTrue(p.at("/article153/qualifiedMinutes").isNull());
        ledger.closePeriod(owner,"2026-05");var s=calculate();assertEquals(p.path("article153"),s.path("article153"));assertFalse(payable.existsById(s.path("id").asLong()));
    }
    @Test void foreignAccountNeverReceivesAnotherOwnersExplanation() throws Exception {
        review(0);ledger.closePeriod(owner,"2026-05");var saved=calculate();var other=users.saveAndFlush(new AppUser("o-"+UUID.randomUUID(),"unused"));
        var response=mvc.perform(get("/api/v1/payroll/periods/2026-05").with(user(other.getUsername()))).andExpect(status().isOk()).andReturn().getResponse();
        assertEquals(0,json.readTree(response.getContentAsString()).path("snapshots").size());
        assertEquals("LEGACY_UNREVIEWED",integration.summary(other,saved.path("id").asLong()).status());
    }
    @Test void reviewedHolidayEntersRealMonthlyBonusAndRegionalBases() throws Exception {
        var monthly=components.create(owner,new PayrollCompensationComponentCreateRequest("2026-05",
            new PayrollCompensationComponentVersionRequest("Fixture monthly","PERCENT_OF_BASE","LOCAL_ELIGIBLE_EARNINGS",4000,null,null,"MONTHLY_BONUS",true)));
        var regional=components.create(owner,new PayrollCompensationComponentCreateRequest("2026-05",
            new PayrollCompensationComponentVersionRequest("Fixture regional","PERCENT_OF_BASE","LOCAL_ELIGIBLE_EARNINGS",1500,null,null,"REGIONAL_COEFFICIENT",true)));
        for(var v:List.of(monthly,regional)) classification.certify(owner,v.versionId(),Article153ComponentAuthority.Decision.EXCLUDE,EVIDENCE.kind(),EVIDENCE.reference(),EVIDENCE.revision(),EVIDENCE.basis());
        bonusFacts.create(owner,monthly.componentId(),PayrollEarningKind.MONTHLY_BONUS,DATE,DATE,41000L,"RUB");
        regionalFacts.create(owner,regional.componentId(),DATE,DATE,21525L,"RUB");review(0);
        var preview=readPeriod().path("preview");var componentLines=preview.path("compensationComponentLines");
        var byKind=new HashMap<String,JsonNode>();componentLines.forEach(l->byKind.put(l.path("earningKind").asText(),l));
        assertEquals(102500,byKind.get("MONTHLY_BONUS").path("referenceBaseMinor").asLong());
        assertEquals(41000,byKind.get("MONTHLY_BONUS").path("amountMinor").asLong());
        assertEquals(143500,byKind.get("REGIONAL_COEFFICIENT").path("referenceBaseMinor").asLong());
        assertEquals(21525,byKind.get("REGIONAL_COEFFICIENT").path("amountMinor").asLong());
        ledger.closePeriod(owner,"2026-05");assertEquals(165025,calculate().path("totalPayMinor").asLong());
    }
    @Test void realRestDayElectionRemovesHolidayPremiumAndKeepsNightMoney() throws Exception {
        elections.elect(owner,DATE,OrdinaryWorkPremiumSourceService.SourceKind.EXPLICIT,actualId);review(0);
        var preview=readPeriod().path("preview");assertEquals(0,preview.at("/article153/tariffPremiumMinor").asLong());
        assertEquals(60,preview.at("/article153/qualifiedMinutes").asLong());assertEquals(2500,preview.path("ordinaryPremiumPayMinor").asLong());
        ledger.closePeriod(owner,"2026-05");assertEquals(52500,calculate().path("totalPayMinor").asLong());
    }
    @Test void corruptFrozenEvidenceCannotTurnIntoAnUnreviewedHistoryLabel() throws Exception {
        review(0);ledger.closePeriod(owner,"2026-05");long id=calculate().path("id").asLong();
        em.createNativeQuery("update payroll_snapshot_article153_payable set payload_json='{}' where snapshot_id=:id").setParameter("id",id).executeUpdate();em.clear();
        assertThrows(IllegalStateException.class,()->integration.summary(owner,id));
    }

}
