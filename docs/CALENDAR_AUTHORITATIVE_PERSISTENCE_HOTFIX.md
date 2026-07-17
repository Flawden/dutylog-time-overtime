# Calendar authoritative persistence hotfix

Status: v27.2.5.

This release fixes bulk schedule fills that appeared successful but were not reproduced by a later month read.

Guarantees:

- every target date is saved explicitly;
- the transaction flushes and clears the persistence context;
- persisted rows are re-read and verified before success is returned;
- the browser performs a direct no-store calendar reload after fill;
- the authoritative response replaces both UI state and IndexedDB snapshot;
- an end-to-end MockMvc test checks all 31 August dates after POST + GET.
