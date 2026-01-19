package com.atsdoctor.backend.infrastructure.parsing;

import com.atsdoctor.backend.api.resume.ResumeDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Splits a structured resume (PRD §4.2) into the relational evidence model
 * {@code resume_evidence} (FEAT-014, TASK-033): sections → rows with
 * {@code section}/{@code section_id}/{@code text}/{@code normalized_text}/
 * {@code metadata}, classified A/B/C.
 *
 * <p>Deterministic MVP rules (no LLM):<ul>
 * <li><b>A</b> — explicit facts: bullets marked {@code evidence_level=explicit},
 *     or carrying explicit metrics/technologies; all skills; projects with
 *     outcomes.</li>
 * <li><b>B</b> — supported descriptors with accomplishment verbs (led, built,
 *     designed, …).</li>
 * <li><b>C</b> — everything else (unsupported claims).</li>
 * </ul>
 * {@code source_refs} stay empty in the MVP (populated by FEAT-044+ with real
 * evidence IDs).
 */
@Component
public class EvidenceExtractor {

    private static final Logger log = LoggerFactory.getLogger(EvidenceExtractor.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Set<String> ACCOMPLISHMENT_VERBS = Set.of(
            "led", "built", "designed", "developed", "implemented", "improved",
            "optimized", "managed", "coordinated", "architected", "delivered",
            "shipped", "launched", "created", "established", "drove", "reduced",
            "increased", "engineered", "automated");

    public record ExtractedEvidence(
            String section,
            String sectionId,
            String text,
            String normalizedText,
            String metadataJson,
            String claimCategory) {
    }

    public List<ExtractedEvidence> extract(ResumeDto dto) {
        List<ExtractedEvidence> evidence = new ArrayList<>();
        if (dto == null) {
            return evidence;
        }
        extractSkills(dto.skills(), evidence);
        extractExperience(dto.experience(), evidence);
        extractProjects(dto.projects(), evidence);
        return evidence;
    }

    private void extractSkills(List<ResumeDto.Skill> skills, List<ExtractedEvidence> out) {
        if (skills == null) {
            return;
        }
        for (int i = 0; i < skills.size(); i++) {
            ResumeDto.Skill skill = skills.get(i);
            Map<String, Object> metadata = new LinkedHashMap<>();
            if (skill.category() != null) {
                metadata.put("category", skill.category());
            }
            if (skill.years() != null) {
                metadata.put("years", skill.years());
            }
            String name = skill.name() == null ? "skill_" + i : skill.name();
            out.add(new ExtractedEvidence(
                    "skills",
                    normalizeId(name),
                    name,
                    normalize(name),
                    toJson(metadata),
                    "A"));
        }
    }

    private void extractExperience(List<ResumeDto.Experience> experience, List<ExtractedEvidence> out) {
        if (experience == null) {
            return;
        }
        for (int i = 0; i < experience.size(); i++) {
            ResumeDto.Experience exp = experience.get(i);
            String expId = exp.id() == null ? "exp_" + (i + 1) : exp.id();

            String roleText = (exp.title() == null ? "" : exp.title())
                    + (exp.company() == null ? "" : " at " + exp.company().trim());
            Map<String, Object> roleMetadata = new LinkedHashMap<>();
            roleMetadata.put("company", exp.company());
            roleMetadata.put("title", exp.title());
            out.add(new ExtractedEvidence(
                    "experience", expId, roleText, normalize(roleText), toJson(roleMetadata), "B"));

            if (exp.bullets() != null) {
                for (int j = 0; j < exp.bullets().size(); j++) {
                    ResumeDto.Bullet bullet = exp.bullets().get(j);
                    if (bullet == null || bullet.text() == null || bullet.text().isBlank()) {
                        continue;
                    }
                    String bulletId = bullet.id() == null
                            ? expId + "_bullet_" + (j + 1)
                            : bullet.id();
                    Map<String, Object> metadata = new LinkedHashMap<>();
                    metadata.put("technologies", bullet.technologies() == null ? List.of() : bullet.technologies());
                    metadata.put("metrics", bullet.metrics() == null ? List.of() : bullet.metrics());
                    metadata.put("domains", bullet.domains() == null ? List.of() : bullet.domains());
                    out.add(new ExtractedEvidence(
                            "experience", bulletId, bullet.text(), normalize(bullet.text()),
                            toJson(metadata), classifyBullet(bullet)));
                }
            }
        }
    }

    private void extractProjects(List<ResumeDto.Project> projects, List<ExtractedEvidence> out) {
        if (projects == null) {
            return;
        }
        for (int i = 0; i < projects.size(); i++) {
            ResumeDto.Project project = projects.get(i);
            if (project == null || project.name() == null || project.name().isBlank()) {
                continue;
            }
            String projectId = project.id() == null ? "proj_" + (i + 1) : project.id();
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("technologies", project.technologies() == null ? List.of() : project.technologies());
            metadata.put("outcomes", project.outcomes() == null ? List.of() : project.outcomes());
            String category = (project.outcomes() != null && !project.outcomes().isEmpty()) ? "A" : "B";
            out.add(new ExtractedEvidence(
                    "projects", projectId, project.name(), normalize(project.name()),
                    toJson(metadata), category));
        }
    }

    private String classifyBullet(ResumeDto.Bullet bullet) {
        boolean explicit = "explicit".equalsIgnoreCase(bullet.evidenceLevel())
                || (bullet.metrics() != null && !bullet.metrics().isEmpty())
                || (bullet.technologies() != null && !bullet.technologies().isEmpty());
        if (explicit) {
            return "A";
        }
        String lower = bullet.text().toLowerCase(Locale.ROOT);
        for (String verb : ACCOMPLISHMENT_VERBS) {
            if (lower.contains(" " + verb + " ") || lower.startsWith(verb + " ")) {
                return "B";
            }
        }
        return "C";
    }

    /** Lowercase id safe for db storage (spaces/slashes → underscore). */
    static String normalizeId(String id) {
        return normalize(id).replace(' ', '_').replace('/', '_').replace('.', '_');
    }

    /** Lowercase, punctuation stripped, whitespace collapsed. */
    static String normalize(String text) {
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFKD);
        String cleaned = decomposed.replaceAll("[^\\p{L}\\p{N}\\s]", " ");
        return cleaned.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private String toJson(Map<String, Object> metadata) {
        try {
            return MAPPER.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            log.warn("Could not serialize evidence metadata, storing empty object: {}", ex.getMessage());
            return "{}";
        }
    }
}