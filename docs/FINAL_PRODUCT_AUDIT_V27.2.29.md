# DutyLog final product audit — v27.2.29

Date: 2026-07-19  
Scope: application source, security boundaries, migrations, deployment scripts, reverse-proxy examples, test matrix and available v27.2.28 CI artifacts.

This is a source-level release audit. It is not a live penetration test and did not have current online vulnerability intelligence.

## Executive result

The product has a strong pre-production baseline: explicit web/mobile security chains, CSRF on browser writes, stateless Bearer mobile endpoints, owner-scoped data access, Flyway-only production schema changes, non-root application container, immutable staging images, clean-PostgreSQL smoke tests, coverage floors and browser E2E.

The audit found four issues worth fixing before the first public production deployment. All four are addressed in v27.2.29:

1. **High — stale browser authority cache.** Existing `JSESSIONID` sessions could keep an old admin role after demotion and could survive password changes until session expiry.
2. **High — proxy-header spoofing.** Authentication rate-limit buckets and audit IPs trusted client-supplied forwarding headers unless the edge overwrote them correctly.
3. **Medium — password-policy downgrade.** Profile password change accepted six characters while registration required eight.
4. **Medium — backup file permissions.** PostgreSQL dumps inherited host umask and could be readable by unintended local users.

A bounded cleanup for expired mobile authentication-token rows was also added as operational hardening.

## Implemented controls

### Browser-session invalidation

- `users.auth_version` is added by Flyway V23.
- Web principals carry the version observed at login.
- Password changes, administrative resets, role changes and bootstrap role/password changes increment the version.
- A security-chain filter compares the session principal with the current user row before authorization.
- Stale or deleted accounts have their security context cleared and HTTP session invalidated.
- Force-resetting the bootstrap administrator also revokes mobile sessions.

### Trusted client IP handling

- Forwarding headers are ignored by default outside the managed proxy deployment.
- The shared resolver validates IPv4/IPv6 literals before using them in rate-limit or audit keys.
- nginx and Caddy overwrite `X-Real-IP` and `X-Forwarded-For` with the direct peer address.
- The proxy-level nginx limiter now includes legacy and v1 mobile login/registration routes.

### Backups

- Backup and restore scripts use `umask 077`.
- Backup directories are forced to `0700`.
- Dumps and checksum sidecars are forced to `0600`.
- Archive parsing and SHA-256 verification remain in place.

### Token retention

- Expired mobile authentication rows older than the configured retention period are removed on a scheduled transaction.
- The retention has a seven-day safety floor and defaults to 90 days after refresh expiry.

## New regression coverage

- stale web session after password change;
- stale cached admin session after role demotion;
- matching/stale `auth_version` filter behavior;
- untrusted proxy-header spoofing and trusted-edge behavior;
- normal/admin password minimums and auth-version increments;
- bootstrap force-reset mobile-session revocation;
- expired mobile-token cleanup and retention clamping.

Baseline declared by the release gate: 65 Java test classes, 340 `@Test` methods and 5 Playwright scenarios.

## Residual launch risks

These are not hidden defects fixed by more unit tests; they require deployment or online work:

- **Dependency/CVE status is unverified in this offline audit.** Run SCA and container scans against the exact release digest before public launch.
- **No live DAST/pentest yet.** Exercise staging over HTTPS, including proxy behavior, throttling, CSRF, IDOR, session invalidation and malformed requests.
- **Backups are still local by default.** Copy encrypted backups offsite and prove restoration from an offsite copy.
- **Docker-group access is root-equivalent.** On a shared VPS it can affect unrelated containers; use stronger isolation when required.
- **The rate limiter is single-instance memory.** It is correct for one app container, not horizontal scaling.
- **Concurrent duplicate Android `operationId` requests need a PostgreSQL stress proof.** The unique constraint protects the ledger, but a race can still surface as a transient server error.
- **Admin user search currently pages in memory.** Move filtering/pagination to SQL before a large user base.
- **Frontend npm installation has no committed lockfile.** Add a verified lockfile and switch CI to `npm ci` during the online dependency update pass.
- **GitHub Actions and base images are not commit/digest pinned.** Verify current upstream SHAs/digests online before pinning; do not invent them.

## Launch decision

v27.2.29 is suitable for staging after `mvn clean verify` and the five Playwright scenarios pass in CI. Production approval should remain blocked until staging, live security scanning and backup/restore rehearsal are complete.
