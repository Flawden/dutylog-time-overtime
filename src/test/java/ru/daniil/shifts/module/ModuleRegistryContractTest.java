package ru.daniil.shifts.module;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static registry invariants shared by backend, PWA and offline sync clients. */
class ModuleRegistryContractTest {

    @Test
    void normalizationAndLookupAreCaseWhitespaceAndNullSafe() {
        assertEquals("notes", DutyLogModules.normalize("  NoTeS  "));
        assertEquals("", DutyLogModules.normalize(null));
        assertEquals(ModuleKeys.NOTES, DutyLogModules.find(" NOTES ").orElseThrow().key());
        assertTrue(DutyLogModules.find("future-module").isEmpty());
    }

    @Test
    void keysOrdersAndDependenciesFormAStableAcyclicRegistry() {
        Set<String> keys = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for (ModuleContract module : DutyLogModules.ALL) {
            assertTrue(keys.add(module.key()), "duplicate module key: " + module.key());
            assertTrue(orders.add(module.order()), "duplicate module order: " + module.order());
            for (String dependency : module.dependencies()) {
                assertTrue(DutyLogModules.knownKeys().contains(dependency),
                        module.key() + " depends on unknown " + dependency);
                assertFalse(dependency.equals(module.key()), "module depends on itself: " + module.key());
            }
        }
        assertEquals(DutyLogModules.ALL.size(), DutyLogModules.knownKeys().size());
    }

    @Test
    void everyDependencyChainTerminatesWithoutCycles() {
        for (ModuleContract module : DutyLogModules.ALL) {
            assertAcyclic(module.key(), new HashSet<>());
        }
    }

    private void assertAcyclic(String key, Set<String> path) {
        assertTrue(path.add(key), "module dependency cycle: " + path + " -> " + key);
        ModuleContract module = DutyLogModules.find(key).orElseThrow();
        for (String dependency : module.dependencies()) {
            assertAcyclic(dependency, new HashSet<>(path));
        }
    }

    @Test
    void nullableContractCollectionsBecomeImmutableEmptyLists() {
        ModuleContract contract = new ModuleContract(
                "test", ModuleCategory.CORE, "Тест", "Test", "Описание", "Description",
                false, false, null, null, null, null, 999);

        assertEquals(List.of(), contract.dependencies());
        assertEquals(List.of(), contract.uiSlots());
        assertEquals(List.of(), contract.apiPrefixes());
        assertEquals(List.of(), contract.offlineQueueTypes());
        assertThrows(UnsupportedOperationException.class, () -> contract.dependencies().add("core"));
    }

    @Test
    void registryMetadataRequiredByClientsIsPresentAndImmutable() {
        for (ModuleContract module : DutyLogModules.ALL) {
            assertFalse(module.key().isBlank());
            assertFalse(module.titleRu().isBlank());
            assertFalse(module.titleEn().isBlank());
            assertFalse(module.descriptionRu().isBlank());
            assertFalse(module.descriptionEn().isBlank());
            assertTrue(module.order() > 0);
        }
        assertThrows(UnsupportedOperationException.class,
                () -> DutyLogModules.knownKeys().add("unexpected"));
    }
}
