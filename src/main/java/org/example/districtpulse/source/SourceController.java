package org.example.districtpulse.source;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('collector','administrator')")
@RestController
@RequestMapping("/api/sources")
public class SourceController {

    private final SourceService service;

    public SourceController(SourceService service) {
        this.service = service;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return service.list();
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody SourceInput input) {
        return service.create(input);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody SourceInput input) {
        return service.update(id, input);
    }

    @PatchMapping("/{id}/status")
    public Map<String, Object> setEnabled(@PathVariable String id, @RequestBody Map<String, Boolean> body) {
        return service.setEnabled(id, Boolean.TRUE.equals(body.get("enabled")));
    }

    @PostMapping("/test")
    public Map<String, Object> test(@RequestBody Map<String, String> body) {
        return service.test(body.get("url"), body.get("type"));
    }

    @PostMapping("/{id}/sync")
    public Map<String, Object> sync(@PathVariable String id) {
        return service.sync(id);
    }

    @GetMapping("/{id}/logs")
    public List<Map<String, Object>> logs(@PathVariable String id) {
        return service.logs(id);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
    }
}
