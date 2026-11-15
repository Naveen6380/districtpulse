package org.example.districtpulse.source;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "data_sources")
@Getter
@Setter
@NoArgsConstructor
public class DataSourceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 2000)
    private String url;

    @Column(length = 1000)
    private String description;

    @Column(length = 30)
    private String category;

    @Column(length = 30)
    private String type;

    @Column(length = 200)
    private String coverage;

    @Column(length = 200)
    private String accessMethod;

    private boolean authRequired;

    @Column(length = 100)
    private String frequency;

    // connected | failed | disabled | pending
    @Column(length = 20)
    private String status = "pending";

    private LocalDateTime lastSync;

    private int recordsCollected;

    // Credentials are encrypted (AES-GCM). They are never returned by any API.
    @Column(length = 200)
    private String credentialUser;

    @Column(columnDefinition = "TEXT")
    private String credentialSecretEnc;
}
