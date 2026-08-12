package ru.daniil.shifts.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Prevents Calendar ICS metadata from drifting behind the actual DutyLog release. */
class CalendarIcsReleaseVersionContractTest {

    @Test
    void calendarIcsProdIdTracksTheProjectReleaseVersion() throws Exception {
        String pom = read("pom.xml");
        String ics = read("src/main/java/ru/daniil/shifts/service/CalendarIcsService.java");
        Matcher projectVersion = Pattern.compile("<artifactId>dutylog</artifactId>\\s*<version>([^<]+)</version>").matcher(pom);

        assertTrue(projectVersion.find(), "DutyLog project version must be present in pom.xml");
        String version = projectVersion.group(1).trim();
        assertTrue(ics.contains("PRODID:-//DutyLog//Time and Overtime " + version + "//RU"),
                "Calendar ICS PRODID must match the current DutyLog project version");
    }

    private static String read(String relative) throws Exception {
        return Files.readString(Path.of(relative), StandardCharsets.UTF_8);
    }
}
