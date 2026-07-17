# DutyLog testing and JaCoCo coverage

## Two different ways to run tests

IntelliJ's green JUnit button and Maven's `verify` lifecycle are not the same operation.

### IntelliJ JUnit runner

Running a test class or the whole `src/test` tree with the green arrow:

- compiles production and test classes;
- runs JUnit tests;
- normally creates `target/classes` and `target/test-classes`;
- does **not** execute Maven plugins bound to `verify`;
- therefore does not create the JaCoCo HTML report.

This mode is useful while developing one test, but it is not the release gate.

### Maven release gate

Run from the project root, where `pom.xml` is located:

```bash
mvn clean verify
```

On Windows PowerShell or Command Prompt the command is the same:

```powershell
mvn clean verify
```

After a successful build, open:

```text
target/site/jacoco/index.html
```

The report is generated only after Maven reaches the `verify` phase. Running only
`mvn test`, IntelliJ Build, or a JUnit run configuration is not sufficient for this project.

## Running `verify` from IntelliJ without a system Maven command

1. Open **View → Tool Windows → Maven**.
2. Expand the DutyLog project.
3. Expand **Lifecycle**.
4. Double-click **clean**.
5. Double-click **verify**.
6. Refresh the project tree if `target/site` is not shown immediately.
7. Open `target/site/jacoco/index.html` in a browser.

IntelliJ can use its bundled Maven, so this also works when `mvn` is not available in
PowerShell.

## Fast development loop

For one class:

```bash
mvn -Dtest=TaskServiceTest test
```

For the complete release gate and coverage report:

```bash
mvn clean verify
bash deploy/scripts/release-check.sh
```

## What a green build means

A green build confirms the automated contracts covered by the suite. It does not replace
manual checks for browser permission prompts, operating-system notification appearance,
responsive layout, service-worker lifecycle, or a real PostgreSQL deployment.


## Coverage gate

`mvn clean verify` not only creates `target/site/jacoco/index.html`; it also fails the build if total JaCoCo coverage drops below:

- 88% instructions;
- 70% branches.

The thresholds intentionally sit below the current verified baseline (90% / 73%) to allow small refactors while preventing silent regression. Raise them only after a green CI run proves the new baseline.
