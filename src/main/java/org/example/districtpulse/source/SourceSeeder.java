package org.example.districtpulse.source;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/** First start only: registers the RSS feeds from application.properties as real, visible sources. */
@Component
public class SourceSeeder implements ApplicationRunner {

    private final DataSourceRepository repo;
    private final List<String> feedUrls;

    public SourceSeeder(DataSourceRepository repo, @Value("${news.rss.urls}") List<String> feedUrls) {
        this.repo = repo;
        this.feedUrls = feedUrls;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repo.count() > 0) return;
        int n = 1;
        for (String url : feedUrls) {
            DataSourceEntity e = new DataSourceEntity();
            e.setName(feedUrls.size() == 1 ? "Google News RSS (Pudukkottai)" : "News RSS feed " + n);
            e.setUrl(url);
            e.setDescription("Public news search feed collected by the DistrictPulse backend.");
            e.setCategory("news");
            e.setType("xml");
            e.setCoverage("Pudukkottai district");
            e.setAccessMethod("Public RSS feed");
            e.setFrequency("On demand");
            e.setStatus("pending");
            repo.save(e);
            n++;
        }
    }
}
