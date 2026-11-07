package org.example.districtpulse.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "mock")
public class MockLlmClient implements LlmClient {

    @Override
    public ExtractionResult extract(String text) {
        String t = text.toLowerCase();
        String category = "Other";
        String severity = "Low";

        if (t.contains("flood") || t.contains("rain") || t.contains("மழை")) {
            category = "Weather"; severity = "Medium";
        } else if (t.contains("road") || t.contains("bridge") || t.contains("சாலை")) {
            category = "Infrastructure";
        } else if (t.contains("crop") || t.contains("farmer") || t.contains("விவசாய")) {
            category = "Agriculture";
        } else if (t.contains("hospital") || t.contains("fever") || t.contains("dengue")) {
            category = "Health"; severity = "Medium";
        }
        if (t.contains("died") || t.contains("collapsed") || t.contains("evacuat")) {
            severity = "High";
        }

        String evidence = text.length() > 200 ? text.substring(0, 200) : text;
        return new ExtractionResult("DEMO: " + category + " report", category,
                "unknown", "unknown", severity, 0.3, evidence);
    }

    @Override
    public String summarize(String text) {
        return "[DEMO SUMMARY] " + (text.length() > 150 ? text.substring(0, 150) + "..." : text);
    }
}