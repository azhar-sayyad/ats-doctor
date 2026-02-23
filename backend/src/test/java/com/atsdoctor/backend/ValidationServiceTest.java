package com.atsdoctor.backend;

import com.atsdoctor.backend.application.tailoring.TailoringConflictException;
import com.atsdoctor.backend.application.tailoring.TailoringNotFoundException;
import com.atsdoctor.backend.application.validation.ValidationService;
import com.atsdoctor.backend.infrastructure.persistence.ResumeEvidence;
import com.atsdoctor.backend.infrastructure.persistence.ResumeEvidenceRepository;
import com.atsdoctor.backend.infrastructure.persistence.TailoredChange;
import com.atsdoctor.backend.infrastructure.persistence.TailoredChangeRepository;
import com.atsdoctor.backend.infrastructure.persistence.TailoredResume;
import com.atsdoctor.backend.infrastructure.persistence.TailoredResumeRepository;
import com.atsdoctor.backend.infrastructure.validation.ValidationIssue;
import com.atsdoctor.backend.infrastructure.validation.ValidationIssueType;
import com.atsdoctor.backend.infrastructure.validation.ValidationRules;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** TASK-071 — ValidationService: per-change rule-engine run, report persistence, state guard. */
@ExtendWith(MockitoExtension.class)
class ValidationServiceTest {

    @Mock
    private TailoredResumeRepository tailoredResumeRepository;
    @Mock
    private TailoredChangeRepository tailoredChangeRepository;
    @Mock
    private ResumeEvidenceRepository resumeEvidenceRepository;
    @Mock
    private ValidationRules validationRules;

    private ValidationService service;

    @BeforeEach
    void setUp() {
        service = new ValidationService(tailoredResumeRepository, tailoredChangeRepository,
                resumeEvidenceRepository, validationRules);
    }

    @Test
    void validates_every_change_and_persists_the_report() {
        TailoredResume tailored = tailored("READY");
        UUID evidenceId = UUID.randomUUID();
        when(tailoredResumeRepository.findById(tailored.getId())).thenReturn(Optional.of(tailored));
        when(resumeEvidenceRepository.findByResumeVersionId(any()))
                .thenReturn(List.of(evidence(evidenceId, "Docker deployment experience.")));
        when(tailoredChangeRepository.findByTailoredResumeIdOrderByCreatedAtAsc(tailored.getId()))
                .thenReturn(List.of(change(tailored, evidenceId, "B",
                        "Built Docker-based pipelines.",
                        "Built Docker-based pipelines with Kubernetes.")));
        when(validationRules.validate("B",
                "Built Docker-based pipelines.",
                "Built Docker-based pipelines with Kubernetes.",
                List.of("Docker deployment experience.")))
                .thenReturn(new ValidationRules.Verdict(false, List.of(new ValidationIssue(
                        ValidationIssueType.UNSUPPORTED_DESCRIPTOR, "unsupported_descriptor",
                        "kubernetes", "drop it", "…B…"))));

        JsonNode report = service.validate(tailored.getId());

        assertThat(report.path("valid").asBoolean()).isFalse();
        assertThat(report.path("checked_changes").asInt()).isEqualTo(1);
        assertThat(report.path("issues").size()).isEqualTo(1);
        assertThat(report.path("issues").get(0).path("type").asText()).isEqualTo("unsupported_descriptor");
        assertThat(report.path("issues").get(0).path("code").asText()).isEqualTo("unsupported_descriptor");
        assertThat(report.path("issues").get(0).path("text").asText()).isEqualTo("kubernetes");
        assertThat(report.path("issues").get(0).path("claim_category").asText()).isEqualTo("B");
        assertThat(report.path("issues").get(0).path("change_id").asText()).isNotNull();
        assertThat(report.path("validated_at").asText()).isNotBlank();
        assertThat(tailored.getValidation()).isEqualTo(report.toString());
        verify(tailoredResumeRepository).save(tailored);
    }

    @Test
    void grounds_changes_against_all_evidence_rows() {
        TailoredResume tailored = tailored("READY");
        when(tailoredResumeRepository.findById(tailored.getId())).thenReturn(Optional.of(tailored));
        when(resumeEvidenceRepository.findByResumeVersionId(any()))
                .thenReturn(List.of(evidence(UUID.randomUUID(), "Java and PostgreSQL microservices.")));
        when(tailoredChangeRepository.findByTailoredResumeIdOrderByCreatedAtAsc(tailored.getId()))
                .thenReturn(List.of(change(tailored, null, "B",
                        "Built scalable services.",
                        "Built scalable services with Java and PostgreSQL.")));
        when(validationRules.validate("B", "Built scalable services.",
                "Built scalable services with Java and PostgreSQL.",
                List.of("Java and PostgreSQL microservices.")))
                .thenReturn(new ValidationRules.Verdict(true, List.of()));

        JsonNode report = service.validate(tailored.getId());

        assertThat(report.path("valid").asBoolean()).isTrue();
    }

