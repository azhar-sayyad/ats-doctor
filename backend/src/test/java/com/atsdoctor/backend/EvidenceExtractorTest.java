package com.atsdoctor.backend;

import com.atsdoctor.backend.api.resume.ResumeDto;
import com.atsdoctor.backend.infrastructure.parsing.EvidenceExtractor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** TASK-033 — evidence extraction: sections, A/B/C classification, metadata. */
class EvidenceExtractorTest {

    private final EvidenceExtractor extractor = new EvidenceExtractor();

    @Test
    void classifies_bullets_by_explicit_metrics_and_verbs() {
        ResumeDto dto = new ResumeDto(
                null,
                new ResumeDto.Basics("Jane", null, null, null, null, null),
                null,
                null,
                List.of(new ResumeDto.Experience(
                        "exp_001", "Tech Corp", "Engineer", null, null, null, null,
                        List.of(
                                new ResumeDto.Bullet("b1",
                                        "Built FastAPI services processing 2M events/day.",
                                        List.of("FastAPI", "Python"), List.of("2M events/day"), null, "explicit"),
                                new ResumeDto.Bullet("b2",
                                        "Led a team of 4 engineers.", null, null, null, null),
                                new ResumeDto.Bullet("b3",
                                        "Attended weekly sync meetings.", null, null, null, null)))),
                null,
                null,
                null);

        List<EvidenceExtractor.ExtractedEvidence> evidence = extractor.extract(dto);

        assertThat(evidence).extracting(EvidenceExtractor.ExtractedEvidence::sectionId)
                .contains("exp_001", "b1", "b2", "b3");
        assertThat(evidence.stream()
                .filter(e -> "b1".equals(e.sectionId()))
                .findFirst().orElseThrow().claimCategory()).isEqualTo("A");
        assertThat(evidence.stream()
                .filter(e -> "b2".equals(e.sectionId()))
                .findFirst().orElseThrow().claimCategory()).isEqualTo("B");
        assertThat(evidence.stream()
                .filter(e -> "b3".equals(e.sectionId()))
                .findFirst().orElseThrow().claimCategory()).isEqualTo("C");
    }

    @Test
    void skills_and_projects_are_classified() {
        ResumeDto dto = new ResumeDto(
                null,
                new ResumeDto.Basics("Jane", null, null, null, null, null),
                null,
                List.of(new ResumeDto.Skill("Python", "Language", 5)),
                null,
                List.of(
                        new ResumeDto.Project("p1", "Queue", "desc",
                                List.of("Redis"), List.of("Reduced latency by 40%"), List.of()),
                        new ResumeDto.Project("p2", "Side note", "desc",
                                List.of(), List.of(), List.of())),
                null,
                null);

        List<EvidenceExtractor.ExtractedEvidence> evidence = extractor.extract(dto);

        assertThat(evidence).filteredOn(e -> "skills".equals(e.section()))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.sectionId()).isEqualTo("python");
                    assertThat(e.claimCategory()).isEqualTo("A");
                    assertThat(e.metadataJson()).contains("\"years\":5");
                });
        assertThat(evidence).filteredOn(e -> "projects".equals(e.section()))
                .extracting(EvidenceExtractor.ExtractedEvidence::claimCategory)
                .containsExactly("A", "B");
    }

    @Test
    void normalized_text_is_lowercased_and_punctuation_free() {
        ResumeDto dto = new ResumeDto(
                null,
                new ResumeDto.Basics("Jane", null, null, null, null, null),
                null,
                null,
                null,
                null,
                null,
                null);
        dto = new ResumeDto(null, dto.basics(), null,
                null,
                List.of(new ResumeDto.Experience(null, "Tech Corp", null, null, null, null, null,
                        List.of(new ResumeDto.Bullet(null, "Processed 2M Events/Day!", null, null, null, null)))),
                null, null, null);

        List<EvidenceExtractor.ExtractedEvidence> evidence = extractor.extract(dto);

        assertThat(evidence.stream()
                .filter(e -> "experience".equals(e.section()) && e.sectionId().contains("_bullet_"))
                .findFirst().orElseThrow().normalizedText()).isEqualTo("processed 2m events day");
    }

    @Test
    void null_dto_yields_no_evidence() {
        assertThat(extractor.extract(null)).isEmpty();
    }
}