package org.example.districtpulse.incident;

import org.example.districtpulse.news.NewsArticle;
import org.example.districtpulse.news.NewsArticleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class IncidentService {

    private static final double THRESHOLD = 0.4;   // min word overlap to treat as same incident
    private static final long MAX_GAP_DAYS = 3;    // articles must be within 3 days

    // District name must NOT count as similarity, every article has it
    private static final Set<String> STOP = Set.of(
            "the", "in", "of", "to", "a", "an", "and", "for", "on", "at", "with", "by", "from", "is", "as",
            "pudukkottai", "pudukottai", "tamil", "nadu", "tn");

    private final NewsArticleRepository articleRepo;
    private final IncidentRepository incidentRepo;

    public IncidentService(NewsArticleRepository articleRepo, IncidentRepository incidentRepo) {
        this.articleRepo = articleRepo;
        this.incidentRepo = incidentRepo;
    }

    /** Rebuilds all incidents from scratch. Safe to run many times. */
    @Transactional
    public Map<String, Integer> build() {
        // 1. reset
        for (NewsArticle a : articleRepo.findAll()) {
            if (a.getIncidentId() != null) {
                a.setIncidentId(null);
                articleRepo.save(a);
            }
        }
        incidentRepo.deleteAll();

        // 2. relevant articles, oldest first
        List<NewsArticle> articles = new ArrayList<>(
                articleRepo.findByProcessingStatusAndCategoryNot("PROCESSED", "Not relevant"));
        articles.sort(Comparator.comparing(this::reportedAt));

        // 3. group
        List<Incident> incidents = new ArrayList<>();
        Map<Long, Set<String>> tokenMap = new HashMap<>();

        for (NewsArticle a : articles) {
            LocalDateTime when = reportedAt(a);
            Set<String> tokens = tokens(a.getTitle());

            Incident best = null;
            double bestSim = 0;
            for (Incident inc : incidents) {
                long gapDays = Math.abs(Duration.between(inc.getLatestReportedAt(), when).toDays());
                if (gapDays > MAX_GAP_DAYS) continue;
                double sim = jaccard(tokens, tokenMap.get(inc.getId()));
                if (sim >= THRESHOLD && sim > bestSim) {
                    best = inc;
                    bestSim = sim;
                }
            }

            if (best == null) {
                Incident inc = new Incident();
                inc.setTitle(cleanTitle(a.getTitle()));
                inc.setCategory(a.getCategory());
                inc.setSeverity(a.getSeverity());
                inc.setTaluk(a.getTaluk());
                inc.setFirstReportedAt(when);
                inc.setLatestReportedAt(when);
                inc.setArticleCount(1);
                inc = incidentRepo.save(inc);
                incidents.add(inc);
                tokenMap.put(inc.getId(), tokens);
                a.setIncidentId(inc.getId());
            } else {
                best.setArticleCount(best.getArticleCount() + 1);
                if (when.isAfter(best.getLatestReportedAt())) best.setLatestReportedAt(when);
                if (rank(a.getSeverity()) > rank(best.getSeverity())) best.setSeverity(a.getSeverity());
                a.setIncidentId(best.getId());
            }
            articleRepo.save(a);
        }

        // 4. priority (transparent rule, not a black box)
        for (Incident inc : incidents) {
            applyPriority(inc);
            incidentRepo.save(inc);
        }

        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("articlesUsed", articles.size());
        result.put("incidentsCreated", incidents.size());
        return result;
    }

    private void applyPriority(Incident inc) {
        int sev = rank(inc.getSeverity());                       // Low 1, Medium 2, High 3
        int count = inc.getArticleCount() >= 10 ? 3 : inc.getArticleCount() >= 4 ? 2 : inc.getArticleCount() >= 2 ? 1 : 0;
        boolean recent = inc.getLatestReportedAt().isAfter(LocalDateTime.now().minusDays(3));
        int rec = recent ? 1 : 0;
        int score = sev + count + rec;

        inc.setPriorityScore(score);
        inc.setPriority(score >= 6 ? "Critical" : score >= 4 ? "High" : score >= 3 ? "Medium" : "Low");
        inc.setPriorityReason("Severity " + inc.getSeverity() + " (+" + sev + "), "
                + inc.getArticleCount() + " related reports (+" + count + "), "
                + (recent ? "reported in last 3 days (+1)" : "not reported recently (+0)")
                + " = " + score);
    }

    private int rank(String severity) {
        if ("High".equals(severity)) return 3;
        if ("Medium".equals(severity)) return 2;
        return 1;
    }

    private LocalDateTime reportedAt(NewsArticle a) {
        return a.getPublishedAt() != null ? a.getPublishedAt() : a.getCollectedAt();
    }

    private String cleanTitle(String title) {
        int idx = title.lastIndexOf(" - ");
        return idx > 0 ? title.substring(0, idx) : title;   // remove "- The Hindu" suffix
    }

    private Set<String> tokens(String title) {
        return Arrays.stream(cleanTitle(title).toLowerCase().split("[^\\p{L}\\p{M}\\p{N}]+"))
                .filter(w -> w.length() >= 2 && !STOP.contains(w))
                .collect(Collectors.toSet());
    }

    private double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        Set<String> common = new HashSet<>(a);
        common.retainAll(b);
        Set<String> all = new HashSet<>(a);
        all.addAll(b);
        return (double) common.size() / all.size();
    }
}