package org.example.districtpulse.source;

import org.example.districtpulse.news.NewsCollectionService;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SourceService {

    private static final Set<String> CATEGORIES = Set.of("farmwise", "government", "news", "weather", "agriculture", "custom");
    private static final Set<String> TYPES = Set.of("rest_api", "public_webpage", "csv", "excel", "json", "xml", "document_upload");

    private final DataSourceRepository repo;
    private final SyncLogRepository logRepo;
    private final SecretBox secretBox;
    private final UrlGuard urlGuard;
    private final NewsCollectionService newsCollection;

    public SourceService(DataSourceRepository repo, SyncLogRepository logRepo, SecretBox secretBox,
                         UrlGuard urlGuard, NewsCollectionService newsCollection) {
        this.repo = repo;
        this.logRepo = logRepo;
        this.secretBox = secretBox;
        this.urlGuard = urlGuard;
        this.newsCollection = newsCollection;
    }

    public List<Map<String, Object>> list() {
        return repo.findAll(Sort.by("id")).stream().map(this::dto).toList();
    }

    public Map<String, Object> create(SourceInput in) {
        DataSourceEntity e = new DataSourceEntity();
        apply(e, in, true);
        return dto(repo.save(e));
    }

    public Map<String, Object> update(String id, SourceInput in) {
        DataSourceEntity e = find(id);
        apply(e, in, false);
        return dto(repo.save(e));
    }

    public Map<String, Object> setEnabled(String id, boolean enabled) {
        DataSourceEntity e = find(id);
        e.setStatus(enabled ? (e.getLastSync() == null ? "pending" : "connected") : "disabled");
        return dto(repo.save(e));
    }

    public Map<String, Object> test(String url, String type) {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            urlGuard.check(url);
            Connection.Response r = fetch(url, null);
            int items = countItems(r.body());
            boolean ok = r.statusCode() >= 200 && r.statusCode() < 300;
            out.put("ok", ok);
            out.put("message", "HTTP " + r.statusCode() + ", " + r.body().length() + " characters"
                    + (items > 0 ? ", RSS/Atom feed with " + items + " items" : "")
                    + (ok ? "" : ". The server did not accept the request."));
        } catch (IllegalArgumentException e) {
            out.put("ok", false);
            out.put("message", e.getMessage());
        } catch (Exception e) {
            out.put("ok", false);
            out.put("message", "Could not connect: " + safe(e));
        }
        return out;
    }

    public Map<String, Object> sync(String id) {
        DataSourceEntity e = find(id);
        if ("disabled".equals(e.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Source is disabled");
        }
        LocalDateTime started = LocalDateTime.now();
        String status;
        int records = 0;
        String message;
        try {
            urlGuard.check(e.getUrl());
            String auth = authHeader(e);
            Connection.Response r = fetch(e.getUrl(), auth);
            if (r.statusCode() < 200 || r.statusCode() >= 300) {
                status = "failed";
                message = "HTTP " + r.statusCode() + ". The server did not accept the request.";
                e.setStatus("failed");
            } else if (countItems(r.body()) > 0) {
                NewsCollectionService.FeedResult res = newsCollection.collectFromFeed(e.getUrl(), auth);
                status = "success";
                records = res.found();
                message = res.found() + " items in the feed, " + res.saved()
                        + " new articles saved. Use Process in News to analyse them.";
                e.setStatus("connected");
                e.setRecordsCollected(res.found());
                e.setLastSync(LocalDateTime.now());
            } else {
                status = "partial";
                message = "Reachable (HTTP " + r.statusCode() + ", " + r.body().length() + " characters). "
                        + "Automatic extraction currently supports RSS/Atom feeds. This source needs a custom adapter.";
                e.setStatus("pending");
                e.setLastSync(LocalDateTime.now());
            }
        } catch (IllegalArgumentException ex) {
            status = "failed";
            message = ex.getMessage();
            e.setStatus("failed");
        } catch (Exception ex) {
            status = "failed";
            message = "Sync failed: " + safe(ex);
            e.setStatus("failed");
        }
        repo.save(e);

        SyncLogEntity log = new SyncLogEntity();
        log.setSourceId(e.getId());
        log.setStartedAt(started);
        log.setDurationSec((int) Math.max(0, Duration.between(started, LocalDateTime.now()).toSeconds()));
        log.setStatus(status);
        log.setRecords(records);
        log.setMessage(message.length() > 1000 ? message.substring(0, 1000) : message);
        return logDto(logRepo.save(log));
    }

    public List<Map<String, Object>> logs(String id) {
        DataSourceEntity e = find(id);
        return logRepo.findTop20BySourceIdOrderByStartedAtDesc(e.getId()).stream().map(this::logDto).toList();
    }

    // ---- helpers ----
    private Connection.Response fetch(String url, String authHeader) throws Exception {
        Connection c = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (DistrictPulse research prototype)")
                .timeout(15000)
                .ignoreContentType(true)
                .ignoreHttpErrors(true)
                .maxBodySize(5_000_000)
                .followRedirects(true);
        if (authHeader != null) c = c.header("Authorization", authHeader);
        return c.execute();
    }

    private int countItems(String body) {
        if (body == null || body.isBlank()) return 0;
        if (!(body.contains("<item") || body.contains("<entry"))) return 0;
        return Jsoup.parse(body, "", Parser.xmlParser()).select("item, entry").size();
    }

    /** HTTP Basic only. Other authentication schemes need a custom adapter. */
    private String authHeader(DataSourceEntity e) {
        if (!e.isAuthRequired() || e.getCredentialUser() == null || e.getCredentialSecretEnc() == null) return null;
        String pair = e.getCredentialUser() + ":" + secretBox.decrypt(e.getCredentialSecretEnc());
        return "Basic " + Base64.getEncoder().encodeToString(pair.getBytes(StandardCharsets.UTF_8));
    }

    private void apply(DataSourceEntity e, SourceInput in, boolean creating) {
        if (creating) {
            require(in.name(), "Name is required");
            require(in.url(), "URL is required");
        }
        if (in.url() != null) {
            urlGuard.check(in.url());
            e.setUrl(in.url().trim());
        }
        if (in.name() != null) e.setName(in.name().trim());
        if (in.description() != null) e.setDescription(in.description().trim());
        if (in.category() != null) {
            if (!CATEGORIES.contains(in.category())) throw new IllegalArgumentException("Unknown category: " + in.category());
            e.setCategory(in.category());
        } else if (creating) {
            e.setCategory("custom");
        }
        if (in.type() != null) {
            if (!TYPES.contains(in.type())) throw new IllegalArgumentException("Unknown type: " + in.type());
            e.setType(in.type());
        } else if (creating) {
            e.setType("public_webpage");
        }
        if (in.coverage() != null) e.setCoverage(in.coverage().trim());
        else if (creating) e.setCoverage("Not specified");
        if (in.accessMethod() != null) e.setAccessMethod(in.accessMethod().trim());
        else if (creating) e.setAccessMethod("Public URL");
        if (in.frequency() != null) e.setFrequency(in.frequency().trim());
        else if (creating) e.setFrequency("On demand");
        if (in.authRequired() != null) e.setAuthRequired(in.authRequired());

        if (in.password() != null && !in.password().isBlank()) {
            if (!secretBox.available()) {
                throw new IllegalArgumentException("Credentials were not saved: SOURCE_SECRET_KEY is not configured on the server");
            }
            e.setCredentialUser(in.username() == null ? "" : in.username().trim());
            e.setCredentialSecretEnc(secretBox.encrypt(in.password()));
            e.setAuthRequired(true);
        }
    }

    private void require(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    }

    private DataSourceEntity find(String id) {
        try {
            long n = Long.parseLong(id.replaceFirst("^SRC-", ""));
            return repo.findById(n).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Source not found"));
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Source not found");
        }
    }

    private String safe(Exception e) {
        String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return m.length() > 300 ? m.substring(0, 300) : m;
    }

    /** Matches the frontend DataSource type. Credentials are never included. Null fields are omitted. */
    private Map<String, Object> dto(DataSourceEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", "SRC-" + e.getId());
        m.put("name", e.getName());
        m.put("url", e.getUrl());
        m.put("description", e.getDescription() == null ? "" : e.getDescription());
        m.put("category", e.getCategory());
        m.put("type", e.getType());
        m.put("coverage", e.getCoverage() == null ? "" : e.getCoverage());
        m.put("accessMethod", e.getAccessMethod() == null ? "" : e.getAccessMethod());
        m.put("authRequired", e.isAuthRequired());
        m.put("frequency", e.getFrequency() == null ? "" : e.getFrequency());
        m.put("status", e.getStatus());
        if (e.getLastSync() != null) m.put("lastSync", e.getLastSync().toString());
        m.put("recordsCollected", e.getRecordsCollected());
        return m;
    }

    private Map<String, Object> logDto(SyncLogEntity l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", "LOG-" + l.getId());
        m.put("sourceId", "SRC-" + l.getSourceId());
        m.put("startedAt", l.getStartedAt().toString());
        m.put("durationSec", l.getDurationSec());
        m.put("status", l.getStatus());
        m.put("records", l.getRecords());
        m.put("message", l.getMessage());
        return m;
    }
}
