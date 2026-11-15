package org.example.districtpulse.source;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "source_sync_logs")
@Getter
@Setter
@NoArgsConstructor
public class SyncLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sourceId;

    private LocalDateTime startedAt;

    private int durationSec;

    // success | failed | partial
    @Column(length = 20)
    private String status;

    private int records;

    @Column(length = 1000)
    private String message;
}
