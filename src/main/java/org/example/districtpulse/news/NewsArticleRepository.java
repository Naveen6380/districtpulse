package org.example.districtpulse.news;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {

    boolean existsByUrlHash(String urlHash);

    List<NewsArticle> findByProcessingStatus(String processingStatus);

    List<NewsArticle> findByProcessingStatusAndCategoryNot(String processingStatus, String category);

    List<NewsArticle> findByIncidentId(Long incidentId);

    @Query("select a.category, count(a) from NewsArticle a where a.processingStatus = 'PROCESSED' and a.category <> 'Not relevant' group by a.category")
    List<Object[]> countByCategory();

    @Query("select a.severity, count(a) from NewsArticle a where a.processingStatus = 'PROCESSED' and a.category <> 'Not relevant' group by a.severity")
    List<Object[]> countBySeverity();

    @Query("select a.taluk, count(a) from NewsArticle a where a.processingStatus = 'PROCESSED' and a.category <> 'Not relevant' group by a.taluk")
    List<Object[]> countByTaluk();
}