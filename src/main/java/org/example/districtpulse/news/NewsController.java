package org.example.districtpulse.news;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/news")
public class NewsController {

    private final NewsCollectionService collectionService;
    private final NewsProcessingService processingService;
    private final NewsArticleRepository repository;

    public NewsController(NewsCollectionService collectionService,
                          NewsProcessingService processingService,
                          NewsArticleRepository repository) {
        this.collectionService = collectionService;
        this.processingService = processingService;
        this.repository = repository;
    }

    @PostMapping("/collect")
    public Map<String, Object> collect() {
        int added = collectionService.collect();
        return Map.of("newArticles", added, "totalArticles", repository.count());
    }

    @PostMapping("/process")
    public Map<String, Object> process() {
        return Map.of("processed", processingService.processPending());
    }

    @GetMapping
    public List<NewsArticle> list() {
        return repository.findAll(
                PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "publishedAt"))).getContent();
    }

    @GetMapping("/intelligence")
    public Map<String, Object> intelligence() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", "DEMO (rule-based, not real AI)");
        result.put("byCategory", toMap(repository.countByCategory()));
        result.put("bySeverity", toMap(repository.countBySeverity()));
        result.put("byTaluk", toMap(repository.countByTaluk()));
        return result;
    }

    private Map<String, Long> toMap(List<Object[]> rows) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (Object[] row : rows) {
            m.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        return m;
    }
}