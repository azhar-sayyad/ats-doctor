package com.atsdoctor.backend.infrastructure.matching;

import java.util.Map;

/**
 * Maintenance-friendly alias table (TASK-047, FEAT-022): common abbreviations
 * and spelling variants map to a canonical phrase. Both sides of a comparison
 * are canonicalized via {@link #canonicalize} AFTER normalization, so keys
 * live in normalized form. Coverage is intentionally small and grows via PR
 * review, not at runtime.
 */
public final class AliasTable {

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("js", "javascript"),
            Map.entry("ts", "typescript"),
            Map.entry("nodejs", "nodejs"),
            Map.entry("node js", "nodejs"),
            Map.entry("node.js", "nodejs"),
            Map.entry("reactjs", "reactjs"),
            Map.entry("react js", "reactjs"),
            Map.entry("backend", "backend"),
            Map.entry("back-end", "backend"),
            Map.entry("back end", "backend"),
            Map.entry("restapi", "rest api"),
            Map.entry("k8s", "kubernetes"),
            Map.entry("gcp", "google cloud"),
            Map.entry("aws", "amazon web services"),
            Map.entry("jquery", "jquery"),
            Map.entry("ml", "machine learning"));

    private AliasTable() {
    }

    /**
     * Maps a phrase to its canonical normalized form (identity when unknown).
     * Known whole phrases map directly; otherwise each token is canonicalized
     * individually so "5 years of JS experience" still yields "javascript".
     */
    public static String canonicalize(String phrase) {
        if (phrase == null) {
            return "";
        }
        String key = Normalizer.normalize(phrase).trim();
        if (key.isEmpty()) {
            return "";
        }
        String whole = ALIASES.get(key);
        if (whole != null) {
            return whole;
        }
        return java.util.Arrays.stream(key.split(" "))
                .map(token -> ALIASES.getOrDefault(token, token))
                .collect(java.util.stream.Collectors.joining(" "));
    }
}
