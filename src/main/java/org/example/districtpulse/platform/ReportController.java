package org.example.districtpulse.platform;
import java.time.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
@RestController @RequestMapping("/api/districts/{d}/reports") @PreAuthorize("hasAnyRole('collector','administrator')")
public class ReportController {
    private final IntelligenceService intel;private final DocumentStore store;
    public ReportController(IntelligenceService intel,DocumentStore store){this.intel=intel;this.store=store;}
    @SuppressWarnings("unchecked") @PostMapping public Map<String,Object> generate(@PathVariable String d,@RequestBody Map<String,Object> input,Authentication auth){
        intel.district(d);String type=Objects.toString(input.get("type"),"");if(!Set.of("daily","weekly","monthly","critical","department","news_trend","weather","agriculture").contains(type))throw new IllegalArgumentException("Invalid report type");
        LocalDate from,to;try{from=LocalDate.parse(input.get("from").toString());to=LocalDate.parse(input.get("to").toString());}catch(Exception e){throw new IllegalArgumentException("Invalid report dates");}if(from.isAfter(to)||from.plusDays(366).isBefore(to))throw new IllegalArgumentException("Invalid report range");
        List<String> cats=(List<String>)input.getOrDefault("categories",List.of()),deps=(List<String>)input.getOrDefault("departmentIds",List.of()),priorities=(List<String>)input.getOrDefault("priorities",List.of());
        var records=intel.allIncidentList().stream().filter(i->{LocalDate date=OffsetDateTime.parse(i.get("reportedDate").toString()).toLocalDate();return !date.isBefore(from)&&!date.isAfter(to);})
            .filter(i->cats.isEmpty()||cats.contains(i.get("category"))).filter(i->deps.isEmpty()||deps.contains(i.get("departmentId"))).filter(i->priorities.isEmpty()||priorities.contains(i.get("priority"))).filter(i->!"critical".equals(type)||"critical".equals(i.get("priority"))).toList();
        List<Map<String,Object>> sections=new ArrayList<>();sections.add(Map.of("heading","Coverage and verification","kind","data","paragraphs",List.of("District: "+intel.districtName+". Reports use stored news incidents; extraction output requires human review.","Period: "+from+" to "+to+". Reports include all stored incidents within the selected date range.","Weather, agriculture and leave statistics appear only when an authorised dataset has been imported. No missing figures are estimated.")));
        for(String verification:List.of("verified","unverified")){var subset=records.stream().filter(i->"verified".equals(verification)=="verified".equals(i.get("verification"))).toList();List<List<Object>> rows=subset.stream().map(i->List.of(i.get("title"),i.get("category"),i.get("priority"),i.get("departmentId"),i.get("verification"),i.get("reportedDate"))).toList();sections.add(Map.of("heading",verification.equals("verified")?"Human-verified incidents":"Unverified / AI-extracted incidents","kind",verification.equals("verified")?"verified":"ai","table",Map.of("columns",List.of("Incident","Category","Priority","Department","Review","Reported"),"rows",rows)));}
        var evidence=records.stream().map(i->List.of(i.get("title"),i.get("id"),i.get("articleIds").toString())).toList();sections.add(Map.of("heading","Evidence references","kind","data","table",Map.of("columns",List.of("Incident","ID","Supporting article IDs"),"rows",evidence)));
        var result=Map.<String,Object>of("title",type.replace('_',' ')+" district intelligence report","district",intel.districtName,"generatedAt",Instant.now().toString(),"period",from+" to "+to,"sections",sections);store.audit(auth.getName(),"report.generated",d);return result;
    }
}
