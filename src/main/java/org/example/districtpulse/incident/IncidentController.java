package org.example.districtpulse.incident;

import org.example.districtpulse.news.NewsArticleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentService service;
    private final IncidentRepository incidentRepo;
    private final NewsArticleRepository articleRepo;

    public IncidentController(IncidentService service, IncidentRepository incidentRepo,
                              NewsArticleRepository articleRepo) {
        this.service = service;
        this.incidentRepo = incidentRepo;
        this.articleRepo = articleRepo;
    }

    @PostMapping("/build")
    public Map<String, Integer> build() {
        return service.build();
    }

    @GetMapping
    public List<Incident> list() {
        return incidentRepo.findAllByOrderByPriorityScoreDescLatestReportedAtDesc();
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        Incident inc = incidentRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("incident", inc);
        result.put("articles", articleRepo.findByIncidentId(id));
        return result;
    }
}