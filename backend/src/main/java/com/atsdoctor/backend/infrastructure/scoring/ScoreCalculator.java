package com.atsdoctor.backend.infrastructure.scoring;

import com.atsdoctor.backend.infrastructure.matching.Normalizer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Weighted Job Match Score (TASK-052/054/055, FEAT-025/026; PRD §5.3
 * "Hybrid Scoring"):
 * {@code score = 0.30*skills + 0.20*keywords + 0.20*responsibilities + 0.15*experience + 0.10*seniority + 0.05*education}.
 *
 * <p>Importance weighting (SPRINT-03 decision, in-code constants): a matched
 * requirement contributes 1.0 × its importance factor (high 1.0 / medium 0.75 /
 * low 0.5); a partial match contributes half that. Categories with no
 * requirements are neutral (score 100, i.e. no penalty). The output JSON
 * follows §4.4 {@code score_breakdown} / {@code strengths} / {@code gaps}.
 */
@Component
public class ScoreCalculator {

    /** §4.4/§5.3 category weights in calculation order. */
    public static final Map<String, Double> WEIGHTS = new LinkedHashMap<>();
    public static final Map<String, Double> IMPORTANCE_FACTORS = Map.of(
            "high", 1.0, "medium", 0.75, "low", 0.5);
    public static final double PARTIAL_CREDIT = 0.5;

    static {
        WEIGHTS.put("skills", 0.30);
        WEIGHTS.put("keywords", 0.20);
        WEIGHTS.put("responsibilities", 0.20);
        WEIGHTS.put("experience", 0.15);
        WEIGHTS.put("seniority", 0.10);
        WEIGHTS.put("education", 0.05);
    }

    public record ScoreOutcome(
            int total,
            Map<String, Object> breakdown,
            List<Map<String, Object>> strengths,
            List<Map<String, Object>> gaps) {
    }

    public record MatchRequirement(String text, String type, String importance, String status) {
    }

    public record MatchKeyword(String keyword, boolean matched) {
    }

    public record MatchResponsibility(String text, boolean matched) {
    }

    public record MatchSeniority(String jdSeniority, boolean aligned) {
    }

    /**
     * Category values are 0..1 before ×100: skills/experience/education use
     * importance-weighted matched fractions (matched=1, partial=0.5);
     * keywords and responsibilities are plain hit fractions; seniority is
     * 1 aligned / 0.25 not aligned / 1 when the JD has no seniority.
     */
    public ScoreOutcome calculate(
            List<MatchRequirement> requirements,
            List<MatchKeyword> keywordHits,
            List<MatchResponsibility> responsibilities,
            MatchSeniority seniority) {

        Map<String, Double> categoryScores = new LinkedHashMap<>();
        Map<String, List<String>> matched = new LinkedHashMap<>();
        Map<String, List<String>> missing = new LinkedHashMap<>();
        WEIGHTS.keySet().forEach(cat -> {
            categoryScores.put(cat, 1.0);
            matched.put(cat, new ArrayList<>());
            missing.put(cat, new ArrayList<>());
        });

        double skillsNum = 0, skillsDen = 0;
        double experienceNum = 0, experienceDen = 0;
        double educationNum = 0, educationDen = 0;
        for (MatchRequirement req : requirements) {
            double factor = IMPORTANCE_FACTORS.getOrDefault(req.importance(), 0.75);
            double value = req.status().equals("matched") ? 1.0
                    : req.status().equals("partial") ? PARTIAL_CREDIT
                    : 0.0;
            String category = switch (req.type()) {
                case "experience" -> "experience";
                case "education" -> "education";
                default -> "skills";
            };
            if ("experience".equals(category)) {
                experienceNum += value * factor;
                experienceDen += factor;
            } else if ("education".equals(category)) {
                educationNum += value * factor;
                educationDen += factor;
            } else {
                skillsNum += value * factor;
                skillsDen += factor;
            }
            if (value > 0) {
                matched.get(category).add(req.text());
            } else {
                missing.get(category).add(req.text());
            }
        }
        categoryScores.put("skills", skillsDen == 0 ? 1.0 : skillsNum / skillsDen);
        categoryScores.put("experience", experienceDen == 0 ? 1.0 : experienceNum / experienceDen);
        categoryScores.put("education", educationDen == 0 ? 1.0 : educationNum / educationDen);

        int kHit = 0, kTotal = 0;
        for (MatchKeyword kw : keywordHits) {
            kTotal++;
            if (kw.matched()) {
                kHit++;
            }
        }
        categoryScores.put("keywords", kTotal == 0 ? 1.0 : (double) kHit / kTotal);
        keywordHits.forEach(kw -> (kw.matched() ? matched.get("keywords") : missing.get("keywords")).add(kw.keyword()));

        int rMatched = 0, rTotal = 0;
        for (MatchResponsibility r : responsibilities) {
            rTotal++;
            if (r.matched()) {
                rMatched++;
            }
            (r.matched() ? matched.get("responsibilities") : missing.get("responsibilities")).add(r.text());
        }
        categoryScores.put("responsibilities", rTotal == 0 ? 1.0 : (double) rMatched / rTotal);

        // Seniority: aligned=1, JD specifies seniority but not aligned=0.25,
        // no seniority in the JD = nothing to compare = neutral 1.0.
        double seniorityScore = seniority.aligned() ? 1.0 : seniority.jdSeniority() == null ? 1.0 : 0.25;
        categoryScores.put("seniority", seniorityScore);
        if (seniority.jdSeniority() != null) {
            (seniority.aligned() ? matched.get("seniority") : missing.get("seniority"))
                    .add(seniority.jdSeniority());
        }

        double total = 0;
        Map<String, Object> breakdown = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : WEIGHTS.entrySet()) {
            double cat = categoryScores.get(e.getKey());
            total += e.getValue() * cat;
            Map<String, Object> catJson = new LinkedHashMap<>();
            catJson.put("score", (int) Math.round(cat * 100));
            catJson.put("weight", e.getValue());
            catJson.put("matched", matched.get(e.getKey()));
            catJson.put("missing", missing.get(e.getKey()));
            breakdown.put(e.getKey(), catJson);
        }

