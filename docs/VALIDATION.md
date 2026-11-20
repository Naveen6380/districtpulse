# Validation — 16 November 2026

## Automated results

- Frontend TypeScript: passed after fixing the truncated live-adapter test and nullable query callback contract.
- Frontend tests: 18 passed across 5 files, including cached-session expiry and logout storage cleanup.
- Frontend ESLint: 0 errors, 7 warnings at last check; warnings are existing React refresh/hook conventions, not a lint failure.
- Frontend production build: passed. Nitro Node server preset is configured for the supplied production container.
- Backend compilation, JAR packaging and tests: passed at last regression run. Tests cover real HTTP login/JWT enforcement, officer department scope, forged/anonymous access, action/comment persistence, reports, per-user preferences, source URL restrictions, AES-GCM round trip, aggregate leave imports, and incremental incident grouping preserving IDs/review/location boundaries.
- Tests use H2 MySQL compatibility mode. Separately, the actual app connected successfully to MySQL 8.0.45 on an isolated 127.0.0.1:3307 instance.

## Actual local smoke checks

The generated local administrator authenticated against MySQL. Twelve protected endpoints returned HTTP 200: profile, districts, overview, incident list, news list, clusters, insights, departments, actions, map, preferences and sources. Frontend `/login` returned HTTP 200. Tokens/passwords were not printed.

Public Google News collection stored **20 dated RSS records** within the configured 30-day window. This verifies this feed fetch, not exhaustive newspaper coverage.

The supplied Gemini model/key lookup succeeded. **Actual generateContent requests returned HTTP 429**, so successful real-AI extraction has **not** been verified. One real article is marked FAILED and other collected records remain pending; the app does not seed mock summaries as live intelligence. Restore provider quota/rate-limit availability, then use Retry failed / Collect & analyse. Do not report the real-AI demo as completed until a source-backed record successfully processes. Failure diagnostics are redacted and stored locally.

## UI preservation and verification limits

The SHA-256 of `frontend/src/styles.css` matches the original exactly. Existing login/sidebar/header/overview/news/incident layouts keep their design and components. Functional controls and actual data wiring were added; previously empty pages use the same design system.

The browser preview failed to connect to localhost, so screenshot-based desktop/tablet/mobile QA and browser end-to-end interaction checks are **not completed**. HTTP rendering, compilation, component import/routing tests and API tests do not substitute for those checks. PDF/Excel download code builds but has not been exercised in a browser; standard jsPDF fonts may require an embedded licensed Unicode font for Tamil-heavy exports.

## Not verified / still external

- Farmwise portal integration: authorised access/API/export details remain pending. LMS profile exploration and external-reference integration are now verified below.
- Dedicated IMD/AGMARKNET/AGRISNET/Tamil Nadu dashboard live integrations: not connected; authorised export imports are implemented instead.
- Tesseract OCR: adapter implemented, local executable/language packs not installed or exercised here. Text-PDF/XLSX parsing dependencies compile.
- Docker deployment: configuration supplied but not executed because Docker is unavailable on this machine.
- Public deployment, HTTPS/reverse proxy, email/reset delivery, MFA, distributed scheduling/rate limiting, production migrations and restore rehearsal: not claimed complete.

This is a substantially implemented, tested core application with explicit external blockers; it is not represented as a fully verified production deployment or a completed government-system integration.


## LMS follow-up verification

All three provided demo profiles were opened through the portal UI: employee (balances/history/planner), reporting manager (queue/team/calendar/insights), and establishment/admin (organisation overview/reports/types/employees). No approvals, rejections, withdrawals, employee edits or configuration changes were made.

Read-only backend sync succeeded. Source date: **6 July 2026**; jurisdiction: **Yadadri Bhuvanagiri, Telangana**. Snapshot: **39 requests = 31 approved + 6 pending + 1 rejected + 1 cancelled**, 28 officers (29 profile accounts including the establishment administrator). These are published prototype values, not verified live administrative statistics.

An explicit before/after HTTP check confirmed Pudukkottai leave data was unchanged by reference sync. Only aggregate counts/provenance were persisted; downloaded inspection HTML was removed after validation. Parser regression tests reject executable JavaScript, changed schemas, invalid dates and broken user references, and assert that employee identities/contact details/reasons are absent from the output. Backend suite now has 9 tests; frontend has 18 tests. TypeScript, production build and lint checks passed for this change. Local screenshot QA of the new reference panel is still not claimed.


## Overview and map follow-up

Fixed the overview empty-state gate that hid the entire dashboard when incident count and processed news were both zero. The page now displays its KPI cards, charts, departments and source health for a valid empty dataset. Added a component regression test for this condition; frontend tests now total 19. Also corrected the Leaflet marker-cluster plugin import to use the mutable default export. Browser inspection confirmed the dashboard renders and map zoom controls initialise. The existing design/CSS is preserved; this is a desktop flow check, not a claim that all responsive layouts and export interactions have been visually verified.


## Successful real-AI and incident follow-up

Exact original failure: `gemini-3.8-flash` returned HTTP 429 RESOURCE_EXHAUSTED for the per-project/per-model free-tier daily quota, limit 20. The existing credential was tested with the documented `gemini-3.5-flash-lite` model and generation succeeded. Local settings now select this model; no billing/account/payment settings were changed.

Final live MySQL-backed result: **20 collected articles, 20 processed, 3 excluded as irrelevant, 0 pending, 0 failed, 17 AI-extracted incident records**. The background job completed successfully. Incidents are review candidates, not verified distinct events; conservative title/location grouping can leave related reports separate. Unknown locations remain unresolved. Evidence and supporting source references are retained.

The incidents page was verified in the browser: 17 records, filters, status/review columns, source references and pagination rendered. The generic district-empty message has been replaced with processing-specific diagnostics for future empty datasets. Overview/news/incidents refresh every 15 seconds while active.

Quota errors now persist safe model-specific retry state and stop the processing batch. Remaining articles stay queued. Regression tests cover daily quota parsing/redaction, persisted model-scoped blocks, stopping after one quota failure, role-protected pipeline diagnostics and the explanatory incident empty state. Latest suite: **12 backend tests and 21 frontend tests**, TypeScript/build/lint checks pass (0 lint errors).

A live JAR was accidentally rebuilt during processing, causing lazy class-loading failures at grouping. Stored article results survived. Startup now executes a versioned immutable copy under ignored `.local/runtime`, independent of future Gradle build output. The backend was restarted on that copy, saved articles were grouped without repeating successful AI calls, and only the two failed extractions were retried; the final job completed with zero failures.
