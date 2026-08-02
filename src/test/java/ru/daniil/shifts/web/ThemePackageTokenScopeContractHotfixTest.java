package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression contract for v27.29.1 Theme Package Token Scope Contract Hotfix. */
class ThemePackageTokenScopeContractHotfixTest {

    @Test
    void javaContractMatchesTheActualJavascriptTemplateLiteral() throws Exception {
        String studio = read("src/main/resources/static/js/12-ui-platform.js");
        String contract = read("src/test/java/ru/daniil/shifts/web/WorkspaceLayoutThemeStudioFrontendContractTest.java");

        assertTrue(studio.contains("tokenScope:`html[data-ui-theme=\"${id}\"]`"));
        assertFalse(studio.contains("tokenScope:`html[data-ui-theme=\\\"${id}\\\"]`"));

        String oneSourceBackslashQuote = "\\" + "\"";
        String threeSourceBackslashesQuote = "\\" + "\\" + "\\" + "\"";
        assertTrue(contract.contains("data-ui-theme=" + oneSourceBackslashQuote + "${id}" + oneSourceBackslashQuote));
        assertFalse(contract.contains("data-ui-theme=" + threeSourceBackslashesQuote + "${id}" + threeSourceBackslashesQuote));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
