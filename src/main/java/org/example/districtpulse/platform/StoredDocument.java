package org.example.districtpulse.platform;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name="platform_documents",indexes=@Index(columnList="kind,owner")) @Getter @Setter @NoArgsConstructor
public class StoredDocument {
    @Id private String id;
    private String kind;
    private String owner;
    @Lob @Column(columnDefinition="LONGTEXT") private String payload;
    private Instant updatedAt;
    @Version private Long version;
}
