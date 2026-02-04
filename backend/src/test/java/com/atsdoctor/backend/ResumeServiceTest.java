package com.atsdoctor.backend;

import com.atsdoctor.backend.api.resume.ResumeDto;
import com.atsdoctor.backend.api.resume.ResumeVersionResponse;
import com.atsdoctor.backend.application.resume.ResumeNotReadyException;
import com.atsdoctor.backend.application.resume.ResumeService;
import com.atsdoctor.backend.domain.states.ResumeState;
import com.atsdoctor.backend.infrastructure.files.LocalFileStorage;
import com.atsdoctor.backend.infrastructure.parsing.EvidenceExtractor;
import com.atsdoctor.backend.infrastructure.persistence.Resume;
import com.atsdoctor.backend.infrastructure.persistence.ResumeEvidence;
import com.atsdoctor.backend.infrastructure.persistence.ResumeEvidenceRepository;
import com.atsdoctor.backend.infrastructure.persistence.ResumeRepository;
import com.atsdoctor.backend.infrastructure.persistence.ResumeVersion;
import com.atsdoctor.backend.infrastructure.persistence.ResumeVersionRepository;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** TASK-035/036/038 — resume orchestration and state machine enforcement. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResumeServiceTest {

    @Mock
    private ResumeRepository resumeRepository;
    @Mock
    private ResumeVersionRepository versionRepository;
    @Mock
    private ResumeEvidenceRepository evidenceRepository;
    @Mock
    private LocalFileStorage storage;
    @Mock
    private ApplicationEventPublisher publisher;

    private final EvidenceExtractor evidenceExtractor = new EvidenceExtractor();
    private final jakarta.validation.Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    private ResumeService service;
    private final java.util.ArrayList<ResumeEvidence> savedEvidence = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new ResumeService(resumeRepository, versionRepository, evidenceRepository,
                storage, evidenceExtractor, publisher, validator);
        savedEvidence.clear();
        when(evidenceRepository.save(any(ResumeEvidence.class))).thenAnswer(inv -> {
            ResumeEvidence row = inv.getArgument(0);
            savedEvidence.add(row);
            return row;
        });
        when(evidenceRepository.findByResumeVersionId(any())).thenAnswer(inv -> savedEvidence);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void upload_creates_resume_and_first_version() throws Exception {
        when(resumeRepository.findByName(ResumeService.MASTER_RESUME_NAME)).thenReturn(Optional.empty());
        when(resumeRepository.save(any(Resume.class))).thenAnswer(inv -> {
            Resume r = inv.getArgument(0);
            r.setName(ResumeService.MASTER_RESUME_NAME);
            return r;
        });
        when(versionRepository.findTopByResumeIdOrderByVersionDesc(any())).thenReturn(Optional.empty());
        when(versionRepository.saveAndFlush(any(ResumeVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        ResumeVersionResponse response = service.upload("master.pdf", new byte[]{1, 2, 3});

        assertThat(response.state()).isEqualTo("UPLOADED");
        assertThat(response.version()).isEqualTo(1);
        assertThat(response.sourceFilename()).isEqualTo("master.pdf");
        verify(storage).store(any(), anyString(), any(byte[].class));
    }

    @Test
    void upload_increments_the_version_number() throws Exception {
        Resume resume = new Resume();
        when(resumeRepository.findByName(ResumeService.MASTER_RESUME_NAME)).thenReturn(Optional.of(resume));
        ResumeVersion previous = new ResumeVersion();
        previous.setVersion(2);
        when(versionRepository.findTopByResumeIdOrderByVersionDesc(resume.getId())).thenReturn(Optional.of(previous));
        when(versionRepository.saveAndFlush(any(ResumeVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        ResumeVersionResponse response = service.upload("master.pdf", new byte[]{1});

        assertThat(response.version()).isEqualTo(3);
    }

    @Test
    void mark_enforces_the_state_machine() {
        ResumeVersion version = version(ResumeState.UPLOADED);

        service.mark(version.getId(), ResumeState.EXTRACTING);

        verify(versionRepository).save(argState(ResumeState.EXTRACTING));
    }

    @Test
    void mark_rejects_illegal_transitions() {
        ResumeVersion version = version(ResumeState.UPLOADED);

        assertThatThrownBy(() -> service.mark(version.getId(), ResumeState.PARSING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UPLOADED");
    }

    @Test
    void complete_success_persists_structured_data_and_evidence() {
        ResumeVersion version = version(ResumeState.PARSING);

        ResumeDto dto = parse("{\"basics\":{\"name\":\"Jane\"},\"skills\":[{\"name\":\"Python\"}]}");
        ResumeVersionResponse response = service.completeSuccess(
                version.getId(), "raw text", dto, ResumeDto.toJson(dto),
                "stub", "stub", "resume-parser-v1");

        assertThat(response.state()).isEqualTo("READY");
        assertThat(response.rawText()).isEqualTo("raw text");
        assertThat(response.evidenceSummary().get("a")).isEqualTo(1L);
        assertThat(response.evidenceSummary().get("total")).isEqualTo(1);
        verify(versionRepository).save(argState(ResumeState.READY));
    }

    @Test
    void complete_failure_marks_failed_with_error() {
        ResumeVersion version = version(ResumeState.PARSING);
        when(versionRepository.save(any(ResumeVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        ResumeVersionResponse response = service.completeFailure(version.getId(),
                new IllegalStateException("boom"));

        assertThat(response.state()).isEqualTo("FAILED");
        assertThat(response.error()).isEqualTo("boom");
    }

    @Test
    void edit_requires_ready_state() {
        ResumeVersion version = version(ResumeState.UPLOADED);

        assertThatThrownBy(() -> service.edit(version.getId(), "{}"))
                .isInstanceOf(ResumeNotReadyException.class)
                .hasMessageContaining("READY");
    }

    @Test
    void edit_creates_next_immutable_version() {
        ResumeVersion version = version(ResumeState.READY);
        when(versionRepository.findTopByResumeIdOrderByVersionDesc(version.getResume().getId()))
                .thenReturn(Optional.of(version));
        when(versionRepository.saveAndFlush(any(ResumeVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        String edited = "{\"basics\":{\"name\":\"Jane\"},\"summary\":\"Edited summary.\"}";
        ResumeVersionResponse response = service.edit(version.getId(), edited);

        assertThat(response.state()).isEqualTo("READY");
        assertThat(response.version()).isEqualTo(2);
        assertThat(response.structuredData()).contains("Edited summary.");
        // the edited row is a NEW version — the source row is never mutated
        assertThat(version.getStructuredData()).isNull();
        assertThat(version.getState()).isEqualTo("READY");
    }

    @Test
    void edit_rejects_non_master_resume_versions() {
        Resume resume = new Resume();
        resume.setName("TailoredVariant");
        ResumeVersion version = new ResumeVersion();
        version.setResume(resume);
        version.setState(ResumeState.READY.name());
        when(versionRepository.findById(version.getId())).thenReturn(Optional.of(version));

        assertThatThrownBy(() -> service.edit(version.getId(), "{\"basics\":{\"name\":\"Jane\"}}"))
                .isInstanceOf(com.atsdoctor.backend.application.resume.ResumeValidationException.class)
                .hasMessageContaining("source of truth");
    }

    @Test
    void edit_rejects_invalid_structured_data() {
        ResumeVersion version = version(ResumeState.READY);

        assertThatThrownBy(() -> service.edit(version.getId(), "{\"basics\":{}}"))
                .isInstanceOf(com.atsdoctor.backend.application.resume.ResumeValidationException.class)
                .hasMessageContaining("basics.name");
    }

    private ResumeVersion version(ResumeState state) {
        Resume resume = new Resume();
        resume.setName(ResumeService.MASTER_RESUME_NAME);
        ResumeVersion version = new ResumeVersion();
        version.setResume(resume);
        version.setState(state.name());
        version.setVersion(1);
        when(versionRepository.findById(version.getId())).thenReturn(Optional.of(version));
        return version;
    }

    private static ResumeDto parse(String json) {
        return ResumeDto.parse(json, Validation.buildDefaultValidatorFactory().getValidator());
    }

    private static ResumeVersion argState(ResumeState state) {
        return org.mockito.ArgumentMatchers.argThat(
                (ResumeVersion v) -> state.name().equals(v.getState()));
    }
}