package com.atsdoctor.backend.infrastructure.matching;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * In-memory evidence lookup (TASK-048, FEAT-023): token → evidence ids index
 * plus linear phrase search. Sized for 100+ bullets (PRD assumption), so a
 * simple HashMap + linear scan is both fast enough and trivially debuggable.
 */
public final class EvidenceIndex {

    private final List<EvidenceDoc> docs;
    private final Map<String, Set<String>> tokenToIds;

    private EvidenceIndex(List<EvidenceDoc> docs) {
        this.docs = List.copyOf(docs);
        Map<String, Set<String>> map = new LinkedHashMap<>();
        for (EvidenceDoc doc : this.docs) {
            for (String token : Normalizer.tokens(doc.normalizedText())) {
                map.computeIfAbsent(token, k -> new HashSet<>()).add(doc.id());
            }
        }
        this.tokenToIds = map;
    }

    public static EvidenceIndex build(List<EvidenceDoc> docs) {
        return new EvidenceIndex(docs == null ? List.of() : docs);
    }

    /** All indexed text documents. */
    public List<EvidenceDoc> docs() {
        return docs;
    }

    /** Evidence ids containing the token (used for coverage checks). */
    public Set<String> idsForToken(String token) {
        return tokenToIds.getOrDefault(Normalizer.normalize(token).trim(), Set.of());
    }

    /** Best matching evidence for a phrase, ordered by strength, capped at {@code max}. */
    public List<EvidenceHit> search(String phrase, int max) {
        return docs.stream()
                .map(doc -> EvidenceHit.of(phrase, doc))
                .filter(hit -> hit != null)
                .sorted(EvidenceHit.byStrength())
                .limit(Math.max(1, max))
                .toList();
    }
}
