# Pipefail-safe authenticated smoke-test hotfix — v27.2.32

## Incident

The v27.2.31 staging deployment started PostgreSQL and the application successfully, but the workflow exited with code `141` during the authenticated app-shell check.

With `set -o pipefail`, a construct such as:

```bash
echo "$APP_HTML" | grep -q 'DutyLog'
```

can fail even when the expected text is present. `grep -q` exits as soon as it finds a match; the producer can then receive SIGPIPE while still writing a larger response. Bash reports that signal as exit code 141.

## Correction

`smoke-test.sh` now captures each HTTP response first and performs literal checks through here-strings. This preserves both properties:

- a failed `curl` remains a real deployment failure;
- an early successful search cannot SIGPIPE the response producer.

The regression harness serves a large multiline authenticated shell so the former implementation fails deterministically while the corrected implementation completes every check.
