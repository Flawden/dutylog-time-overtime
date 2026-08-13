# v27.40.34 — Vue Asset Version Contract Hotfix

## CI evidence

The v27.40.33 run passed the exact Vue frontend step and then failed in `Build, test and enforce coverage`. The saved run snapshot shows Maven reached the Java test phase; repository inspection finds three live assertions in `VueAppShellDesignSystemContractTest` / `VueFrontendFoundationContractTest` still pinned to `v=27.40.32` while `index.html` correctly ships the current v27.40.33 asset suffix.

## Root cause

The contracts tested a valid invariant—browser assets must be cache-busted with the release version—but encoded one historical patch number. Any subsequent release bump therefore made the test stale even when runtime packaging was correct.

## Fix

Both contract classes now read `info.app.release-version` from `src/main/resources/application.properties` and compose the expected asset URL from that canonical value. This preserves the invariant while removing patch-release churn. Production runtime code is unchanged.

The contract now follows the canonical application release version rather than a historical patch literal.
