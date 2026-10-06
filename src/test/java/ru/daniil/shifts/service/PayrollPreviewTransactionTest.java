package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import ru.daniil.shifts.model.*;
import ru.daniil.shifts.repo.*;
import java.time.*;
import java.util.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;
import ru.daniil.shifts.service.exception.PayrollPricingUnavailableException;
import static org.junit.jupiter.api.Assertions.*;
import static ru.daniil.shifts.dto.Dtos.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Requests own and finish their transactions: an outer test transaction would hide rollback-only failures. */
@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:payroll-preview-transaction;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class PayrollPreviewTransactionTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ShiftTypeRepository shifts;
    @Autowired DayEntryService days;
    @Autowired ActualWorkService actual;
    @Autowired PayrollService payroll;
    @Autowired PayPricingConfigurationService pricing;
    @Autowired OrdinaryWorkPremiumPricingService ordinary;
    @Autowired PlatformTransactionManager transactions;
    static final LocalDate DATE=LocalDate.of(2026,5,9);

    AppUser owner() {
        var owner=new AppUser("tx-"+UUID.randomUUID().toString().replace("-",""),"unused");
        owner.setWorkTimezone("UTC");owner.setOnboardingCompleted(true);
        owner=users.saveAndFlush(owner);
        var shift=shifts.saveAndFlush(new ShiftType(owner,"Night fixture",1,"#123456",false,
                LocalTime.of(22,0),LocalTime.of(23,0),0,1.0));
        days.upsert(owner,DATE.toString(),new DayUpsertRequest(shift.getId(),null,null,null,null));
        actual.create(owner,new ActualWorkIntervalRequest(DATE.toString(),null,"22:00","23:00",0,"Transaction fixture"));
        return owner;
    }
    void rules(AppUser owner) {
        pricing.upsert(owner,"2026-05-01",new PayPricingTermRequest(List.of(
                new PayPricingRuleRequest("NIGHT_FIXTURE","NIGHT",500,0,null,null))));
    }
    void readable(AppUser owner,String reason) throws Exception {
        mvc.perform(get("/api/v1/payroll/periods/2026-05").with(user(owner.getUsername())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canCalculate").value(false))
                .andExpect(jsonPath("$.preview.ordinaryPremiumPricingBlockingReason").value(reason));
    }
    @Test void missingPricingRulesReturnsBlockedPreviewAndCommitsReadRequest() throws Exception {
        var owner=owner();
        payroll.upsertCompensationTerm(owner,"2026-05",new PayrollCompensationTermRequest("HOURLY","RUB",50000L,null));
        readable(owner,"PAY_PRICING_RULES_REQUIRED");
        readable(owner,"PAY_PRICING_RULES_REQUIRED");
    }
    @Test void missingHistoricalCompensationReturnsBlockedPreview() throws Exception {
        var owner=owner();rules(owner);
        readable(owner,"PAYROLL_COMPENSATION_REQUIRED");
    }
    @Test void incompleteSalaryScheduleReturnsBlockedPreview() throws Exception {
        var owner=owner();rules(owner);
        payroll.upsertCompensationTerm(owner,"2026-05",new PayrollCompensationTermRequest("SALARY","RUB",null,10000000L));
        readable(owner,"PAYROLL_PRODUCTION_NORM_INCOMPLETE");
    }
    @Test void strictCallerStillThrowsAndRollsBackItsWrites() {
        var owner=owner();
        var before=owner.getDisplayName();
        assertThrows(PayrollPricingUnavailableException.class, () ->
                new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                    var current=users.findById(owner.getId()).orElseThrow();
                    current.setDisplayName("Must roll back");users.saveAndFlush(current);
                    ordinary.priceMonth(owner,YearMonth.of(2026,5));
                }));
        assertEquals(before,users.findById(owner.getId()).orElseThrow().getDisplayName());
    }
    @Test void unexpectedProgrammingErrorStillMarksJoinedTransactionForRollback() {
        var owner=owner();
        var before=owner.getDisplayName();
        assertThrows(UnexpectedRollbackException.class, () ->
                new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                    var current=users.findById(owner.getId()).orElseThrow();
                    current.setDisplayName("Must roll back");users.saveAndFlush(current);
                    try { ordinary.priceMonth(null,YearMonth.of(2026,5)); }
                    catch (IllegalArgumentException expected) { /* root commit must still fail */ }
                }));
        assertEquals(before,users.findById(owner.getId()).orElseThrow().getDisplayName());
    }
}
