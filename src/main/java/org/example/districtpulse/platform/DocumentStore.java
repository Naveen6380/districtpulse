package org.example.districtpulse.platform;
import java.util.*;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
@Service
public class DocumentStore {
    private final DocumentRepository repo;private final JsonMapper mapper=JsonMapper.builder().build();
    public DocumentStore(DocumentRepository repo){this.repo=repo;}
    @SuppressWarnings("unchecked") public Map<String,Object> read(StoredDocument d){return mapper.readValue(d.getPayload(),Map.class);}
    public List<Map<String,Object>> list(String kind,String owner){return repo.findByKindAndOwner(kind,owner).stream().map(this::read).toList();}
    public Map<String,Object> get(String id){return repo.findById(id).map(this::read).orElse(null);}
    @Transactional public Map<String,Object> save(String id,String kind,String owner,Map<String,Object> body){
        StoredDocument d=repo.findById(id).orElseGet(StoredDocument::new);d.setId(id);d.setKind(kind);d.setOwner(owner);d.setPayload(mapper.writeValueAsString(body));d.setUpdatedAt(Instant.now());repo.save(d);return body;
    }
    @Transactional public void delete(String id){repo.deleteById(id);}
    public void audit(String actor,String event,String target){String id="AUD-"+UUID.randomUUID();save(id,"audit",actor,Map.of("id",id,"actor",actor,"event",event,"target",target,"date",Instant.now().toString()));}
}
