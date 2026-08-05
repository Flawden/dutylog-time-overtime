# DutyLog v27.36.3 — CI Artifact Quota Resilience Hotfix

## Trigger

GitHub Actions completed the exact frontend gate and Maven `verify`, then failed while `actions/upload-artifact@v4` attempted to persist 473 JaCoCo report files. GitHub reported exhausted artifact storage and skipped release checks, Playwright, image build and clean PostgreSQL smoke even though product compilation and tests had succeeded.

## Resolution

- JaCoCo publication now uploads only `target/site/jacoco/jacoco.xml` and `jacoco.csv`.
- JaCoCo and Playwright artifacts use three-day retention and names qualified by `github.run_id` plus `github.run_attempt`.
- Playwright artifacts are uploaded only after failed browser validation in CI and staging.
- All artifact upload steps are `continue-on-error`; report storage cannot stop later quality/deployment stages.
- Missing failure reports are ignored because they are diagnostic, not release authority.

## Preserved blocking gates

Frontend delivery verification, OpenAPI drift, strict TypeScript, Vitest, Vite, Maven/JUnit/JaCoCo thresholds, static release checks, Playwright execution, Docker image build, clean PostgreSQL migration smoke and staging deployment remain fail-closed.

## Runtime impact

None. No Vue source, Spring Boot behavior, OpenAPI shape, npm dependency graph, PostgreSQL schema, Flyway migration, security boundary or domain ownership changes.

## Rollback

Ordinary application-code revert. No database or user-data rollback is required.