    @Test
    void clean_changes_produce_a_valid_report() {
        TailoredResume tailored = tailored("READY");
        when(tailoredResumeRepository.findById(tailored.getId())).thenReturn(Optional.of(tailored));
        when(resumeEvidenceRepository.findByResumeVersionId(any())).thenReturn(List.of());
        when(tailoredChangeRepository.findByTailoredResumeIdOrderByCreatedAtAsc(tailored.getId()))
                .thenReturn(List.of(change(tailored, null, "A",
                        "Built FastAPI services.",
                        "Built FastAPI services using Python.")));
        when(validationRules.validate("A", "Built FastAPI services.",
                "Built FastAPI services using Python.", List.of()))
                .thenReturn(new ValidationRules.Verdict(true, List.of()));

        JsonNode report = service.validate(tailored.getId());

        assertThat(report.path("valid").asBoolean()).isTrue();
        assertThat(report.path("issues").size()).isZero();
    }

    @Test
    void validating_state_completes_to_ready() {
        TailoredResume tailored = tailored("VALIDATING");
        when(tailoredResumeRepository.findById(tailored.getId())).thenReturn(Optional.of(tailored));
        when(resumeEvidenceRepository.findByResumeVersionId(any())).thenReturn(List.of());
        when(tailoredChangeRepository.findByTailoredResumeIdOrderByCreatedAtAsc(tailored.getId()))
                .thenReturn(List.of(change(tailored, null, "B", "old", "new")));
        when(validationRules.validate(any(), any(), any(), any()))
                .thenReturn(new ValidationRules.Verdict(true, List.of()));

        service.validate(tailored.getId());

        assertThat(tailored.getState()).isEqualTo("READY");
    }

    @Test
    void ready_revalidation_keeps_state_ready() {
        TailoredResume tailored = tailored("READY");
        when(tailoredResumeRepository.findById(tailored.getId())).thenReturn(Optional.of(tailored));
        when(resumeEvidenceRepository.findByResumeVersionId(any())).thenReturn(List.of());
        when(tailoredChangeRepository.findByTailoredResumeIdOrderByCreatedAtAsc(tailored.getId()))
                .thenReturn(List.of(change(tailored, null, "B", "old", "new")));
        when(validationRules.validate(any(), any(), any(), any()))
                .thenReturn(new ValidationRules.Verdict(true, List.of()));

        service.validate(tailored.getId());

        assertThat(tailored.getState()).isEqualTo("READY");
    }

    @Test
    void unknown_tailored_is_not_found() {
        when(tailoredResumeRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validate(UUID.randomUUID()))
                .isInstanceOf(TailoringNotFoundException.class);
    }

    @Test
    void non_ready_states_conflict() {
        TailoredResume tailored = tailored("GENERATING");
        when(tailoredResumeRepository.findById(tailored.getId())).thenReturn(Optional.of(tailored));

        assertThatThrownBy(() -> service.validate(tailored.getId()))
                .isInstanceOf(TailoringConflictException.class)
                .hasMessageContaining("state=GENERATING");
        verify(tailoredResumeRepository, never()).save(any());
    }

    @Test
    void missing_evidence_row_grounds_without_evidence() {
        TailoredResume tailored = tailored("READY");
        UUID evidenceId = UUID.randomUUID();
        when(tailoredResumeRepository.findById(tailored.getId())).thenReturn(Optional.of(tailored));
        when(resumeEvidenceRepository.findByResumeVersionId(any())).thenReturn(List.of());
        when(tailoredChangeRepository.findByTailoredResumeIdOrderByCreatedAtAsc(tailored.getId()))
                .thenReturn(List.of(change(tailored, evidenceId, "C", "old", "new claim")));
        when(validationRules.validate("C", "old", "new claim", List.of()))
                .thenReturn(new ValidationRules.Verdict(false, List.of(new ValidationIssue(
                        ValidationIssueType.UNSUPPORTED_CLAIM, "unsupported_achievement",
                        "new", "remove", "…"))));

        JsonNode report = service.validate(tailored.getId());

        assertThat(report.path("valid").asBoolean()).isFalse();
        assertThat(report.path("issues").get(0).path("type").asText()).isEqualTo("unsupported_claim");
    }

    private static TailoredResume tailored(String state) {
        TailoredResume tailored = new TailoredResume();
        ReflectionTestUtils.setField(tailored, "id", UUID.randomUUID());
        tailored.setResumeVersion(new com.atsdoctor.backend.infrastructure.persistence.ResumeVersion());
        tailored.setState(state);
        return tailored;
    }

    private static TailoredChange change(TailoredResume tailored, UUID evidenceId,
                                         String category, String original, String tailoredText) {
        TailoredChange change = new TailoredChange();
        ReflectionTestUtils.setField(change, "id", UUID.randomUUID());
        change.setTailoredResume(tailored);
        change.setEvidenceId(evidenceId);
        change.setClaimCategory(category);
        change.setOriginalText(original);
        change.setTailoredText(tailoredText);
        return change;
    }

    private static ResumeEvidence evidence(UUID id, String text) {
        ResumeEvidence evidence = new ResumeEvidence();
        ReflectionTestUtils.setField(evidence, "id", id);
        evidence.setText(text);
        return evidence;
    }
}