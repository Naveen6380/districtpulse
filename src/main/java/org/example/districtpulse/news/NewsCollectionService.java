package org.example.districtpulse.news;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;

@Service
public class NewsCollectionService {

    private final NewsArticleRepository repository;
    private final List<String> feedUrls;

    public NewsCollectionService(NewsArticleRepository repository,
                                 @Value("${news.rss.urls}") List<String> feedUrls) {
        this.repository = repository;
        this.feedUrls = feedUrls;
    }

    /** Fetches all configured feeds, saves new articles, returns how many were new. */
    public int collect() {
        int saved = 0;
        for (String feedUrl : feedUrls) {
            try {
                Document doc = Jsoup.connect(feedUrl)
                        .userAgent("Mozilla/5.0 (DistrictPulse research prototype)")
                        .timeout(15000)
                        .parser(Parser.xmlParser())
                        .get();

                for (Element item : doc.select("item")) {
                    String link = item.select("link").text();
                    String title = item.select("title").text();
                    if (link.isBlank() || title.isBlank()) continue;

                    String hash = sha256(link);
                    if (repository.existsByUrlHash(hash)) continue;

                    NewsArticle a = new NewsArticle();
                    a.setTitle(title.length() > 500 ? title.substring(0, 500) : title);
                    a.setUrl(link);
                    a.setUrlHash(hash);
                    a.setSource(item.select("source").text());
                    a.setContent(Jsoup.parse(item.select("description").text()).text());
                    a.setPublishedAt(parseDate(item.select("pubDate").text()));
                    a.setCollectedAt(LocalDateTime.now());
                    repository.save(a);
                    saved++;
                }
            } catch (Exception e) {
                System.err.println("Feed failed: " + feedUrl + " -> " + e.getMessage());
            }
        }
        return saved;
    }

    private LocalDateTime parseDate(String raw) {
        try {
            return ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .withZoneSameInstant(ZoneId.of("Asia/Kolkata"))
                    .toLocalDateTime();
        } catch (Exception e) {
            return null; // unknown date stays null, never guessed
        }
    }

    private String sha256(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
    }
}
