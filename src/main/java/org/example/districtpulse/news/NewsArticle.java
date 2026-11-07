package org.example.districtpulse.news;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "news_articles")
@Getter
@Setter
@NoArgsConstructor
public class NewsArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, length = 2000)
    private String url;

    // SHA-256 of url: unique key (long varchar can't be unique in utf8mb4)
    @Column(nullable = false, unique = true, length = 64)
    private String urlHash;

    private String source;

    @Column(columnDefinition = "TEXT")
    private String content;

    private LocalDateTime publishedAt;

    private LocalDateTime collectedAt;

    @Column(length = 20)
    private String processingStatus = "NEW";
}
