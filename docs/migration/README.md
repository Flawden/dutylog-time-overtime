# Vue domain migration manifests

Every domain release from `v27.36.0` through `v27.40.0` starts by copying `_template.md` to:

```text
docs/migration/<domain>-vue-migration-manifest.md
```

The manifest is an executable parity contract, not a retrospective note. Each legacy journey must have evidence for Vue behavior, tests, accessibility, offline/PWA impact and legacy deletion. A domain is not complete while two UI owners remain.

The binding Definition of Done is [`../VUE_MIGRATION_EXECUTION_STANDARD.md`](../VUE_MIGRATION_EXECUTION_STANDARD.md).
