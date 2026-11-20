# REST API contract

Base URL locally: `http://localhost:8091/api`. JSON bodies and responses unless otherwise noted. Authenticated requests use `Authorization: Bearer <JWT>`. Never log bearer tokens or source credentials.

Errors use HTTP status and `{ "message": "..." }` where controlled by the API. Security filters may return an empty 401/403 body. Missing authentication → 401; forbidden role/department → 403; invalid fields → 400; missing record → 404; disabled source → 409; login throttling → 429. Browser services translate these into existing error states.

| Method | Path | Purpose / response | Access |
|---|---|---|---|
| GET | `/health` | `{status:"up"}`; process health, not a source-health check | Public |
| POST | `/auth/login` | `{email,password}` → `{token,user,expiresAt}` | Public; throttled |
| GET | `/auth/me` | Current profile; no password hash | Signed in |
| POST | `/auth/logout` | Client discards one-hour access token | Signed in |
| POST | `/auth/forgot-password` | Explicit administrator-contact instruction; no fictional email delivery | Public |
| POST | `/auth/users` | `{email,password,name,role,departmentId}` → user | Administrator |
| GET | `/districts` | Configured real district; no demo district mixed into live mode | Signed in |
| GET | `/districts/{d}/overview` | `OverviewData` DTO; source health from stored sources | Signed in; officers scoped |
| GET | `/districts/{d}/incidents` | Last 30 days of incident DTOs | Signed in; officers scoped |
| GET | `/intelligence/incidents/{id}` | `{incident,articles,related}` | Signed in; officers scoped |
| PATCH | `/incidents/{id}/verification` | `{verification,note?}` → incident | Collector/admin |
| PATCH | `/incidents/{id}/department` | `{departmentId}` → incident | Collector/admin |
| PATCH | `/incidents/{id}/status` | `{status}` → incident | Signed in; officers scoped |
| PATCH | `/incidents/{id}/location` | `{lat,lng,sourceUrl,verified:true}` → incident; finite/range-checked | Collector/admin |
| GET | `/districts/{d}/news/articles` | All dated relevant records in last 30 days; no 50-row truncation | Collector/admin |
| GET | `/districts/{d}/news/clusters` | Source-preserving incident clusters | Collector/admin |
| GET | `/districts/{d}/news/insights` | Observed counts, recurrence, period comparisons | Collector/admin |
| GET | `/districts/{d}/news/summaries` | Structured evidence-linked summary DTOs | Collector/admin |
| POST | `/pipeline` | Queue background collection/extraction/grouping → `{id,status}` | Collector/admin |
| GET | `/pipeline/{id}` | Persisted job status and result counts | Collector/admin |
| POST | `/pipeline/retry` | Queue failed article extraction again | Collector/admin |
| POST | `/news/import` | Multipart authorised document import; see below | Collector/admin |
| POST | `/news/collect` | Legacy synchronous RSS collection → counts | Collector/admin |
| POST | `/news/{id}/process` | Process one NEW/FAILED article → `{processed}` | Collector/admin |
| POST | `/news/process` | Legacy synchronous extraction; prefer background pipeline | Collector/admin |
| POST | `/incidents/build` | Incremental grouping; preserves IDs/review → counts | Collector/admin |
| GET | `/districts/{d}/map` | `{incidents,overlays}`; unresolved records contain no coordinates | Signed in; officers scoped |
| GET | `/districts/{d}/departments` | `DepartmentStats[]` | Signed in; officers scoped |
| GET | `/districts/{d}/departments/{id}` | `{department,stats,incidents,actions}` | Signed in; officers scoped |
| GET | `/districts/{d}/actions` | `ActionItem[]` | Signed in; officers scoped |
| POST | `/actions` | ActionInput → persisted ActionItem | Signed in; officers scoped |
| PATCH | `/actions/{id}` | Partial action fields/status → ActionItem | Signed in; officers scoped |
| POST | `/actions/{id}/comments` | `{text}` → ActionItem; author taken from authenticated user | Signed in; officers scoped |
| GET | `/districts/{d}/notifications` | Current high/critical incidents; read state is not persisted | Signed in; officers scoped |
| GET | `/districts/{d}/search?q=...` | Matching incident/action references | Signed in; officers scoped |
| GET/PUT/DELETE | `/users/me/dashboard-preferences` | Get/save/reset account layout and alerts | Signed in; server-resolved owner |
| GET/POST | `/sources` | List/create source metadata; no credentials returned | Collector/admin |
| PUT | `/sources/{id}` | Update provided source fields | Collector/admin |
| PATCH | `/sources/{id}/status` | `{enabled}`; enabled sources return to pending until synced | Collector/admin |
| POST | `/sources/test` | `{url,type}` → `{ok,message}`; real network check | Collector/admin |
| POST | `/sources/{id}/sync` | Real SyncLog; RSS queues analysis; unsupported types return partial | Collector/admin |
| GET | `/sources/{id}/logs` | Latest 20 sync logs | Collector/admin |
| GET | `/districts/{d}/weather` | Imported WeatherOverview or empty response if unconfigured | Signed in |
| GET | `/districts/{d}/agriculture` | Imported AgricultureOverview or empty response | Collector/admin |
| GET | `/districts/{d}/leave/summary` | Imported aggregate LeaveOverview or empty response | Collector/admin |
| GET | `/districts/{d}/datasets/{kind}/provenance` | Attribution, observed/collection times, import status | Weather: signed in; other sectors: collector/admin |
| POST | `/districts/{d}/datasets/{kind}/import` | `{sourceUrl,observedAt,authorised:true,data}` → import result | Collector/admin |
| POST | `/districts/{d}/reports` | ReportRequest → GeneratedReport | Collector/admin |

