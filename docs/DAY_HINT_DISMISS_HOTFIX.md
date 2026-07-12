# v26.6.8 — today clarity and dismissible hidden-blocks hotfix

Carried into release candidate: v27.2.2.
Status: v26.6.8.

This hotfix keeps the stabilization scope: no new large features, only UX clarity for the release candidate path.

## Calendar day states

The selected day and the current day now use different visual language:

- selected day: strong solid accent outline;
- current day: subtle tinted/dashed cell, small accent dot and `сегодня` label;
- selected current day: keeps the strong selected outline.

This avoids confusing “today” with the day the user clicked.

## Hidden blocks notice

The selected-day notice about disabled modules can now be dismissed:

- the notice has a small close button;
- dismissal is saved in `localStorage` under `dutylog.dayModulesHint.dismissed.v1`;
- after dismissal, the notice stays hidden in that browser;
- no backend data or module settings are changed.

The notice still remains useful on first run, but it no longer nags the user after they understand what disabled modules mean.

## Release guardrails

`release-check.sh` verifies that:

- the dismiss key is present;
- the close button is rendered;
- the close button CSS exists;
- today uses a less selected-looking style;
- the current release document is present.
