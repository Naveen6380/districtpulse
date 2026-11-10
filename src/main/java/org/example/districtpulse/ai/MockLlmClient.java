package org.example.districtpulse.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "mock")
public class MockLlmClient implements LlmClient {

    // VERIFY against official list. District name itself is NOT a taluk.
    private static final List<String> TALUKS = List.of(
            "alangudi", "aranthangi", "avudaiyarkoil", "gandarvakottai", "illuppur",
            "karambakudi", "kulathur", "manamelkudi", "ponnamaravathy", "thirumayam",
            "viralimalai", "annavasal", "arimalam");

    private static boolean has(String text, String regex) {
        return Pattern.compile(regex).matcher(text).find();
    }

    @Override
    public ExtractionResult extract(String text) {
        String t = text.toLowerCase();
        String category = "Other";
        String severity = "Low";
        double confidence = 0.4;

        if (has(t, "on road price|price in|ex-showroom|variants|weather today|hourly forecast|the weather channel")) {
            category = "Not relevant";
            confidence = 0.6;
        } else if (has(t, "\\bflood|\\brainfall\\b|\\brains?\\b|cyclone|heatwave|மழை")) {
            category = "Weather"; severity = "Medium";
        } else if (has(t, "hospital|\\bfever\\b|dengue|outbreak")) {
            category = "Health"; severity = "Medium";
        } else if (has(t, "\\bdied\\b|\\bdeath\\b|\\bdead\\b|killed|accident|arrest|blockade|protest|\\bfaints?\\b|police")) {
            category = "Public safety"; severity = "Medium";
        } else if (has(t, "\\bcrops?\\b|farmer|paddy|விவசாய")) {
            category = "Agriculture";
        } else if (has(t, "\\bschool|college|\\bstudents?\\b|exam")) {
            category = "Education";
        } else if (has(t, "\\broads?\\b|\\bbridge\\b|pothole|சாலை")) {
            category = "Infrastructure";
        } else if (has(t, "\\bcm\\b|chief minister|inaugurat|bypoll|roadshow|collector")) {
            category = "Government administration";
        }

        if (has(t, "\\bdied\\b|\\bdeath\\b|\\bdead\\b|killed|collapsed|evacuat|drown")) {
            severity = "High";
        }

        String taluk = "unknown";
        for (String name : TALUKS) {
            if (t.contains(name)) {
                taluk = Character.toUpperCase(name.charAt(0)) + name.substring(1);
                confidence = Math.max(confidence, 0.5);
                break;
            }
        }

        String evidence = text.length() > 200 ? text.substring(0, 200) : text;
        return new ExtractionResult("DEMO: " + category + " report", category,
                taluk, "unknown", severity, confidence, evidence);
    }

    @Override
    public String summarize(String text) {
        return "[DEMO SUMMARY] " + (text.length() > 150 ? text.substring(0, 150) + "..." : text);
    }
}