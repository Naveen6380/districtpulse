package org.example.districtpulse.platform;
import java.time.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
/** Authorised export adapter. Never claims a government portal is connected merely because its URL exists. */
@RestController @RequestMapping("/api/districts/{d}")
public class SectorController {
    private final DocumentStore store;private final IntelligenceService intel;private final JsonMapper mapper=JsonMapper.builder().build();
    private final org.example.districtpulse.integration.WeatherLiveService weather; private final org.example.districtpulse.integration.AgmarknetService agriculture;
    public SectorController(DocumentStore store,IntelligenceService intel,org.example.districtpulse.integration.WeatherLiveService weather,org.example.districtpulse.integration.AgmarknetService agriculture){this.store=store;this.intel=intel;this.weather=weather;this.agriculture=agriculture;}
    private String sector(String kind){if(!Set.of("weather","agriculture","leave").contains(kind))throw new IllegalArgumentException("Unknown sector");return kind;}
    private void checkRole(String kind,Authentication auth){if(!"weather".equals(kind)&&auth.getAuthorities().stream().noneMatch(a->Set.of("ROLE_collector","ROLE_administrator").contains(a.getAuthority())))throw new ResponseStatusException(HttpStatus.FORBIDDEN);}
    private Map<String,Object> dataset(String d,String kind){var doc=store.get("SECTOR-"+d+"-"+kind);if(doc!=null)return doc;if("weather".equals(kind))return weather.snapshot(d);if("agriculture".equals(kind))return agriculture.snapshot(d);return null;}
    @GetMapping({"/{kind:weather|agriculture}","/{kind:leave}/summary"}) public ResponseEntity<?> get(@PathVariable String d,@PathVariable String kind,Authentication auth){intel.district(d);checkRole(kind,auth);var doc=dataset(d,kind);if(doc==null)return ResponseEntity.ok().build();if(Set.of("open_meteo_forecast","agmarknet_ogd").contains(doc.get("method"))){var data=new LinkedHashMap<Object,Object>((Map<?,?>)doc.get("data"));data.put("freshness",doc.get("status"));data.put("fetchedAt",doc.get("collectedAt"));return ResponseEntity.ok(data);}return ResponseEntity.ok(doc.get("data"));}
    @GetMapping("/datasets/{kind}/provenance") public Map<String,Object> provenance(@PathVariable String d,@PathVariable String kind,Authentication auth){intel.district(d);sector(kind);checkRole(kind,auth);var doc=dataset(d,kind);if(doc==null)return Map.of("status","not_connected","district",d);Map<String,Object> copy=new LinkedHashMap<>(doc);copy.remove("data");if(doc.get("observedAt")!=null&&Instant.parse(doc.get("observedAt").toString()).isBefore(Instant.now().minusSeconds(86400)))copy.put("status","stale");return copy;}
    @PostMapping("/datasets/{kind}/import") @PreAuthorize("hasAnyRole('collector','administrator')")
    public Map<String,Object> importData(@PathVariable String d,@PathVariable String kind,@RequestBody Map<String,Object> input,Authentication auth){
        intel.district(d);sector(kind);String url=Objects.toString(input.get("sourceUrl"),"");java.net.URI uri;try{uri=java.net.URI.create(url);}catch(Exception e){throw new IllegalArgumentException("Invalid source URL");}if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)throw new IllegalArgumentException("A public HTTPS attribution URL is required");
        Instant observed;try{observed=Instant.parse(input.get("observedAt").toString());}catch(Exception e){throw new IllegalArgumentException("observedAt must be an ISO instant");}if(observed.isAfter(Instant.now().plusSeconds(300)))throw new IllegalArgumentException("Future data timestamp");
        if(!Boolean.TRUE.equals(input.get("authorised")))throw new IllegalArgumentException("Confirm that the export is authorised");
        if(!(input.get("data") instanceof Map<?,?> raw))throw new IllegalArgumentException("data must be an object");
        Map<String,Object> data=new LinkedHashMap<>();raw.forEach((k,v)->data.put(k.toString(),v));
        var tree=mapper.valueToTree(data);
        List<String> arrays=switch(kind){case "weather"->List.of("rainfall","warnings","monthly","forecast");case "agriculture"->List.of("commodities","prices","arrivals","latestPrices","schemes","visits","villages");default->List.of("byDepartment","monthly","byType");};
        for(String key:arrays)if(!tree.path(key).isArray())throw new IllegalArgumentException("Missing array: "+key);
        if("weather".equals(kind)){
            var current=tree.path("current");for(String key:List.of("tempC","humidity","windKmph"))if(!current.path(key).isNumber())throw new IllegalArgumentException("Invalid current weather "+key);
            for(var w:tree.path("warnings")){if(!w.path("id").isString()||!w.path("title").isString()||!w.path("description").isString()||!w.path("source").isString()||!w.path("areas").isArray()||!w.path("active").isBoolean()||!Set.of("red","orange","yellow","green").contains(w.path("level").asText()))throw new IllegalArgumentException("Invalid warning");}
        }
        if("leave".equals(kind)){
            for(String key:List.of("total","pending","approved","rejected"))if(!tree.path("totals").path(key).isIntegralNumber()||tree.path("totals").path(key).asLong()<0)throw new IllegalArgumentException("Invalid leave totals");
            long total=tree.path("totals").path("total").asLong(),sum=0;for(String key:List.of("pending","approved","rejected"))sum+=tree.path("totals").path(key).asLong();if(total!=sum)throw new IllegalArgumentException("Leave totals do not reconcile");
            data.put("recentRequests",List.of()); // Only aggregate information crosses this boundary.
            data.keySet().retainAll(Set.of("totals","byDepartment","monthly","byType","recentRequests"));
            Map<String,Object> totals=new LinkedHashMap<>();for(String key:List.of("total","pending","approved","rejected"))totals.put(key,tree.path("totals").path(key).asLong());data.put("totals",totals);
            data.put("byDepartment",sanitiseRows(data.get("byDepartment"),Set.of("departmentId","department"),Set.of("pending","approved","rejected")));
            data.put("monthly",sanitiseRows(data.get("monthly"),Set.of("month"),Set.of("approved","rejected","pending")));
            data.put("byType",sanitiseRows(data.get("byType"),Set.of("type"),Set.of("count")));
        }
        Map<String,Object> doc=new LinkedHashMap<>();doc.put("sourceUrl",url);doc.put("observedAt",observed.toString());doc.put("collectedAt",Instant.now().toString());doc.put("method","authorised_export");doc.put("status",observed.isBefore(Instant.now().minusSeconds(86400))?"stale":"imported_unverified");doc.put("district",d);doc.put("data",data);
        store.save("SECTOR-"+d+"-"+kind,"sector",d,doc);store.audit(auth.getName(),"dataset.imported",kind);return Map.of("status",doc.get("status"),"message","Authorised export imported; verify provenance and freshness before use");
    }
    private List<Map<String,Object>> sanitiseRows(Object input,Set<String> texts,Set<String> numbers){
        if(!(input instanceof List<?> rows)||rows.size()>5000)throw new IllegalArgumentException("Invalid aggregate rows");List<Map<String,Object>> clean=new ArrayList<>();
        for(Object row:rows){if(!(row instanceof Map<?,?> raw))throw new IllegalArgumentException("Invalid aggregate row");Map<String,Object> item=new LinkedHashMap<>();
            for(String key:texts){if(!(raw.get(key) instanceof String value)||value.isBlank()||value.length()>200)throw new IllegalArgumentException("Invalid aggregate label");item.put(key,value);}
            for(String key:numbers){if(!(raw.get(key) instanceof Number value)||!Double.isFinite(value.doubleValue())||value.doubleValue()<0||value.doubleValue()!=Math.rint(value.doubleValue()))throw new IllegalArgumentException("Invalid aggregate count");item.put(key,value.longValue());}clean.add(item);
        }return clean;
    }
}
