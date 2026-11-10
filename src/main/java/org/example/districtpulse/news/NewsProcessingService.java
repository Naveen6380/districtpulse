package org.example.districtpulse.news;

import org.example.districtpulse.ai.ExtractionResult;
import org.example.districtpulse.ai.LlmClient;
import org.springframework.stereotype.Service;

@Service
public class NewsProcessingService {

    private final NewsArticleRepository repository;
    private final LlmClient llm;

    public NewsProcessingService(NewsArticleRepository repository, LlmClient llm) {
        this.repository = repository;
        this.llm = llm;
    }

    /** Processes every article still in NEW status. Returns how many succeeded. */
    public int processPending() {
        int ok = 0;
        for (NewsArticle a : repository.findByProcessingStatus("NEW")) {
            String text = a.getTitle() + ". " + (a.getContent() == null ? "" : a.getContent());
            try {
                ExtractionResult r = llm.extract(text);
                a.setCategory(r.category());
                a.setSeverity(r.severity());
                a.setTaluk(r.taluk());
                a.setLocality(r.locality());
                a.setConfidence(r.confidence());
                a.setEvidence(r.evidence());
                a.setSummary(llm.summarize(text));
                a.setProcessingStatus("PROCESSED");
                ok++;
            } catch (Exception e) {
                a.setProcessingStatus("FAILED");
            }
            repository.save(a);
        }
        return ok;
    }
}