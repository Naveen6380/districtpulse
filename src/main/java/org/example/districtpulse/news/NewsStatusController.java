package org.example.districtpulse.news;
import java.util.*;
import org.example.districtpulse.ai.AiProviderState;
import org.example.districtpulse.platform.IntelligenceService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController @PreAuthorize("hasAnyRole('collector','administrator')")
public class NewsStatusController {
    private final IntelligenceService intel;private final AiProviderState provider;private final NewsArticleRepository articles;
    public NewsStatusController(IntelligenceService intel,AiProviderState provider,NewsArticleRepository articles){this.intel=intel;this.provider=provider;this.articles=articles;}
    @GetMapping("/api/districts/{d}/news/status") public Map<String,Object> status(@PathVariable String d){intel.district(d);var now=java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata"));var start=now.toLocalDate().minusDays(29).atStartOfDay();var rows=articles.findAll().stream().filter(a->a.getPublishedAt()!=null&&!a.getPublishedAt().isBefore(start)&&!a.getPublishedAt().isAfter(now)).toList();return Map.of("collected",rows.size(),"pending",rows.stream().filter(a->"NEW".equals(a.getProcessingStatus())).count(),"failed",rows.stream().filter(a->"FAILED".equals(a.getProcessingStatus())).count(),"processed",rows.stream().filter(a->"PROCESSED".equals(a.getProcessingStatus())).count(),"excluded",rows.stream().filter(a->"Not relevant".equals(a.getCategory())).count(),"provider",provider.current());}
}
