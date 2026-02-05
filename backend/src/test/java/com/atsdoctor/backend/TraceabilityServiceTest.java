package com.atsdoctor.backend;

import com.atsdoctor.backend.application.tailoring.TailoringNotFoundException;
import com.atsdoctor.backend.application.validation.TraceabilityService;
import com.atsdoctor.backend.infrastructure.persistence.ResumeEvidence;
import com.atsdoctor.backend.infrastructure.persistence.ResumeEvidenceRepository;
import com.atsdoctor.backend.infrastructure.persistence.TailoredChange;
import com.atsdoctor.backend.infrastructure.persistence.TailoredChangeRepository;
import com.atsdoctor.backend.infrastructure.persistence.TailoredResume;
import com.atsdoctor.backend.infrastructure.persistence.TailoredResumeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** TASK-072 — TraceabilityService: change → evidence chain → section → bullet + validation issues. */
@ExtendWith(MockitoExtension.class)
class TraceabilityServiceTest {

    @Mock
    private TailoredResumeRepository tailoredResumeRepository;
    @Mock
    private TailoredChangeRepository tailoredChangeRepository;
    @Mock
    private ResumeEvidenceRepository resumeEvidenceRepository;

    private TraceabilityService service;

    @BeforeEach
    void setUp() {
        service = new TraceabilityService(tailoredResumeRepository, tailoredChangeRepository,
                resumeEvidenceRepository);
    }

