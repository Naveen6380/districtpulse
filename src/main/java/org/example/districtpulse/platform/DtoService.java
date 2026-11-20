package org.example.districtpulse.platform;
import org.example.districtpulse.incident.*;
import org.example.districtpulse.news.*;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
@Service
public class DtoService {
    private final NewsArticleRepository articles;private final DocumentStore store;
    public DtoService(NewsArticleRepository articles,DocumentStore store){this.articles=articles;this.store=store;}
    public static String date(LocalDateTime date){return date==null?"":date.atZone(ZoneId.of("Asia/Kolkata")).toOffsetDateTime().toString();}
    public static Map<String,Object> location(String taluk,String locality){return Map.of("name",known(locality)?locality:known(taluk)?taluk:"Location not stated in report","taluk",known(taluk)?taluk:"Unresolved","resolved",false);}
    private static boolean known(String s){return s!=null&&!s.isBlank()&&!s.equalsIgnoreCase("unknown");}
    public Map<String,Object> article(NewsArticle a){
        Map<String,Object> m=new LinkedHashMap<>();m.put("id","LIVE-A"+a.getId());m.put("headline",a.getTitle());m.put("source",a.getSource()==null?"Unknown source":a.getSource());
        m.put("publishedDate",date(a.getPublishedAt()));m.put("location",location(a.getTaluk(),a.getLocality()));m.put("category",ReferenceData.category(a.getCategory()));
        m.put("priority",a.getSeverity()==null?"low":a.getSeverity().toLowerCase());m.put("aiSummary",a.getSummary()==null?"":a.getSummary());
        m.put("processingStatus",switch(a.getProcessingStatus()){case "PROCESSED"->"processed";case "FAILED"->"failed";default->"pending";});
        m.put("url",a.getUrl());if(a.getIncidentId()!=null){m.put("incidentId","LIVE-"+a.getIncidentId());m.put("clusterId","LIVE-CL-"+a.getIncidentId());}
        m.put("isDemo",false);m.put("confidence",a.getConfidence());m.put("evidence",a.getEvidence());m.put("collectedAt",date(a.getCollectedAt()));m.put("extractionMode",a.getExtractionMode());m.put("processingError",a.getProcessingError());return m;
    }
    public Map<String,Object> incident(Incident i){
        List<NewsArticle> supporting=articles.findByIncidentId(i.getId());String category=ReferenceData.category(i.getCategory());
        Map<String,Object> m=new LinkedHashMap<>();m.put("id","LIVE-"+i.getId());m.put("title",i.getTitle());
        m.put("description",i.getArticleCount()+" supporting news reports. "+i.getPriorityReason());
        m.put("aiSummary",supporting.stream().map(NewsArticle::getSummary).filter(Objects::nonNull).findFirst().orElse("Not yet summarised"));
        m.put("category",category);Map<String,Object> loc=new LinkedHashMap<>(location(i.getTaluk(),null));if(i.getLatitude()!=null&&i.getLongitude()!=null&&i.getLocationEvidenceUrl()!=null){loc.put("lat",i.getLatitude());loc.put("lng",i.getLongitude());loc.put("resolved",true);loc.put("evidenceUrl",i.getLocationEvidenceUrl());}m.put("location",loc);m.put("reportedDate",date(i.getFirstReportedAt()));m.put("latestReportedAt",date(i.getLatestReportedAt()));
        m.put("priority",i.getPriority()==null?"low":i.getPriority().toLowerCase());m.put("priorityReason",i.getPriorityReason());m.put("status",i.getStatus());
        m.put("verification",switch(i.getReviewStatus()){case "VERIFIED"->"verified";case "NEEDS_REVIEW"->"needs_review";case "REJECTED"->"rejected";default->"ai_extracted";});
        m.put("departmentId",i.getDepartmentId()==null?ReferenceData.department(category):i.getDepartmentId());
        m.put("confidence",supporting.stream().map(NewsArticle::getConfidence).filter(Objects::nonNull).mapToDouble(Double::doubleValue).min().orElse(0));
        m.put("articleIds",supporting.stream().map(a->"LIVE-A"+a.getId()).toList());m.put("sourceCount",supporting.stream().map(NewsArticle::getSource).filter(Objects::nonNull).distinct().count());
        m.put("relatedIncidentIds",List.of());m.put("history",store.list("incident_event","LIVE-"+i.getId()));m.put("isDemo",false);return m;
    }
}
