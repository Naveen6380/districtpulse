package org.example.districtpulse.news;

import org.example.districtpulse.ai.ExtractionResult;
import org.example.districtpulse.ai.LlmClient;
import org.springframework.stereotype.Service;

@Service
public class NewsProcessingService {

    private final NewsArticleRepository repository;
    private final LlmClient llm;
    @org.springframework.beans.factory.annotation.Value("${ai.provider:mock}") private String provider;

    public NewsProcessingService(NewsArticleRepository repository, LlmClient llm) {
        this.repository = repository;
        this.llm = llm;
    }

    /** Processes every article still in NEW status. Returns how many succeeded. */
    public synchronized int processPending() {
        return processArticles(repository.findByProcessingStatus("NEW"));
    }

    public synchronized int processOne(Long id) {
        var article=repository.findById(id).orElseThrow(()->new IllegalArgumentException("Article not found"));
        return processArticles(java.util.List.of(article));
    }

    private int processArticles(java.util.List<NewsArticle> pending) {
        int ok = 0;
        for (NewsArticle a : pending) {
            if("PROCESSED".equals(a.getProcessingStatus())) continue;
            String text = a.getTitle() + ". " + (a.getContent() == null ? "" : a.getContent());
            try {
                ExtractionResult r = llm.extract(text);
                if (r.category() == null || r.evidence() == null || r.evidence().isBlank() || !text.toLowerCase(java.util.Locale.ROOT).contains(r.evidence().toLowerCase(java.util.Locale.ROOT))) throw new IllegalArgumentException("Extraction evidence does not match input");
                a.setExtractionMode(provider);
                a.setProcessingError(null);
                a.setCategory(r.category());
                a.setSeverity(r.severity());
                a.setTaluk(r.taluk());
                a.setLocality(r.locality());
                a.setConfidence(r.confidence());
                a.setEvidence(r.evidence());
                a.setSummary(llm.summarize(text));
                a.setProcessingStatus("PROCESSED");
                ok++;
            } catch (org.example.districtpulse.ai.AiQuotaException e) {
                a.setProcessingStatus("FAILED");a.setProcessingError(e.reason()+": remaining articles stay queued until provider quota is available");repository.save(a);
                break;
            } catch (Exception e) {
                a.setProcessingStatus("FAILED");
                String message=e.getMessage()==null?"":e.getMessage();
                a.setProcessingError(message.contains("rate limited")||message.contains("HTTP 429")?"GEMINI_RATE_LIMITED: provider quota/rate limit; retry after quota is available":message.contains("Evidence quote")?"INVALID_EVIDENCE: extraction quote did not match input":message.contains("Invalid extraction schema")?"INVALID_AI_JSON: response did not match extraction schema":"EXTRACTION_FAILED: check provider configuration and retry");
            }
            repository.save(a);
        }
        return ok;
    }
}
