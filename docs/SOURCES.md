# Source registry and feasibility

Target district: **Pudukkottai, Tamil Nadu**, confirmed by the user. Last review: 16 November 2026.

| Source | URL | Access / data | Coverage | Status |
|---|---|---|---|---|
| Google News RSS | https://news.google.com/rss/search?q=Pudukkottai+when:30d&hl=en-IN&gl=IN&ceid=IN:en | Public RSS; titles, snippets, timestamps, links | Search matches for district | Collector implemented; fetch/extraction results recorded in validation |
| Gemini | https://generativelanguage.googleapis.com | API key; structured news extraction | Configured district | Adapter implemented; configured model/key verified locally |
| Farmwise initial intelligence system | https://dev.d27muebhptw7w1.amplifyapp.com/login | Authorised login; APIs/exports not yet established | GHMC relationship to Pudukkottai unconfirmed | Not connected; do not bypass login |
| Yadadri Bhuvanagiri LMS | https://staging.d1ilryfopwulnt.amplifyapp.com/ | Three demo profiles inspected; read-only static aggregate snapshot | Telangana, fixed source date 2026-07-06 | Integrated as external demo reference only; excluded from Pudukkottai statistics |
| IMD | https://mausam.imd.gov.in | Official warning/forecast access method and permitted export must be established | District-dependent | Authorised export import available; no claimed live IMD feed |
| AGMARKNET | https://agmarknet.gov.in | Authorised market price/arrival export/API | Market-dependent | Authorised export import available; live credentials/adapter pending |
| Tamil Nadu town panchayats | Exact challenge dashboard URL pending | Official tax/collection export where permitted | Relevant local bodies only | Not connected |
| Tamil Nadu climate tracker | Exact challenge URL pending | Published climate indicators and permitted exports | Indicator-dependent | Not connected |
| AGRISNET | Exact challenge public dashboard URL pending | Published scheme/visit data or authorised export | Tamil Nadu coverage to verify | Authorised agricultural snapshot import available |
| Chennai flood monitoring | Exact challenge URL pending | Rainfall/water/flood risk where permitted | Chennai | Not merged into Pudukkottai |

On-demand collection is the default; optional `PIPELINE_ENABLED=true` runs the news pipeline every `PIPELINE_DELAY_MS` (default 30 minutes). External sources have their own unknown publication/update schedules: the app does not invent their freshness. Each collected article retains publication/collection dates and source URL. Dataset imports retain source URL, observed time and collection time, with stale status after 24 hours.

Only approved exact source hosts may be fetched. RSS is the only generic automatic adapter. REST, HTML, CSV, Excel, JSON and document source types may be registered, but unsupported URLs explicitly return a partial sync result rather than claiming integration. Authorised file imports are separate from URL fetching. No scraping around authentication, robots restrictions, paywalls or CAPTCHA is implemented. Source terms and rights must be established before enabling a feed or importing full articles.

Official API reference checked for configured model identifiers: https://aistudio.google.com/docs/models . Provider availability is still verified using the actual local credential before activation.

The LMS has no fetch/XHR/WebSocket calls in the inspected client page. Shared demo profile buttons populate a client-side sign-in; no server API or persistent server-side leave database was established. Its CSV export is generated in the browser and includes individual employee fields; the adapter deliberately does not retain those records. The external reference endpoint stores aggregate status/cadre/month/type counts and source provenance only.
