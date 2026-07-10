# v26.6.11 — UI alignment and registration test hotfix

Carried into release candidate: v27.2.0.
This hotfix keeps the v26.6 release-polish scope and fixes two regressions found after v26.6.10.

## Fixed

- `RegistrationTest` compiles again: the test helper now supports registration bodies with `languagePreference`.
- Settings header controls are visually stable across RU/EN:
  - profile avatar is pinned to the right side of the profile card header;
  - time autosave chip is pinned to the right side of the time card header;
  - notification count/browser-permission chips are pinned to the right side of the notification card header.

## Java 25 local warning

The warning about `java.lang.System::load` from Tomcat Native on Java 25 is not an application startup failure. CI runs on JDK 17, which is the supported runtime target for this project. For local development, JDK 17 is still the cleanest option. If running from IntelliJ on Java 25, the warning can be ignored unless a future Java release turns it into a hard failure.
