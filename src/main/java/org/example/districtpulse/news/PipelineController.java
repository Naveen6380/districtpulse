package org.example.districtpulse.news;
import java.util.*;
import org.example.districtpulse.platform.DocumentStore;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController @RequestMapping("/api/pipeline") @PreAuthorize("hasAnyRole('collector','administrator')")
public class PipelineController {
    private final PipelineCoordinator pipeline;private final DocumentStore store;private final NewsArticleRepository articles;
    public PipelineController(PipelineCoordinator pipeline,DocumentStore store,NewsArticleRepository articles){this.pipeline=pipeline;this.store=store;this.articles=articles;}
    @PostMapping public Map<String,Object> run(){return pipeline.start(true);}
    @GetMapping("/{id}") public Map<String,Object> status(@PathVariable String id){if(!id.startsWith("JOB-"))throw new IllegalArgumentException("Invalid job id");var job=store.get(id);if(job==null)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);return job;}
    @PostMapping("/retry") public Map<String,Object> retry(){for(var a:articles.findByProcessingStatus("FAILED")){a.setProcessingStatus("NEW");articles.save(a);}return pipeline.start(false);}
}
