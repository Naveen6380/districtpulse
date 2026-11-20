package org.example.districtpulse.integration;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
@RestController @RequestMapping("/api/integrations/lms") @PreAuthorize("hasAnyRole('collector','administrator')")
public class LmsReferenceController {
    private final LmsReferenceService service;
    public LmsReferenceController(LmsReferenceService service){this.service=service;}
    @GetMapping public Map<String,Object> get(){return service.get();}
    @PostMapping("/sync") public Map<String,Object> sync(Authentication auth){return service.sync(auth.getName());}
}
