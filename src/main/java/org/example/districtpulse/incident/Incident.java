package org.example.districtpulse.incident;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "incidents")
@Getter
@Setter
@NoArgsConstructor
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 500)
    private String title;

    @Column(length = 50)
    private String category;

    @Column(length = 20)
    private String severity;

    @Column(length = 100)
    private String taluk;

    private LocalDateTime firstReportedAt;
    private LocalDateTime latestReportedAt;

    private int articleCount;

    private int priorityScore;

    @Column(length = 20)
    private String priority;

    @Column(length = 500)
    private String priorityReason;

    // Human must be able to review AI/rule output
    @Column(length = 20)
    private String reviewStatus = "UNREVIEWED";
}