    @Test
    void resolves_evidence_chain_section_bullet_and_validation_issues() {
        UUID tailoredId = UUID.randomUUID();
        UUID changeId = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();
        UUID refId = UUID.randomUUID();

        TailoredResume tailored = new TailoredResume();
        ReflectionTestUtils.setField(tailored, "id", tailoredId);
        tailored.setContent(CONTENT);
        tailored.setValidation("""
                {"valid": false, "checked_changes": 1, "validated_at": "2026-08-13T10:00:00Z", "issues": [
                  {"type": "UNSUPPORTED_DESCRIPTOR", "code": "unsupported_descriptor", "text": "kubernetes",
                   "suggestion": "drop", "context": "…", "change_id": "%s", "claim_category": "B"}
                ]}""".formatted(changeId));
        when(tailoredResumeRepository.findById(tailoredId)).thenReturn(Optional.of(tailored));

        TailoredChange change = change(tailoredId, changeId, evidenceId);
        when(tailoredChangeRepository.findByTailoredResumeIdOrderByCreatedAtAsc(tailoredId))
                .thenReturn(List.of(change));

        ResumeEvidence evidence = new ResumeEvidence();
        ReflectionTestUtils.setField(evidence, "id", evidenceId);
        evidence.setText("Docker-based deployment.");
        evidence.setSection("experience");
        evidence.setSectionId("exp_002");
        evidence.setClaimCategory("B");
        evidence.setSourceRefs(new UUID[]{refId});
        when(resumeEvidenceRepository.findById(evidenceId)).thenReturn(Optional.of(evidence));

        Map<String, Object> trace = service.trace(tailoredId, changeId);

        assertThat(trace).containsEntry("change_id", changeId)
                .containsEntry("original_text", "Built Docker-based deployment pipelines.")
                .containsEntry("tailored_text", "Built Docker-based deployment pipelines with Kubernetes.")
                .containsEntry("claim_category", "B");
        assertThat(trace.get("evidence")).asList().hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> evidenceRow = (Map<String, Object>) ((List<?>) trace.get("evidence")).get(0);
        assertThat(evidenceRow).containsEntry("id", evidenceId)
                .containsEntry("text", "Docker-based deployment.")
                .containsEntry("section", "experience")
                .containsEntry("section_id", "exp_002");
        assertThat(evidenceRow.get("source_refs")).asList().containsExactly(refId.toString());
        assertThat(trace.get("section")).isEqualTo(Map.of("id", "exp_002", "company", "Acme Corp", "title", "Backend Engineer"));
        assertThat(trace.get("bullet")).isEqualTo(Map.of(
                "original_id", "blt_2",
                "original_text", "Built Docker-based deployment pipelines.",
                "tailored_text", "Built Docker-based deployment pipelines with Kubernetes."));
        assertThat(trace.get("validation_issues")).asList()
                .singleElement()
                .satisfies(issue -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) issue;
                    assertThat(map).containsEntry("code", "unsupported_descriptor")
                            .containsEntry("text", "kubernetes")
                            .containsEntry("suggestion", "drop");
                });
    }

    @Test
    void summary_change_resolves_without_evidence_chain() {
        UUID tailoredId = UUID.randomUUID();
        UUID changeId = UUID.randomUUID();
        TailoredResume tailored = new TailoredResume();
        ReflectionTestUtils.setField(tailored, "id", tailoredId);
        tailored.setContent(CONTENT);
        when(tailoredResumeRepository.findById(tailoredId)).thenReturn(Optional.of(tailored));

        TailoredChange change = change(tailoredId, changeId, null);
        when(tailoredChangeRepository.findByTailoredResumeIdOrderByCreatedAtAsc(tailoredId))
                .thenReturn(List.of(change));

        Map<String, Object> trace = service.trace(tailoredId, changeId);

        assertThat(trace.get("evidence")).asList().isEmpty();
        assertThat(trace.get("section")).isNull();
        // bullet resolves by text match even without an evidence row
        assertThat(trace.get("bullet")).isEqualTo(Map.of(
                "original_id", "blt_2",
                "original_text", "Built Docker-based deployment pipelines.",
                "tailored_text", "Built Docker-based deployment pipelines with Kubernetes."));
        assertThat(trace.get("validation_issues")).asList().isEmpty();
    }

    @Test
    void unknown_tailored_is_not_found() {
        when(tailoredResumeRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.trace(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(TailoringNotFoundException.class);
    }

    @Test
    void change_not_belonging_to_tailored_is_not_found() {
        UUID tailoredId = UUID.randomUUID();
        TailoredResume tailored = new TailoredResume();
        ReflectionTestUtils.setField(tailored, "id", tailoredId);
        when(tailoredResumeRepository.findById(tailoredId)).thenReturn(Optional.of(tailored));
        when(tailoredChangeRepository.findByTailoredResumeIdOrderByCreatedAtAsc(tailoredId))
                .thenReturn(List.of(change(tailoredId, UUID.randomUUID(), null)));

        assertThatThrownBy(() -> service.trace(tailoredId, UUID.randomUUID()))
                .isInstanceOf(TailoringNotFoundException.class)
                .hasMessageContaining("tailored change");
    }

    @Test
    void contentless_tailored_resolves_an_empty_chain() {
        UUID tailoredId = UUID.randomUUID();
        UUID changeId = UUID.randomUUID();
        TailoredResume tailored = new TailoredResume();
        ReflectionTestUtils.setField(tailored, "id", tailoredId);
        when(tailoredResumeRepository.findById(tailoredId)).thenReturn(Optional.of(tailored));
        when(tailoredChangeRepository.findByTailoredResumeIdOrderByCreatedAtAsc(tailoredId))
                .thenReturn(List.of(change(tailoredId, changeId, null)));

        Map<String, Object> trace = service.trace(tailoredId, changeId);

        assertThat(trace.get("section")).isNull();
        assertThat(trace.get("bullet")).isNull();
        assertThat(trace.get("evidence")).asList().isEmpty();
    }

    private static TailoredChange change(UUID tailoredId, UUID changeId, UUID evidenceId) {
        TailoredChange change = new TailoredChange();
        ReflectionTestUtils.setField(change, "id", changeId);
        change.setTailoredResume(new TailoredResume());
        change.setEvidenceId(evidenceId);
        change.setOriginalText("Built Docker-based deployment pipelines.");
        change.setTailoredText("Built Docker-based deployment pipelines with Kubernetes.");
        change.setReason("Better alignment with JD keywords ('kubernetes').");
        change.setClaimCategory("B");
        change.setStatus("PENDING");
        return change;
    }

    private static final String CONTENT = """
            {"summary": null, "experience": [
              {"id": "exp_001", "company": "OldCo", "title": "Engineer", "bullets": [
                {"original_id": "blt_1", "original_text": "Wrote APIs.", "tailored_text": "Wrote APIs."}
              ]},
              {"id": "exp_002", "company": "Acme Corp", "title": "Backend Engineer", "bullets": [
                {"original_id": "blt_2", "original_text": "Built Docker-based deployment pipelines.",
                 "tailored_text": "Built Docker-based deployment pipelines with Kubernetes."}
              ]}
            ], "order": ["exp_002", "exp_001"]}""";
}