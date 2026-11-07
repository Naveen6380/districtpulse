package org.example.districtpulse.news;

import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/news")
public class NewsController {

    private final NewsCollectionService service;
    private final NewsArticleRepository repository;

    public NewsController(NewsCollectionService service, NewsArticleRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    @PostMapping("/collect")
    public Map<String, Object> collect() {
        int added = service.collect();
        return Map.of("newArticles", added, "totalArticles", repository.count());
    }

    @GetMapping
    public List<NewsArticle> list() {
        return repository.findAll(
                PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "publishedAt"))).getContent();
    }
}