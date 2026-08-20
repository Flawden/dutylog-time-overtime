package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.PayrollCompensationComponentCreateRequest;
import ru.daniil.shifts.dto.Dtos.PayrollCompensationComponentVersionRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.UserRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class CompensationComponentConfigurationServiceTest {

    @Autowired
    CompensationComponentConfigurationService service;

    @Autowired
    UserRepository users;

    AppUser owner;

    @BeforeEach
    void setUp() {
        owner =
                users.saveAndFlush(
                        user(
                                "component-owner"
                        )
                );
    }

    @Test
    void customFixedComponentNameIsOpaqueUserTextAndHistoryIsPreserved() {
        var created =
                service.create(
                        owner,
                        new PayrollCompensationComponentCreateRequest(
                                "2026-09",
                                fixed(
                                        "Премия за выживание после ночной смены",
                                        50_000L,
                                        "rub",
                                        true
                                )
                        )
                );

        assertNotNull(
                created.componentId()
        );
        assertNotNull(
                created.versionId()
        );
        assertEquals(
                "Премия за выживание после ночной смены",
                created.displayName()
        );
        assertEquals(
                "FIXED_AMOUNT",
                created.calculationType()
        );
        assertEquals(
                50_000L,
                created.amountMinor()
        );
        assertEquals(
                "RUB",
                created.currencyCode()
        );

        var history =
                service.history(
                        owner
                );

        assertEquals(
                1,
                history.size()
        );
        assertEquals(
                created.componentId(),
                history.get(0).componentId()
        );
    }

    @Test
    void resolverUsesLatestVersionNotAfterRequestedMonth() {
        var created =
                service.create(
                        owner,
                        new PayrollCompensationComponentCreateRequest(
                                "2026-08",
                                percent(
                                        "Вредность 4%",
                                        "EARNED_BASE_PAY",
                                        400,
                                        true
                                )
                        )
                );

        service.upsertVersion(
                owner,
                created.componentId(),
                "2026-10",
                percent(
                        "Вредность 6%",
                        "EARNED_BASE_PAY",
                        600,
                        true
                )
        );

        var september =
                service.effective(
                        owner,
                        "2026-09"
                );

        assertEquals(
                1,
                september.size()
        );
        assertEquals(
                400,
                september.get(0).rateBps()
        );
        assertEquals(
                "Вредность 4%",
                september.get(0).displayName()
        );

        var october =
                service.effective(
                        owner,
                        "2026-10"
                );

        assertEquals(
                600,
                october.get(0).rateBps()
        );
        assertEquals(
                "Вредность 6%",
                october.get(0).displayName()
        );
    }

    @Test
    void disabledEffectiveVersionDoesNotEraseStableComponentHistory() {
        var created =
                service.create(
                        owner,
                        new PayrollCompensationComponentCreateRequest(
                                "2026-08",
                                percent(
                                        "За классность",
                                        "EARNED_BASE_PAY",
                                        1_000,
                                        true
                                )
                        )
                );

        service.upsertVersion(
                owner,
                created.componentId(),
                "2026-09",
                percent(
                        "За классность",
                        "EARNED_BASE_PAY",
                        1_000,
                        false
                )
        );

        var august =
                service.effective(
                        owner,
                        "2026-08"
                );

        var october =
                service.effective(
                        owner,
                        "2026-10"
                );

        assertTrue(
                august.get(0).enabled()
        );
        assertFalse(
                october.get(0).enabled()
        );
        assertEquals(
                2,
                service.history(owner)
                        .size()
        );
        assertEquals(
                created.componentId(),
                october.get(0).componentId()
        );
    }

    @Test
    void ownerCannotMutateAnotherUsersStableComponent() {
        AppUser stranger =
                users.saveAndFlush(
                        user(
                                "component-stranger"
                        )
                );

        var foreign =
                service.create(
                        stranger,
                        new PayrollCompensationComponentCreateRequest(
                                "2026-08",
                                percent(
                                        "Чужая надбавка",
                                        "EARNED_BASE_PAY",
                                        400,
                                        true
                                )
                        )
                );

        ApiException error =
                assertThrows(
                        ApiException.class,
                        () ->
                                service.upsertVersion(
                                        owner,
                                        foreign.componentId(),
                                        "2026-09",
                                        percent(
                                                "Не должно сохраниться",
                                                "EARNED_BASE_PAY",
                                                500,
                                                true
                                        )
                                )
                );

        assertEquals(
                404,
                error.getStatus()
                        .value()
        );
    }

    @Test
    void invalidCrossFieldShapeAndInvalidMonthFailWithStableCode() {
        ApiException shape =
                assertThrows(
                        ApiException.class,
                        () ->
                                service.create(
                                        owner,
                                        new PayrollCompensationComponentCreateRequest(
                                                "2026-08",
                                                new PayrollCompensationComponentVersionRequest(
                                                        "Сломанная",
                                                        "PERCENT_OF_BASE",
                                                        null,
                                                        400,
                                                        null,
                                                        null,
                                                        true
                                                )
                                        )
                                )
                );

        assertEquals(
                CompensationComponentConfigurationService.INVALID_CODE,
                shape.getCode()
        );
        assertEquals(
                400,
                shape.getStatus().value()
        );

        ApiException month =
                assertThrows(
                        ApiException.class,
                        () ->
                                service.effective(
                                        owner,
                                        "2026-13"
                                )
                );

        assertEquals(
                CompensationComponentConfigurationService.INVALID_CODE,
                month.getCode()
        );
        assertEquals(
                400,
                month.getStatus().value()
        );
    }

    private PayrollCompensationComponentVersionRequest fixed(
            String name,
            long amount,
            String currency,
            boolean enabled
    ) {
        return new PayrollCompensationComponentVersionRequest(
                name,
                "FIXED_AMOUNT",
                null,
                null,
                amount,
                currency,
                enabled
        );
    }

    private PayrollCompensationComponentVersionRequest percent(
            String name,
            String base,
            int rateBps,
            boolean enabled
    ) {
        return new PayrollCompensationComponentVersionRequest(
                name,
                "PERCENT_OF_BASE",
                base,
                rateBps,
                null,
                null,
                enabled
        );
    }

    private AppUser user(
            String prefix
    ) {
        return new AppUser(
                prefix
                        + "-"
                        + UUID.randomUUID()
                                .toString()
                                .substring(
                                        0,
                                        12
                                ),
                "{noop}unused"
        );
    }
}
