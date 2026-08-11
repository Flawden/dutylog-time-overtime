package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression contract for v27.34.1 strict Vue template typing and Vite 5 library options. */
class VueStrictOptionalTemplateContractHotfixTest {

    @Test
    void strictOptionalTemplateBindingsAndViteLibraryOptionsCompileWithoutUndefinedAttributes() throws Exception {
        String navigation = read("frontend/src/app/AppNavigation.vue");
        String button = read("frontend/src/shared/ui/UiButton.vue");
        String tabs = read("frontend/src/shared/ui/UiTabs.vue");
        String vite = read("frontend/vite.config.ts");
        String tsconfig = read("frontend/tsconfig.json");
        String diagnosticsSpec = read("frontend/src/platform/diagnostics/frontendDiagnostics.spec.ts");

        assertTrue(tsconfig.contains("\"exactOptionalPropertyTypes\": true"));
        assertTrue(navigation.contains(":aria-current=\"activeRoute === item.route ? 'page' : false\""));
        assertFalse(navigation.contains("? 'page' : undefined"));
        assertTrue(button.contains(":type=\"type ?? 'button'\""));
        assertTrue(button.contains(":disabled=\"disabled ?? false\""));
        assertTrue(tabs.contains(":disabled=\"option.disabled ?? false\""));
        assertFalse(vite.contains("cssFileName:"));
        assertTrue(vite.contains("assetInfo.name === \"style.css\""));
        assertTrue(vite.contains("dutylog-vue-app-shell.css"));
        assertTrue(vite.contains("import packageMetadata from \"./package.json\";"));
        assertTrue(vite.contains("const releaseVersion = packageMetadata.version;"));
        assertFalse(vite.contains("const releaseVersion = \"27."));
        assertTrue(diagnosticsSpec.contains("import packageMetadata from \"../../../package.json\";"));
        assertTrue(diagnosticsSpec.contains("expect(failure.releaseVersion).toBe(packageMetadata.version);"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
