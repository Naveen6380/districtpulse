package org.example.districtpulse.ai;

public record ExtractionResult(
        String title,
        String category,
        String taluk,
        String locality,
        String severity,
        double confidence,
        String evidence) {}