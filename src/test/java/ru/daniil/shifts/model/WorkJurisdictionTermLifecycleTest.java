package ru.daniil.shifts.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class WorkJurisdictionTermLifecycleTest {

    @Test
    void lifecycleAcceptsValidFactsAndRepairsMissingCreatedAt() {
        WorkJurisdictionTerm repaired = valid();
        setField(repaired, "createdAt", null);

        repaired.onCreate();

        assertNotNull(repaired.getCreatedAt());
        assertNotNull(repaired.getUpdatedAt());

        WorkJurisdictionTerm ordinary = valid();
        ordinary.setJurisdiction("RU", "RU-MOW");
        ordinary.onCreate();
        ordinary.onUpdate();

        assertEquals("RU", ordinary.getJurisdictionCode());
        assertEquals("RU-MOW", ordinary.getRegionCode());
        assertNotNull(ordinary.getOwner());
        assertNotNull(ordinary.getEffectiveFrom());
    }

    @Test
    void lifecycleRejectsMissingOwner() {
        WorkJurisdictionTerm term = valid();
        setField(term, "owner", null);

        assertThrows(IllegalStateException.class, term::onCreate);
    }

    @Test
    void lifecycleRejectsMissingEffectiveDate() {
        WorkJurisdictionTerm term = valid();
        setField(term, "effectiveFrom", null);

        assertThrows(IllegalStateException.class, term::onCreate);
    }

    @Test
    void lifecycleRejectsBlankJurisdiction() {
        WorkJurisdictionTerm term = valid();
        term.setJurisdiction(" ", null);

        assertThrows(IllegalStateException.class, term::onCreate);
    }

    @Test
    void lifecycleRejectsBlankRegion() {
        WorkJurisdictionTerm term = valid();
        term.setJurisdiction("RU", " ");

        assertThrows(IllegalStateException.class, term::onUpdate);
    }

    private WorkJurisdictionTerm valid() {
        return new WorkJurisdictionTerm(
                new AppUser(
                        "jurisdiction-term-coverage-owner",
                        "{noop}irrelevant"
                ),
                LocalDate.of(2026, 1, 1),
                "RU",
                null
        );
    }

    private void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }
}
