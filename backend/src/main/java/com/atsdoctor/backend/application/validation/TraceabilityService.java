package com.atsdoctor.backend.application.validation;

import com.atsdoctor.backend.application.tailoring.TailoringNotFoundException;
import com.atsdoctor.backend.infrastructure.persistence.ResumeEvidence;
import com.atsdoctor.backend.infrastructure.persistence.ResumeEvidenceRepository;
import com.atsdoctor.backend.infrastructure.persistence.TailoredChange;
import com.atsdoctor.backend.infrastructure.persistence.TailoredChangeRepository;
import com.atsdoctor.backend.infrastructure.persistence.TailoredResume;
import com.atsdoctor.backend.infrastructure.persistence.TailoredResumeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Claim traceability (FEAT-036, TASK-072): resolves a {@code tailored_changes}
 * row to its source chain — evidence row (id/text/section/section_id/
 * {@code source_refs}, PRD §5.9) → section context (company/title from the
 * tailored document) → tailored bullet (original/tailored text) — plus the
 * validation issues recorded for that change. Serves
 * {@code GET /tailored/{id}/changes/{change_id}/trace}, the "why is this
 * claim here?" API.
 *
 * <p>Guards: 404 for an unknown tailored resume or a change that does not
 * belong to it; the summary variant simply resolves without an evidence
 * chain (evidence_id is null).
 */
