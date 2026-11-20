package org.example.districtpulse.platform;

import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.example.districtpulse.incident.*;
import org.example.districtpulse.news.*;
import org.example.districtpulse.source.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class IntelligenceService {
    public final String districtId,districtName,state;
    private final IncidentRepository incidents;private final NewsArticleRepository articles;private final DtoService dto;
    private final DocumentStore store;private final SourceService sources;
    public IntelligenceService(IncidentRepository incidents,NewsArticleRepository articles,DtoService dto,DocumentStore store,SourceService sources,
        @Value("${district.id}") String districtId,@Value("${district.name}") String districtName,@Value("${district.state}") String state){
        this.incidents=incidents;this.articles=articles;this.dto=dto;this.store=store;this.sources=sources;this.districtId=districtId;this.districtName=districtName;this.state=state;
    }
    public void district(String id){if(!districtId.equals(id))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"District is not configured");}
    public List<Incident> recentIncidents(){LocalDateTime start=LocalDate.now(ZoneId.of("Asia/Kolkata")).minusDays(29).atStartOfDay();return incidents.findAllByOrderByPriorityScoreDescLatestReportedAtDesc().stream().filter(i->i.getLatestReportedAt()!=null&&!i.getLatestReportedAt().isBefore(start)).toList();}
    public List<NewsArticle> recentArticles(){LocalDateTime start=LocalDate.now(ZoneId.of("Asia/Kolkata")).minusDays(29).atStartOfDay();LocalDateTime now=LocalDateTime.now(ZoneId.of("Asia/Kolkata"));return articles.findAll().stream().filter(a->a.getPublishedAt()!=null&&!a.getPublishedAt().isBefore(start)&&!a.getPublishedAt().isAfter(now)&&!"Not relevant".equals(a.getCategory())).sorted(Comparator.comparing(NewsArticle::getPublishedAt).reversed()).toList();}
    public List<Map<String,Object>> allIncidentList(){return incidents.findAllByOrderByPriorityScoreDescLatestReportedAtDesc().stream().map(dto::incident).toList();}
    public List<Map<String,Object>> incidentList(){return recentIncidents().stream().map(dto::incident).toList();}
    public List<Map<String,Object>> articleList(){return recentArticles().stream().map(dto::article).toList();}
    public List<Map<String,Object>> actions(){return store.list("action",districtId);}
    public static boolean active(Map<String,Object> i){return !Set.of("closed","resolved").contains(i.get("status"))&&!"rejected".equals(i.get("verification"));}
    public List<Map<String,Object>> departments(){
        var all=incidentList();var actions=actions();List<Map<String,Object>> result=new ArrayList<>();
        ReferenceData.DEPARTMENTS.forEach((id,dept)->{
            var local=all.stream().filter(i->id.equals(i.get("departmentId"))).toList();var tasks=actions.stream().filter(a->id.equals(a.get("departmentId"))).toList();
            Map<String,Object> m=new LinkedHashMap<>();m.put("departmentId",id);m.put("department",dept.get("name"));m.put("shortName",dept.get("shortName"));
            m.put("activeIncidents",local.stream().filter(IntelligenceService::active).count());m.put("highPriority",local.stream().filter(IntelligenceService::active).filter(i->"high".equals(i.get("priority"))).count());
            m.put("critical",local.stream().filter(IntelligenceService::active).filter(i->"critical".equals(i.get("priority"))).count());
            m.put("pendingActions",tasks.stream().filter(a->!"completed".equals(a.get("status"))).count());m.put("completedActions",tasks.stream().filter(a->"completed".equals(a.get("status"))).count());result.add(m);
        });return result;
    }
    public static List<Map<String,Object>> counts(List<Map<String,Object>> rows,Function<Map<String,Object>,String> key,String label){
        return rows.stream().collect(Collectors.groupingBy(key,Collectors.counting())).entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).map(e->{Map<String,Object> m=new LinkedHashMap<>();m.put(label,e.getKey());m.put("count",e.getValue());return m;}).toList();
    }
    public List<Map<String,Object>> summaries(){
        return recentIncidents().stream().filter(i->!"REJECTED".equals(i.getReviewStatus())).limit(12).map(i->{
            var inc=dto.incident(i);Map<String,Object> s=new LinkedHashMap<>();s.put("id","SUMMARY-"+i.getId());s.put("title",i.getTitle());s.put("whatHappened",inc.get("aiSummary"));
            s.put("where",i.getTaluk()==null?"Unknown":i.getTaluk());s.put("when",DtoService.date(i.getFirstReportedAt()));s.put("whyImportant",i.getPriorityReason());
            s.put("pattern",i.getArticleCount()>1?i.getArticleCount()+" reports cover this incident; repeated reporting is not proof of repeated events.":"No recurrence established");
            s.put("department",ReferenceData.DEPARTMENTS.get((String)inc.get("departmentId")).get("name"));s.put("followUp","AI-generated suggestion: verify source evidence and request a departmental assessment before taking action.");
            s.put("confidence",inc.get("confidence"));s.put("supportingArticleIds",inc.get("articleIds"));s.put("incidentIds",List.of(inc.get("id")));s.put("category",inc.get("category"));s.put("priority",inc.get("priority"));return s;
        }).toList();
    }
    @SuppressWarnings("unchecked") public Map<String,Object> insights(){
        var rows=incidentList().stream().filter(i->!"rejected".equals(i.get("verification"))).toList();
        var categories=counts(rows,i->(String)i.get("category"),"category");
        var locations=counts(rows,i->(String)((Map<String,Object>)i.get("location")).get("name"),"location").stream().filter(r->!"Location not stated in report".equals(r.get("location"))).toList();
        var recurrence=rows.stream().collect(Collectors.groupingBy(i->i.get("category")+"|"+((Map<String,Object>)i.get("location")).get("taluk")));
        List<Map<String,Object>> recurring=new ArrayList<>();recurrence.values().stream().filter(g->g.size()>1).forEach(g->{var i=g.get(0);var l=(Map<String,Object>)i.get("location");if(!"Unresolved".equals(l.get("taluk")))recurring.add(Map.of("label",i.get("category")+" issues in "+l.get("taluk"),"count",g.size(),"category",i.get("category")));});
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Kolkata"));List<Map<String,Object>> patterns=new ArrayList<>();
        for(var c:categories){String cat=(String)c.get("category");long recent=recentIncidents().stream().filter(i->cat.equals(ReferenceData.category(i.getCategory()))&& !i.getFirstReportedAt().toLocalDate().isBefore(today.minusDays(6))).count();
            long prev=recentIncidents().stream().filter(i->cat.equals(ReferenceData.category(i.getCategory()))&&i.getFirstReportedAt().toLocalDate().isBefore(today.minusDays(6))&&!i.getFirstReportedAt().toLocalDate().isBefore(today.minusDays(13))).count();
            if(recent>=3&&recent>prev)patterns.add(Map.of("label","Increased "+cat+" reporting","detail",recent+" incidents in the last 7 days versus "+prev+" in the previous 7 days. Observed reporting trend; not a forecast."));
        }
        var concerns=departments().stream().filter(d->((Number)d.get("activeIncidents")).longValue()>0).map(d->Map.of("departmentId",d.get("departmentId"),"department",d.get("department"),"count",d.get("activeIncidents"))).toList();
        var developments=articleList().stream().filter(a->"processed".equals(a.get("processingStatus"))).limit(5).map(a->Map.of("label",a.get("headline"),"detail",a.get("aiSummary"),"articleId",a.get("id"))).toList();
        return Map.of("recurringIssues",recurring,"emergingPatterns",patterns,"frequentLocations",locations,"categoryFrequency",categories,"departmentConcerns",concerns,"developments",developments);
    }
    public List<Map<String,Object>> clusters(){return recentIncidents().stream().filter(i->i.getArticleCount()>1).map(i->{
        var supporting=articles.findByIncidentId(i.getId());var inc=dto.incident(i);Map<String,Object> m=new LinkedHashMap<>();m.put("id","LIVE-CL-"+i.getId());m.put("incidentId",inc.get("id"));m.put("title",i.getTitle());m.put("articleIds",inc.get("articleIds"));
        m.put("sources",supporting.stream().map(NewsArticle::getSource).filter(Objects::nonNull).distinct().toList());m.put("combinedSummary",inc.get("aiSummary"));m.put("firstReported",DtoService.date(i.getFirstReportedAt()));m.put("latestReported",DtoService.date(i.getLatestReportedAt()));m.put("category",inc.get("category"));m.put("priority",inc.get("priority"));return m;
    }).toList();}
    public Map<String,Object> overview(){
        var all=incidentList();var valid=all.stream().filter(i->!"rejected".equals(i.get("verification"))).toList();var active=valid.stream().filter(IntelligenceService::active).toList();var news=articleList();var sources=this.sources.list();
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Kolkata"));List<Map<String,Object>> daily=new ArrayList<>();
        for(int n=29;n>=0;n--){LocalDate day=today.minusDays(n);daily.add(Map.of("date",day.toString(),"count",valid.stream().filter(i->((String)i.get("reportedDate")).startsWith(day.toString())).count()));}
        List<Map<String,Object>> weekly=new ArrayList<>();for(int w=0;w<4;w++){long count=0;for(int x=2+w*7;x<9+w*7;x++)count+=((Number)daily.get(x).get("count")).longValue();weekly.add(Map.of("week","Week "+(w+1),"count",count));}
        Map<String,Object> health=new LinkedHashMap<>();for(String status:List.of("connected","failed","disabled","pending"))health.put(status,sources.stream().filter(s->status.equals(s.get("status"))).count());health.put("sources",sources);
        sources.stream().map(s->s.get("lastSync")).filter(Objects::nonNull).map(Object::toString).max(String::compareTo).ifPresent(s->health.put("lastSync",s));
        Map<String,Object> kpis=new LinkedHashMap<>();kpis.put("activeIncidents",active.size());kpis.put("criticalIncidents",active.stream().filter(i->"critical".equals(i.get("priority"))).count());kpis.put("highPriority",active.stream().filter(i->"high".equals(i.get("priority"))).count());
        var weather=store.get("SECTOR-"+districtId+"-weather");kpis.put("weatherAlertsAvailable",weather!=null);long warningCount=0;
        if(weather!=null&&weather.get("data") instanceof Map<?,?> data&&data.get("warnings") instanceof List<?> warnings)warningCount=warnings.stream().filter(w->w instanceof Map<?,?> m&&Boolean.TRUE.equals(m.get("active"))).count();
        kpis.put("weatherAlerts",warningCount);kpis.put("pendingActions",actions().stream().filter(a->!"completed".equals(a.get("status"))).count());kpis.put("newsAnalyzed",news.stream().filter(a->"processed".equals(a.get("processingStatus"))).count());
        kpis.put("incidentsLast7",recentIncidents().stream().filter(i->!i.getFirstReportedAt().toLocalDate().isBefore(today.minusDays(6))).count());kpis.put("incidentsPrev7",recentIncidents().stream().filter(i->!i.getFirstReportedAt().toLocalDate().isBefore(today.minusDays(13))&&i.getFirstReportedAt().toLocalDate().isBefore(today.minusDays(6))).count());
        Map<String,Object> result=new LinkedHashMap<>();result.put("kpis",kpis);result.put("criticalAlerts",active.stream().filter(i->Set.of("critical","high").contains(i.get("priority"))).limit(6).toList());result.put("daily",daily);result.put("weekly",weekly);result.put("byCategory",counts(valid,i->(String)i.get("category"),"category"));
        result.put("byLocation",insights().get("frequentLocations"));result.put("departments",departments());result.put("summaries",summaries());result.put("recentNews",news.stream().filter(a->"processed".equals(a.get("processingStatus"))).limit(6).toList());result.put("sourceHealth",health);return result;
    }
}