## Example action

```json
{
  "title": "Verify reported road damage",
  "description": "Review supporting sources and request a field assessment.",
  "departmentId": "pwd",
  "priority": "high",
  "dueDate": "2026-11-20",
  "incidentId": "LIVE-123"
}
```

`incidentId` is optional. If supplied, the incident must exist and its assigned department must match. Statuses: `open`, `in_progress`, `under_review`, `completed`, `on_hold`. Completion time is set by the server. Comments are 1–5000 characters. No client-supplied author is trusted.

Incident DTO IDs are `LIVE-{numericId}`; article IDs are `LIVE-A{numericId}`; source IDs are `SRC-{numericId}`. The inherited `/news` and `/incidents` maintenance endpoints expose raw entity shapes for existing tools; the new frontend consistently uses the DTO endpoints above.

## Authorised article document import

`POST /news/import` uses multipart fields:

- `file`: TXT, CSV, XLSX, text PDF, PNG or JPEG; maximum 10MB.
- `title`, `source`, `sourceUrl`: required attribution; source URL is not fetched.
- `publishedAt`: ISO datetime with timezone; within previous 30 days.
- `authorised`: `true`, explicitly confirmed in the UI.

The response includes the stored article ID and background pipeline job. Duplicate source URLs are skipped. OCR requires Tesseract and `eng+tam` language packs; scanned PDFs must be converted to images by an authorised workflow first. No shell interpretation is used to invoke OCR.

## Reports

```json
{
  "type": "weekly",
  "from": "2026-11-09",
  "to": "2026-11-16",
  "categories": [],
  "departmentIds": [],
  "priorities": []
}
```

Response contains title, district, generatedAt, period, and sections. Sections distinguish `verified`, `ai`, and `data`. The UI renders PDF/Excel locally from this exact response. This is a reporting workflow over stored incidents; selecting weather/agriculture does not manufacture sector statistics or prove a government integration.

## Known contract limits

There is no hard-delete source endpoint, no generic arbitrary-site scraper, no email notification delivery, and no automatic portal login adapter. Alert preferences are persisted; email delivery is intentionally not offered as a functioning feature. Multi-instance job coordination and persistent notification-read state are future work.

Administrator account maintenance: GET `/auth/users` lists profiles; PATCH `/auth/users/{id}` accepts a new `password` (12�72 characters) or `enabled` boolean. Resetting a password invalidates existing tokens. GET `/audit` returns the latest 200 stored audit events for administrators.


## Provided LMS reference adapter

GET `/integrations/lms` returns the last stored external aggregate snapshot (or `not_synced`). POST `/integrations/lms/sync` reads the exact public staging URL and returns `reference_demo_snapshot` with source district/state, fixed referenceDate, fetchedAt, SHA-256 source fingerprint, `isDemo:true`, `scope:external_reference_only`, totals (including cancelled), byCadre, monthly and byType counts. Collector/administrator only. Unknown page schemas/literal expressions fail safely and preserve the previous snapshot. No remote JavaScript is evaluated, no source records are changed, and employee names/IDs/reasons/attachments are discarded. This endpoint never writes `SECTOR-pudukkottai-leave`.


GET `/districts/{d}/news/status` (Collector/administrator only) returns last-30-day collected, pending, failed, processed and excluded counts, plus safe model/provider status. A blocked provider includes reasonCode, retryAt and requestLimit when supplied by the provider. It does not return raw provider/account/key data.
