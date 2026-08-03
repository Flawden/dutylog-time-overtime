# v27.31.2 — Canonical Absence Browser Contract Alignment Hotfix

GitHub Actions confirmed Maven 626/626, then the 41-scenario browser suite finished with 39 passed and two stale ownership expectations. Both failures described the retired standalone usage UI rather than the released canonical absence model.

## Fix

- verify the intentional `409 DIRECT_USAGE_RETIRED` response through `page.context().request`, outside the strict page console/network collector;
- render and assert linked usages as read-only `Управляется отсутствием` projections in Overtime;
- open the owning absence through the Vacation/Absences editor instead of expecting `data-edit-usage`;
- after deleting one absence, assert the deleted projection is gone, the surviving absence projection remains and all credits/FIFO allocations are preserved.

This changes no production behavior. API, PostgreSQL, FIFO, Payroll and Flyway remain unchanged at V47.
