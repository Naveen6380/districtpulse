package org.example.districtpulse.platform;

import java.time.*;
import java.util.*;
import org.example.districtpulse.security.*;
import org.example.districtpulse.incident.*;
import org.example.districtpulse.news.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api")
public class PlatformController {
    private final IntelligenceService intel;private final DocumentStore store;private final UserRepository users;private final IncidentRepository incidents;private final NewsArticleRepository articles;private final DtoService dto;
    private final DistrictGeography geography;
    public PlatformController(IntelligenceService intel,DocumentStore store,UserRepository users,IncidentRepository incidents,NewsArticleRepository articles,DtoService dto,DistrictGeography geography){this.intel=intel;this.store=store;this.users=users;this.incidents=incidents;this.articles=articles;this.dto=dto;this.geography=geography;}
    @GetMapping("/health") public Map<String,String> health(){return Map.of("status","up");}
    @GetMapping("/districts") public List<?> districts(){return List.of(Map.of("id",intel.districtId,"name",intel.districtName,"state",intel.state,"isDemo",false,"hasData",true,"geography",geography.metadata()));}
    private boolean officer(Authentication auth){return auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_department_officer"));}
    private String department(Authentication auth){return users.findById(auth.getName()).orElseThrow().getDepartmentId();}
    private void allowed(Authentication auth,String department){if(officer(auth)&&!Objects.equals(department(auth),department))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Outside your department");}
    @GetMapping("/districts/{d}/incidents") public List<?> incidents(@PathVariable String d,Authentication auth){intel.district(d);return intel.incidentList().stream().filter(i->!officer(auth)||Objects.equals(department(auth),i.get("departmentId"))).toList();}
    @GetMapping("/districts/{d}/news/articles") @PreAuthorize("hasAnyRole('collector','administrator')") public List<?> news(@PathVariable String d){intel.district(d);return intel.articleList();}
    @GetMapping("/districts/{d}/news/clusters") @PreAuthorize("hasAnyRole('collector','administrator')") public List<?> clusters(@PathVariable String d){intel.district(d);return intel.clusters();}
    @GetMapping("/districts/{d}/news/insights") @PreAuthorize("hasAnyRole('collector','administrator')") public Map<?,?> insights(@PathVariable String d){intel.district(d);return intel.insights();}
    @GetMapping("/districts/{d}/news/summaries") @PreAuthorize("hasAnyRole('collector','administrator')") public List<?> summaries(@PathVariable String d){intel.district(d);return intel.summaries();}
    @GetMapping("/districts/{d}/overview") public Map<String,Object> overview(@PathVariable String d,Authentication auth){
        intel.district(d);if(!officer(auth))return intel.overview();
        // Officers receive only their departmental records, even if they call the endpoint directly.
        var rows=intel.incidentList().stream().filter(i->Objects.equals(department(auth),i.get("departmentId"))).toList();
        var tasks=actionList(d,auth);Map<String,Object> m=new LinkedHashMap<>();
        m.put("kpis",Map.of("activeIncidents",rows.stream().filter(IntelligenceService::active).count(),"criticalIncidents",rows.stream().filter(IntelligenceService::active).filter(i->"critical".equals(i.get("priority"))).count(),"highPriority",rows.stream().filter(IntelligenceService::active).filter(i->"high".equals(i.get("priority"))).count(),"weatherAlerts",0,"pendingActions",tasks.stream().filter(a->!"completed".equals(a.get("status"))).count(),"newsAnalyzed",0,"incidentsLast7",0,"incidentsPrev7",0));
        m.put("criticalAlerts",rows.stream().filter(i->Set.of("critical","high").contains(i.get("priority"))).toList());m.put("daily",List.of());m.put("weekly",List.of());m.put("byCategory",IntelligenceService.counts(rows,i->(String)i.get("category"),"category"));
        m.put("byLocation",List.of());m.put("departments",departmentList(d,auth));m.put("summaries",List.of());m.put("recentNews",List.of());m.put("sourceHealth",Map.of("connected",0,"failed",0,"disabled",0,"pending",0,"sources",List.of()));return m;
    }
    @GetMapping("/districts/{d}/map") public Map<String,Object> map(@PathVariable String d,Authentication auth){return Map.of("incidents",incidents(d,auth).stream().map(i->(Map<?,?>)i).filter(i->!"rejected".equals(i.get("verification"))).toList(),"overlays",List.of());}
    @GetMapping("/districts/{d}/departments") public List<Map<String,Object>> departmentList(@PathVariable String d,Authentication auth){intel.district(d);return intel.departments().stream().filter(x->!officer(auth)||Objects.equals(department(auth),x.get("departmentId"))).toList();}
    @GetMapping("/districts/{d}/departments/{id}") public Map<String,Object> departmentDetail(@PathVariable String d,@PathVariable String id,Authentication auth){
        intel.district(d);allowed(auth,id);if(!ReferenceData.DEPARTMENTS.containsKey(id))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Department not found");
        return Map.of("department",ReferenceData.DEPARTMENTS.get(id),"stats",intel.departments().stream().filter(x->id.equals(x.get("departmentId"))).findFirst().orElseThrow(),"incidents",intel.incidentList().stream().filter(x->id.equals(x.get("departmentId"))).toList(),"actions",intel.actions().stream().filter(x->id.equals(x.get("departmentId"))).toList());
    }
    @GetMapping("/districts/{d}/notifications") public List<?> notifications(@PathVariable String d,Authentication auth){
        intel.district(d);return intel.incidentList().stream().filter(i->!officer(auth)||Objects.equals(department(auth),i.get("departmentId"))).filter(IntelligenceService::active).filter(i->Set.of("critical","high").contains(i.get("priority"))).limit(20).map(i->Map.of("id","N-"+i.get("id"),"title",i.get("title"),"detail",i.get("description"),"date",i.get("reportedDate"),"priority",i.get("priority"),"link","/incidents/"+i.get("id"),"read",false)).toList();
    }
    @GetMapping("/districts/{d}/search") public List<?> search(@PathVariable String d,@RequestParam(defaultValue="") String q,Authentication auth){intel.district(d);if(q.length()<2)return List.of();if(q.length()>200)throw new IllegalArgumentException("Search is too long");String term=q.toLowerCase(Locale.ROOT);List<Map<String,Object>> found=new ArrayList<>();
        intel.incidentList().stream().filter(i->!officer(auth)||Objects.equals(department(auth),i.get("departmentId"))).filter(i->i.get("title").toString().toLowerCase(Locale.ROOT).contains(term)).limit(30).forEach(i->found.add(Map.of("id",i.get("id"),"title",i.get("title"),"subtitle",i.get("priority"),"link","/incidents/"+i.get("id"),"type","incident")));
        actionList(d,auth).stream().filter(a->a.get("title").toString().toLowerCase(Locale.ROOT).contains(term)).limit(20).forEach(a->found.add(Map.of("id",a.get("id"),"title",a.get("title"),"subtitle",a.get("status"),"link","/actions","type","action")));return found;
    }
    @GetMapping("/districts/{d}/actions") public List<Map<String,Object>> actionList(@PathVariable String d,Authentication auth){intel.district(d);return intel.actions().stream().filter(a->!officer(auth)||Objects.equals(department(auth),a.get("departmentId"))).toList();}
    private static final Set<String> ACTION_FIELDS=Set.of("title","description","departmentId","priority","dueDate","incidentId","status");
    private void validateAction(Map<String,Object> a){
        for(String f:List.of("title","description","departmentId","priority","dueDate"))if(!(a.get(f) instanceof String))throw new IllegalArgumentException("Invalid "+f);
        String title=a.get("title").toString();if(title.isBlank()||title.length()>300||a.get("description").toString().length()>10000)throw new IllegalArgumentException("Invalid action title or description");
        if(!ReferenceData.DEPARTMENTS.containsKey(a.get("departmentId")))throw new IllegalArgumentException("Unknown department");
        if(!Set.of("critical","high","medium","low").contains(a.get("priority")))throw new IllegalArgumentException("Invalid priority");
        try{LocalDate.parse(a.get("dueDate").toString());}catch(Exception e){throw new IllegalArgumentException("Due date must be YYYY-MM-DD");}
        if(!Set.of("open","in_progress","under_review","completed","on_hold").contains(a.get("status")))throw new IllegalArgumentException("Invalid status");
        if(a.get("incidentId")!=null){var inc=findIncident(a.get("incidentId").toString());if(!Objects.equals(dto.incident(inc).get("departmentId"),a.get("departmentId")))throw new IllegalArgumentException("Incident and action department must match");}
    }
    @PostMapping("/actions") @Transactional public Map<String,Object> createAction(@RequestBody Map<String,Object> input,Authentication auth){
        Map<String,Object> a=new LinkedHashMap<>();input.forEach((k,v)->{if(ACTION_FIELDS.contains(k))a.put(k,v);});a.put("status","open");validateAction(a);allowed(auth,a.get("departmentId").toString());
        String id="ACT-"+UUID.randomUUID();a.put("id",id);a.put("createdAt",Instant.now().toString());a.put("comments",List.of());store.save(id,"action",intel.districtId,a);store.audit(auth.getName(),"action.created",id);return a;
    }
    private Map<String,Object> action(String id,Authentication auth){var a=store.get(id);if(a==null||!id.startsWith("ACT-"))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Action not found");allowed(auth,a.get("departmentId").toString());return new LinkedHashMap<>(a);}
    @PatchMapping("/actions/{id}") @Transactional public Map<String,Object> updateAction(@PathVariable String id,@RequestBody Map<String,Object> input,Authentication auth){var a=action(id,auth);input.forEach((k,v)->{if(ACTION_FIELDS.contains(k))a.put(k,v);});validateAction(a);allowed(auth,a.get("departmentId").toString());
        if("completed".equals(a.get("status")))a.put("completedAt",Instant.now().toString());else a.remove("completedAt");store.save(id,"action",intel.districtId,a);store.audit(auth.getName(),"action.updated",id);return a;
    }
    @SuppressWarnings("unchecked") @PostMapping("/actions/{id}/comments") @Transactional public Map<String,Object> comment(@PathVariable String id,@RequestBody Map<String,String> input,Authentication auth){var a=action(id,auth);String text=input.getOrDefault("text","");if(text.isBlank()||text.length()>5000)throw new IllegalArgumentException("Comment must contain 1-5000 characters");
        List<Object> comments=new ArrayList<>((List<Object>)a.get("comments"));comments.add(Map.of("id",UUID.randomUUID().toString(),"author",users.findById(auth.getName()).orElseThrow().getName(),"text",text,"date",Instant.now().toString()));a.put("comments",comments);store.save(id,"action",intel.districtId,a);store.audit(auth.getName(),"action.comment",id);return a;
    }
    public Incident findIncident(String id){try{return incidents.findById(Long.valueOf(id.replaceFirst("^LIVE-",""))).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Incident not found"));}catch(NumberFormatException e){throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Incident not found");}}
    @GetMapping("/intelligence/incidents/{id}") public Map<String,Object> detail(@PathVariable String id,Authentication auth){var inc=findIncident(id);allowed(auth,dto.incident(inc).get("departmentId").toString());return Map.of("incident",dto.incident(inc),"articles",articles.findByIncidentId(inc.getId()).stream().map(dto::article).toList(),"related",List.of());}
    @PatchMapping("/incidents/{id}/verification") @PreAuthorize("hasAnyRole('collector','administrator')") @Transactional
    public Map<String,Object> verify(@PathVariable String id,@RequestBody Map<String,String> body,Authentication auth){var i=findIncident(id);String value=body.getOrDefault("verification","");i.setReviewStatus(switch(value){case "verified"->"VERIFIED";case "needs_review"->"NEEDS_REVIEW";case "rejected"->"REJECTED";case "ai_extracted"->"UNREVIEWED";default->throw new IllegalArgumentException("Invalid verification");});incidents.save(i);event(i,auth,"Review changed to "+value+": "+body.getOrDefault("note",""));return dto.incident(i);}
    @PatchMapping("/incidents/{id}/department") @PreAuthorize("hasAnyRole('collector','administrator')") @Transactional
    public Map<String,Object> assign(@PathVariable String id,@RequestBody Map<String,String> body,Authentication auth){String dep=body.get("departmentId");if(!ReferenceData.DEPARTMENTS.containsKey(dep))throw new IllegalArgumentException("Unknown department");var i=findIncident(id);i.setDepartmentId(dep);if("new".equals(i.getStatus()))i.setStatus("action_assigned");incidents.save(i);event(i,auth,"Assigned to "+dep);return dto.incident(i);}
    @PatchMapping("/incidents/{id}/status") @Transactional public Map<String,Object> status(@PathVariable String id,@RequestBody Map<String,String> body,Authentication auth){var i=findIncident(id);allowed(auth,dto.incident(i).get("departmentId").toString());String status=body.getOrDefault("status","");if(!Set.of("new","under_investigation","action_assigned","resolved","closed").contains(status))throw new IllegalArgumentException("Invalid status");i.setStatus(status);incidents.save(i);event(i,auth,"Status: "+status);return dto.incident(i);}
    private void event(Incident i,Authentication auth,String text){String id="EV-"+UUID.randomUUID();store.save(id,"incident_event","LIVE-"+i.getId(),Map.of("date",Instant.now().toString(),"actor",users.findById(auth.getName()).orElseThrow().getName(),"event",text));store.audit(auth.getName(),text,"LIVE-"+i.getId());}
    @PatchMapping("/incidents/{id}/location") @PreAuthorize("hasAnyRole('collector','administrator')") @Transactional
    public Map<String,Object> locate(@PathVariable String id,@RequestBody Map<String,Object> body,Authentication auth){
        if(!Boolean.TRUE.equals(body.get("verified")))throw new IllegalArgumentException("Coordinates require human verification");
        if(!(body.get("lat") instanceof Number lat)||!(body.get("lng") instanceof Number lng)||!Double.isFinite(lat.doubleValue())||!Double.isFinite(lng.doubleValue())||Math.abs(lat.doubleValue())>90||Math.abs(lng.doubleValue())>180)throw new IllegalArgumentException("Invalid coordinates");
        String source=Objects.toString(body.get("sourceUrl"),"");java.net.URI uri;try{uri=java.net.URI.create(source);}catch(Exception e){throw new IllegalArgumentException("Source URL required");}if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||source.length()>2000)throw new IllegalArgumentException("Public HTTPS source URL required");
        var i=findIncident(id);i.setLatitude(lat.doubleValue());i.setLongitude(lng.doubleValue());i.setLocationEvidenceUrl(source);incidents.save(i);event(i,auth,"Location verified against "+source);return dto.incident(i);
    }
    private Map<String,Object> defaults(){return new LinkedHashMap<>(Map.of("widgets",List.of("incident_overview","critical_alerts","news_intelligence","trend_analysis","source_health"),"categories",List.of(),"locations",List.of(),"alerts",Map.of("critical",true,"high",true,"weather",true,"overdueActions",true,"email",false)));}
    @GetMapping("/users/me/dashboard-preferences") public Map<String,Object> prefs(Authentication auth){var p=store.get("PREF-"+auth.getName());return p==null?defaults():p;}
    @PutMapping("/users/me/dashboard-preferences") public Map<String,Object> savePrefs(@RequestBody Map<String,Object> input,Authentication auth){
        Set<String> widgets=Set.of("incident_overview","critical_alerts","news_intelligence","weather_summary","agriculture_summary","department_performance","action_tracker","incident_map","trend_analysis","source_health");
        if(!(input.get("widgets") instanceof List<?> w)||w.size()>10||!widgets.containsAll(w)||new HashSet<>(w).size()!=w.size())throw new IllegalArgumentException("Invalid widgets");
        if(!(input.get("categories") instanceof List<?> cats)||cats.size()>10||!Set.of("flood","weather","agriculture","infrastructure","grievance","health","education","public_safety","governance","other").containsAll(cats))throw new IllegalArgumentException("Invalid categories");
        if(!(input.get("locations") instanceof List<?> locations)||locations.size()>100||locations.stream().anyMatch(l->!(l instanceof String)||l.toString().length()>200))throw new IllegalArgumentException("Invalid locations");
        if(!(input.get("alerts") instanceof Map<?,?> alerts)||!alerts.keySet().equals(Set.of("critical","high","weather","overdueActions","email"))||alerts.values().stream().anyMatch(v->!(v instanceof Boolean)))throw new IllegalArgumentException("Invalid alerts");
        Map<String,Object> p=new LinkedHashMap<>();for(String k:List.of("widgets","categories","locations","alerts"))p.put(k,input.get(k));p.put("updatedAt",Instant.now().toString());return store.save("PREF-"+auth.getName(),"preferences",auth.getName(),p);
    }
    @DeleteMapping("/users/me/dashboard-preferences") public Map<String,Object> reset(Authentication auth){store.delete("PREF-"+auth.getName());return defaults();}
}