@Service
@ConditionalOnProperty(name = "ats.doctor.persistence.enabled", havingValue = "true")
public class TraceabilityService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TailoredResumeRepository tailoredResumeRepository;
    private final TailoredChangeRepository tailoredChangeRepository;
    private final ResumeEvidenceRepository resumeEvidenceRepository;

    public TraceabilityService(TailoredResumeRepository tailoredResumeRepository,
                               TailoredChangeRepository tailoredChangeRepository,
                               ResumeEvidenceRepository resumeEvidenceRepository) {
        this.tailoredResumeRepository = tailoredResumeRepository;
        this.tailoredChangeRepository = tailoredChangeRepository;
        this.resumeEvidenceRepository = resumeEvidenceRepository;
    }

    /** Resolve the change's evidence chain + section + bullet + validation issues. */
    @Transactional(readOnly = true)
    public Map<String, Object> trace(UUID tailoredResumeId, UUID changeId) {
        TailoredResume tailored = tailoredResumeRepository.findById(tailoredResumeId)
                .orElseThrow(() -> new TailoringNotFoundException("No tailored resume found for id " + tailoredResumeId));
        TailoredChange change = tailoredChangeRepository.findByTailoredResumeIdOrderByCreatedAtAsc(tailoredResumeId)
                .stream()
                .filter(c -> changeId.equals(c.getId()))
                .findFirst()
                .orElseThrow(() -> new TailoringNotFoundException(
                        "No tailored change " + changeId + " found for tailored resume " + tailoredResumeId));

        ResumeEvidence evidence = change.getEvidenceId() == null
                ? null
                : resumeEvidenceRepository.findById(change.getEvidenceId()).orElse(null);
        JsonNode content = json(tailored.getContent());

        JsonNode masterResume = (tailored.getResumeVersion() != null && tailored.getResumeVersion().getStructuredData() != null)
                ? json(tailored.getResumeVersion().getStructuredData())
                : null;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("change_id", change.getId());
        out.put("original_text", change.getOriginalText());
        out.put("tailored_text", change.getTailoredText());
        out.put("reason", change.getReason());
        out.put("claim_category", change.getClaimCategory());
        out.put("status", change.getStatus());
        out.put("evidence", evidence == null ? List.of() : List.of(evidenceRow(evidence)));
        out.put("section", sectionOf(content, masterResume, evidence));
        out.put("bullet", bulletOf(content, change));
        out.put("validation_issues", validationIssuesOf(json(tailored.getValidation()), changeId));
        return out;
    }

    private static Map<String, Object> evidenceRow(ResumeEvidence evidence) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", evidence.getId());
        row.put("text", evidence.getText());
        row.put("section", evidence.getSection());
        row.put("section_id", evidence.getSectionId());
        row.put("claim_category", evidence.getClaimCategory());
        List<String> refs = new ArrayList<>();
        for (UUID ref : evidence.getSourceRefs()) {
            refs.add(ref.toString());
        }
        row.put("source_refs", refs);
        return row;
    }

    /** Section context (id/company/title) from the tailored document or master resume. */
    private static Map<String, Object> sectionOf(JsonNode content, JsonNode masterResume, ResumeEvidence evidence) {
        if (evidence == null || evidence.getSectionId() == null) {
            return null;
        }
        if (content != null) {
            for (JsonNode entry : content.path("experience")) {
                if (evidence.getSectionId().equals(entry.path("id").asText())
                        || hasBullet(entry, evidence.getSectionId())) {
                    Map<String, Object> section = new LinkedHashMap<>();
                    section.put("id", entry.path("id").asText());
                    section.put("company", entry.path("company").asText(""));
                    section.put("title", entry.path("title").asText(""));
                    return section;
                }
            }
        }
        if (masterResume != null) {
            for (JsonNode entry : masterResume.path("experience")) {
                if (evidence.getSectionId().equals(entry.path("id").asText())
                        || hasMasterBullet(entry, evidence.getSectionId(), evidence.getText())) {
                    Map<String, Object> section = new LinkedHashMap<>();
                    section.put("id", entry.path("id").asText());
                    section.put("company", entry.path("company").asText(""));
                    section.put("title", entry.path("title").asText(""));
                    return section;
                }
            }
        }
        return null;
    }

    private static boolean hasMasterBullet(JsonNode entry, String sectionId, String text) {
        if (sectionId.equals(entry.path("id").asText())) {
            return true;
        }
        for (JsonNode bullet : entry.path("bullets")) {
            String bId = bullet.path("id").asText("");
            String bTxt = bullet.isObject() ? bullet.path("text").asText("") : bullet.asText("");
            if (sectionId.equals(bId) || (text != null && text.equals(bTxt))) {
                return true;
            }
        }
        return false;
    }

    /** Whether the experience entry contains the tailored bullet with this original id. */
    private static boolean hasBullet(JsonNode entry, String bulletId) {
        for (JsonNode bullet : entry.path("bullets")) {
            if (bulletId.equals(bullet.path("original_id").asText())) {
                return true;
            }
        }
        return false;
    }

    /** Tailored bullet whose original text is the change's — first match wins. */
    private static Map<String, Object> bulletOf(JsonNode content, TailoredChange change) {
        if (content == null || change.getOriginalText() == null) {
            return null;
        }
        for (JsonNode entry : content.path("experience")) {
            for (JsonNode bullet : entry.path("bullets")) {
                if (change.getOriginalText().equals(bullet.path("original_text").asText())) {
                    Map<String, Object> hit = new LinkedHashMap<>();
                    hit.put("original_id", bullet.path("original_id").asText(""));
                    hit.put("original_text", bullet.path("original_text").asText(""));
                    hit.put("tailored_text", bullet.path("tailored_text").asText(""));
                    return hit;
                }
            }
        }
        return null;
    }

    /** Issues of the recorded validation report that belong to this change. */
    private static List<Map<String, Object>> validationIssuesOf(JsonNode report, UUID changeId) {
        if (report == null) {
            return List.of();
        }
        List<Map<String, Object>> issues = new ArrayList<>();
        for (JsonNode node : report.path("issues")) {
            if (!changeId.toString().equals(node.path("change_id").asText())) {
                continue;
            }
            Map<String, Object> issue = new LinkedHashMap<>();
            issue.put("type", node.path("type").asText());
            issue.put("code", node.path("code").asText());
            issue.put("text", node.path("text").asText());
            issue.put("suggestion", node.path("suggestion").asText());
            issue.put("context", node.path("context").asText());
            issues.add(issue);
        }
        return issues;
    }

    private static JsonNode json(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(stored);
        } catch (Exception ex) {
            return null;
        }
    }
}