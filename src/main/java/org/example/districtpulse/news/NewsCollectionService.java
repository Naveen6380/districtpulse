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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;

@Service
public class NewsCollectionService {

    /** found = items in the feed, saved = new articles stored (duplicates are skipped by URL hash). */
    public record FeedResult(int found, int saved) {
    }

    private final NewsArticleRepository repository;
    private final List<String> feedUrls;
    private final org.example.districtpulse.source.UrlGuard guard;
    private final org.example.districtpulse.source.DataSourceRepository sources;

    public NewsCollectionService(NewsArticleRepository repository,
                                 @Value("${news.rss.urls}") List<String> feedUrls,
                                 org.example.districtpulse.source.UrlGuard guard,
                                 org.example.districtpulse.source.DataSourceRepository sources) {
        this.repository = repository;
        this.feedUrls = feedUrls; this.guard=guard; this.sources=sources;
    }

    /** Fetches all configured feeds, saves new articles, returns how many were new. */
    public int collect() {
        int saved = 0;
        int failures=0;
        for (var source : sources.findAll().stream().filter(s->"xml".equals(s.getType()) && !s.isAuthRequired() && !"disabled".equals(s.getStatus())).toList()) {
            String feedUrl=source.getUrl();
            try {
                var result=collectFromFeed(feedUrl, null);saved+=result.saved();source.setStatus("connected");source.setRecordsCollected(result.found());source.setLastSync(LocalDateTime.now(ZoneId.of("Asia/Kolkata")));sources.save(source);
            } catch (Exception e) {
                failures++;source.setStatus("failed");sources.save(source);
            }
        }
        if(failures>0) throw new IllegalStateException("One or more RSS sources failed; inspect source status");
        return saved;
    }

    /** Reads one RSS or Atom feed. authHeader is an optional Authorization header value (never logged). */
    public FeedResult collectFromFeed(String feedUrl, String authHeader) throws Exception {
        guard.check(feedUrl);
        var connection = Jsoup.connect(feedUrl)
                .followRedirects(false)
                .maxBodySize(5_000_000)
                .userAgent("Mozilla/5.0 (DistrictPulse research prototype)")
                .timeout(15000)
                .ignoreContentType(true)
                .parser(Parser.xmlParser());
        if (authHeader != null) {
            connection = connection.header("Authorization", authHeader);
        }
        Document doc = connection.get();

        int found = 0;
        int saved = 0;
        for (Element item : doc.select("item, entry")) {
            String link = item.select("link").text();
            if (link.isBlank()) link = item.select("link").attr("href");
            String title = item.select("title").text();
            if (link.isBlank() || title.isBlank()) continue;
            found++;

            try { var uri=java.net.URI.create(link); if(!SetOfSchemes(uri.getScheme())) continue; } catch(Exception invalid){ continue; }
            String hash = sha256(link);
            if (repository.existsByUrlHash(hash)) continue;

            String body = item.select("description").text();
            if (body.isBlank()) body = item.select("summary").text();
            if (body.isBlank()) body = item.select("content").text();
            String date = item.select("pubDate").text();
            if (date.isBlank()) date = item.select("published").text();
            if (date.isBlank()) date = item.select("updated").text();

            LocalDateTime published=parseDate(date);
            LocalDateTime now=LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
            if(published==null || published.isBefore(now.minusDays(30)) || published.isAfter(now.plusMinutes(5))) continue;
            NewsArticle a = new NewsArticle();
            a.setTitle(title.length() > 500 ? title.substring(0, 500) : title);
            a.setUrl(link);
            a.setUrlHash(hash);
            a.setSource(item.select("source").text());
            a.setContent(Jsoup.parse(body).text());
            a.setPublishedAt(published);
            a.setCollectedAt(now);
            repository.save(a);
            saved++;
        }
        return new FeedResult(found, saved);
    }

    private boolean SetOfSchemes(String scheme) { return "http".equalsIgnoreCase(scheme)||"https".equalsIgnoreCase(scheme); }

    private LocalDateTime parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .withZoneSameInstant(ZoneId.of("Asia/Kolkata")).toLocalDateTime();
        } catch (Exception ignored) {
            // try ISO below
        }
        try {
            return OffsetDateTime.parse(raw).atZoneSameInstant(ZoneId.of("Asia/Kolkata")).toLocalDateTime();
        } catch (Exception e) {
            return null; // unknown date stays null, never guessed
        }
    }

    private String sha256(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
    }
}