        return new ScoreOutcome(
                Math.toIntExact(Math.round(total * 100)),
                breakdown,
                strengthEntries(requirements, keywordHits, responsibilities, seniority),
                gapEntries(requirements, keywordHits, responsibilities, seniority));
    }

    private static List<Map<String, Object>> strengthEntries(
            List<MatchRequirement> requirements, List<MatchKeyword> keywords,
            List<MatchResponsibility> responsibilities, MatchSeniority seniority) {
        List<Map<String, Object>> out = new ArrayList<>();
        requirements.stream().filter(r -> !"unmatched".equals(r.status()))
                .forEach(r -> out.add(entry("matched", "requirement", r.text())));
        keywords.stream().filter(MatchKeyword::matched)
                .forEach(k -> out.add(entry("matched", "keyword", k.keyword())));
        responsibilities.stream().filter(MatchResponsibility::matched)
                .forEach(r -> out.add(entry("matched", "responsibility", r.text())));
        if (seniority.aligned()) {
            out.add(entry("matched", "seniority", String.valueOf(seniority.jdSeniority())));
        }
        return out;
    }

    private static List<Map<String, Object>> gapEntries(
            List<MatchRequirement> requirements, List<MatchKeyword> keywords,
            List<MatchResponsibility> responsibilities, MatchSeniority seniority) {
        List<Map<String, Object>> out = new ArrayList<>();
        requirements.stream().filter(r -> "unmatched".equals(r.status())).forEach(r -> {
            Map<String, Object> gap = entry("unmatched", "requirement", r.text());
            gap.put("suggestions", suggestionsFor(r.text()));
            out.add(gap);
        });
        keywords.stream().filter(k -> !k.matched()).forEach(k -> {
            Map<String, Object> gap = entry("unmatched", "keyword", k.keyword());
            gap.put("suggestions", suggestionsFor(k.keyword()));
            out.add(gap);
        });
        responsibilities.stream().filter(r -> !r.matched())
                .forEach(r -> out.add(entry("unmatched", "responsibility", r.text())));
        if (!seniority.aligned() && seniority.jdSeniority() != null) {
            out.add(entry("unmatched", "seniority", String.valueOf(seniority.jdSeniority())));
        }
        return out;
    }

    private static final java.util.Set<String> STOP_TOKENS = java.util.Set.of(
            "experience", "strong", "knowledge", "with", "of", "and", "in", "or",
            "related", "field", "years", "year", "required", "preferred", "a", "an",
            "the", "to", "for", "design", "implement", "collaborate", "optimize");

    /** Deterministic, mirroring the PRD §4.4 gaps example; skips filler words. */
    private static List<String> suggestionsFor(String text) {
        String keyword = Normalizer.tokens(text).stream()
                .filter(t -> !STOP_TOKENS.contains(t))
                .findFirst()
                .orElseGet(() -> Normalizer.normalize(text));
        return List.of(
                "Add " + keyword + " to skills if applicable.",
                "Highlight any " + keyword + " experience.");
    }

    private static Map<String, Object> entry(String status, String type, String text) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", status);
        map.put("type", type);
        map.put("text", text);
        return map;
    }
}
