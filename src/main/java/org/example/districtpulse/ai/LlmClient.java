package org.example.districtpulse.ai;

public interface LlmClient {
    ExtractionResult extract(String articleText);
    String summarize(String articleText);
}