# v26.6.11 — CI permission hotfix

Status: v26.6.11.

This hotfix fixes the GitHub Actions release gate on repositories cloned from
Windows or archives where executable bits can be lost.

## Fixed

- CI now starts the release gate with `bash ./deploy/scripts/release-check.sh`.
- The release gate no longer depends on the executable bit of `release-check.sh`.
- Release checks verify that CI uses the bash invocation.

## Local note

Keeping shell scripts executable is still recommended:

```bash
git update-index --chmod=+x deploy/scripts/*.sh
```

But CI is now resilient even when the executable bit is missing.
