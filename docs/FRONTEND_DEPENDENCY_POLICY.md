# Frontend dependency and vulnerability policy

Status: active from v27.35.0.

## Reproducibility

- `frontend/package.json` uses exact dependency versions.
- `frontend/package-lock.json` is committed and is the only accepted dependency tree.
- Node `20.18.1` and npm `10.8.2` are pinned in package metadata, CI and Docker.
- Local, CI and Docker builds use `npm ci`; `npm install --package-lock=false` is forbidden.
- A dependency update must change both package files and pass typecheck, Vitest, bundle audit, Maven, Playwright and Docker smoke.

## Update cadence

Dependabot checks Maven, GitHub Actions, Docker, root Playwright and `/frontend` npm dependencies weekly. Patch/minor updates are reviewed in bounded groups; majors require an ADR or explicit migration note when they alter architecture or runtime behavior.

## Vulnerabilities

- Critical advisories are triaged immediately and block release when exploitable in the shipped image/build chain.
- High advisories receive a documented remediation or accepted-risk decision before the next feature release.
- Moderate/low findings are reviewed during regular dependency maintenance.
- A full blocking Maven/npm/container audit is required again in `v27.45.0`.
- Lockfile deletion, advisory suppression without rationale and broad version ranges are not accepted fixes.

## Evidence

The review records package, advisory, affected surface, exploitability, chosen version, tests and rollback implications. CI logs and Dependabot PRs are evidence; a green build alone is not a vulnerability assessment.
