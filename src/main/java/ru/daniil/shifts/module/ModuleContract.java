package ru.daniil.shifts.module;

import java.util.List;

/**
 * Developer-facing module contract.
 *
 * A contract describes what a module owns: UI slots, API prefixes and offline queue operations.
 * User settings only switch a module on/off; this contract is the source of truth for boundaries.
 */
public record ModuleContract(
        String key,
        ModuleCategory category,
        String titleRu,
        String titleEn,
        String descriptionRu,
        String descriptionEn,
        boolean locked,
        boolean defaultEnabled,
        List<String> dependencies,
        List<String> uiSlots,
        List<String> apiPrefixes,
        List<String> offlineQueueTypes,
        int order
) {
    public ModuleContract {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        uiSlots = uiSlots == null ? List.of() : List.copyOf(uiSlots);
        apiPrefixes = apiPrefixes == null ? List.of() : List.copyOf(apiPrefixes);
        offlineQueueTypes = offlineQueueTypes == null ? List.of() : List.copyOf(offlineQueueTypes);
    }
}
