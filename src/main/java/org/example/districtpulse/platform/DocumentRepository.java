package org.example.districtpulse.platform;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DocumentRepository extends JpaRepository<StoredDocument,String> {List<StoredDocument> findByKindAndOwner(String kind,String owner); List<StoredDocument> findByKind(String kind);}
