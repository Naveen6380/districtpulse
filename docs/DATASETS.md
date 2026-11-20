# Authorised dataset formats

Sector imports require `sourceUrl` (HTTPS), `observedAt` (ISO instant), `authorised:true`, and `data`. The UI accepts a JSON file containing **only the data object**, and collects the provenance fields separately. It does not fetch the supplied source URL.

The following empty shapes are templates, not real statistics. Replace them only with authorised source values and retain supporting evidence. No templates are seeded as live data.

## Weather

```json
{
  "current": {"tempC": 0, "humidity": 0, "windKmph": 0, "condition": "REPLACE FROM SOURCE", "observedAt": "2026-11-16T00:00:00Z"},
  "rainfall": [],
  "warnings": [],
  "monthly": [],
  "forecast": []
}
```

Populate rainfall rows with `{date,actualMm,normalMm}`; forecast rows with `{date,condition,maxC,minC,rainChance}`; monthly rows with `{month,rainfallMm,normalMm,maxTempC}`. Warning rows use `{id,type,level,title,description,areas,issuedAt,validUntil,source,active}`. Levels: red/orange/yellow/green. Types: heavy_rain/cyclone/heatwave/thunderstorm/flood. Use actual official warnings; do not infer an official warning from a forecast.

## Agriculture

```json
{
  "commodities": [], "prices": [], "arrivals": [], "latestPrices": [],
  "schemes": [], "visits": [], "villages": []
}
```

- Price/arrival time-series: `{week, "Commodity name": numericValue}`.
- Latest prices: `{commodity,market,modalPrice,unit,changePct}`.
- Schemes: `{scheme,target,covered}`.
- Visits: `{block,visits,farmersReached}`.
- Villages: `{village,taluk,cropArea,irrigatedPct,soilCards}`.

Units must match the authorised source; do not combine different markets/units into one unexplained figure. Geographic coverage must actually include Pudukkottai.

## Leave aggregates

```json
{
  "totals": {"total": 0, "pending": 0, "approved": 0, "rejected": 0},
  "byDepartment": [], "monthly": [], "byType": [], "recentRequests": []
}
```

Totals must be nonnegative integers and reconcile. Department rows: `{departmentId,department,pending,approved,rejected}`. Monthly rows: `{month,approved,rejected,pending}`. Type rows: `{type,count}`. Employee-level recent requests are discarded by the server. Do not include names, medical reasons, personal identifiers or contact information in an export.

Dataset provenance status distinguishes unconfigured sources, imported/unverified data and stale imports. Importing an export does not establish a live portal connection. Verify all values before administrative action.
