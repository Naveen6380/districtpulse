package org.example.districtpulse.platform;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController @RequestMapping("/api/audit") @PreAuthorize("hasRole('administrator')")
public class AuditController {
    private final DocumentRepository repo;private final DocumentStore store;
    public AuditController(DocumentRepository repo,DocumentStore store){this.repo=repo;this.store=store;}
    @GetMapping public List<?> list(){return repo.findByKind("audit").stream().sorted(Comparator.comparing(StoredDocument::getUpdatedAt).reversed()).limit(200).map(store::read).toList();}
}
