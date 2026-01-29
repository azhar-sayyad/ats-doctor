package com.atsdoctor.backend.application.resume;

import com.atsdoctor.backend.api.resume.ResumeDto;
import com.atsdoctor.backend.api.resume.ResumeVersionResponse;
import com.atsdoctor.backend.domain.states.ResumeState;
import com.atsdoctor.backend.infrastructure.files.LocalFileStorage;
import com.atsdoctor.backend.infrastructure.parsing.EvidenceExtractor;
import com.atsdoctor.backend.infrastructure.persistence.Resume;
import com.atsdoctor.backend.infrastructure.persistence.ResumeEvidence;
import com.atsdoctor.backend.infrastructure.persistence.ResumeEvidenceRepository;
import com.atsdoctor.backend.infrastructure.persistence.ResumeRepository;
import com.atsdoctor.backend.infrastructure.persistence.ResumeVersion;
import com.atsdoctor.backend.infrastructure.persistence.ResumeVersionRepository;
import com.atsdoctor.backend.domain.states.StateMachines;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Master resume orchestration (FEAT-015/016, TASK-035/036/038). The pipeline
 * runs asynchronously ({@link ResumePipeline}); every state change goes through
 * {@link StateMachines#resume()} (TASK-036). Versions are **append-only**
 * (FEAT-044/TASK-083): uploads and edits create a new immutable
 * {@code resume_version} row; old versions are never mutated after READY.
 * Every analysis/tailoring run references the exact master version
 * (source of truth, PRD §5.9 — TASK-084).
 *
 * <p>Like the rest of the persistence stack, the resume API exists only when
 * {@code ats.doctor.persistence.enabled=true} (the app must boot without a DB,
 * TASK-006).
 */
@Service
@ConditionalOnProperty(name = "ats.doctor.persistence.enabled", havingValue = "true")
public class ResumeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);

    public static final String MASTER_RESUME_NAME = "Master";

    private final ResumeRepository resumeRepository;
    private final ResumeVersionRepository versionRepository;
    private final ResumeEvidenceRepository evidenceRepository;
    private final LocalFileStorage storage;
    private final EvidenceExtractor evidenceExtractor;
    private final ApplicationEventPublisher publisher;
    private final Validator validator;

    public ResumeService(ResumeRepository resumeRepository,
                         ResumeVersionRepository versionRepository,
                         ResumeEvidenceRepository evidenceRepository,
                         LocalFileStorage storage,
                         EvidenceExtractor evidenceExtractor,
                         ApplicationEventPublisher publisher,
                         Validator validator) {
        this.resumeRepository = resumeRepository;
        this.versionRepository = versionRepository;
        this.evidenceRepository = evidenceRepository;
        this.storage = storage;
        this.evidenceExtractor = evidenceExtractor;
        this.publisher = publisher;
        this.validator = validator;
    }

    /**
     * Creates the next resume_version row (state UPLOADED), stores the file
     * under data/resumes and schedules the async pipeline after commit.
     */
    @Transactional
    public ResumeVersionResponse upload(String filename, byte[] content) {
        Resume resume = resumeRepository.findByName(MASTER_RESUME_NAME).orElseGet(() -> {
            Resume created = new Resume();
            created.setName(MASTER_RESUME_NAME);
            return resumeRepository.save(created);
        });

        int versionNumber = nextVersionNumber(resume.getId());

        ResumeVersion version = new ResumeVersion();
        version.setResume(resume);
        version.setVersion(versionNumber);
        version.setState(ResumeState.UPLOADED.name());
        version.setSourceFilename(filename);
        // New upload always starts clean: a previous FAILED parse is superseded.
        version.setError(null);
        ResumeVersion saved = versionRepository.saveAndFlush(version);

        try {
            storage.store(saved.getId(), filename, content);
        } catch (IOException ex) {
            throw new ResumeStorageException("Could not store the uploaded resume: " + ex.getMessage());
        }

        UUID versionId = saved.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publisher.publishEvent(new ResumeUploadedEvent(versionId));
            }
        });
        return toResponse(saved);
    }

    /** Pipeline step: UPLOADED → EXTRACTING, EXTRACTING → PARSING. */
    @Transactional
    public void mark(UUID versionId, ResumeState to) {
        ResumeVersion version = require(versionId);
        transition(version, to);
        versionRepository.save(version);
    }

    /** Pipeline success: PARSING → READY with text, structured data + evidence. */
    @Transactional
    public ResumeVersionResponse completeSuccess(UUID versionId, String rawText, ResumeDto dto,
                                                 String aiOutput, String modelUsed, String modelVersion,
                                                 String promptVersion) {
        ResumeVersion version = require(versionId);
        transition(version, ResumeState.READY);
        version.setRawText(rawText);
        version.setStructuredData(aiOutput);
        version.setModelUsed(modelUsed);
        version.setModelVersion(modelVersion);
        version.setPromptVersion(promptVersion);
        version.setTemperature(dto.metadata() == null ? null : dto.metadata().temperature());
        version.setError(null);
        replaceEvidence(version, dto);
        versionRepository.save(version);
        return toResponse(version);
    }

    /** Pipeline failure: any active state → FAILED with recorded error. */
    @Transactional
    public ResumeVersionResponse completeFailure(UUID versionId, Throwable error) {
        ResumeVersion version = require(versionId);
        transition(version, ResumeState.FAILED);
        version.setError(messageOf(error));
        versionRepository.save(version);
        log.warn("Resume pipeline failed for version {}: {}", versionId, messageOf(error));
        return toResponse(version);
    }

    /**
     * PUT edit (TASK-038, FEAT-044/TASK-083): re-validates the §4.2 structured
     * data, then creates the next immutable version ({@code version+1}) with
     * the edited content + regenerated evidence. The edited source version is
     * never mutated — the unique {@code (resume_id, version)} index guards the
     * increment. State of the new version is READY.
     */
    @Transactional
    public ResumeVersionResponse edit(UUID versionId, String structuredDataJson) {
        ResumeVersion version = require(versionId);
        if (!ResumeState.READY.name().equals(version.getState())) {
            throw new ResumeNotReadyException(
                    "Resume version " + versionId + " is " + version.getState()
                            + " — edits require state READY.");
        }
        requireMasterSource(version);
        ResumeDto dto = ResumeDto.parse(structuredDataJson, validator);
        int nextVersion = nextVersionNumber(version.getResume().getId());
        ResumeVersion next = new ResumeVersion();
        next.setResume(version.getResume());
        next.setVersion(nextVersion);
        next.setState(ResumeState.READY.name());
        next.setRawText(version.getRawText());
        next.setStructuredData(ResumeDto.toJson(dto));
        next.setSourceFilename(version.getSourceFilename());
        // Edited content supersedes the model's output; model/prompt metadata
        // carry over so the evidence lineage stays visible in the response.
        next.setModelUsed(version.getModelUsed());
        next.setModelVersion(version.getModelVersion());
        next.setPromptVersion(version.getPromptVersion());
        next.setTemperature(version.getTemperature());
        ResumeVersion saved = versionRepository.saveAndFlush(next);
        replaceEvidence(saved, dto);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ResumeVersionResponse current() {
        Resume resume = resumeRepository.findByName(MASTER_RESUME_NAME).orElse(null);
        if (resume == null) {
            return null;
        }
        return versionRepository.findTopByResumeIdOrderByVersionDesc(resume.getId())
                .map(this::toResponse)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public ResumeVersionResponse byId(UUID versionId) {
        return toResponse(require(versionId));
    }

    /** Pipeline lookup: original filename of a version (file path is derived). */
    @Transactional(readOnly = true)
    public String sourceFilename(UUID versionId) {
        return require(versionId).getSourceFilename();
    }

    /**
     * Source-of-truth enforcement (FEAT-044, TASK-084, PRD §5.9): only master
     * resume versions are editable/analyzable. A tailored-resume-derived
     * version (a different {@code resumes} row) is rejected — tailoring never
     * chains.
     */
    private void requireMasterSource(ResumeVersion version) {
        String name = version.getResume() == null ? null : version.getResume().getName();
        if (!MASTER_RESUME_NAME.equals(name)) {
            throw new ResumeValidationException(
                    "Resume version " + version.getId() + " does not belong to the master resume"
                            + " (source of truth) — tailored versions cannot be edited or re-analyzed (PRD §5.9).");
        }
    }

    private int nextVersionNumber(UUID resumeId) {
        return versionRepository
                .findTopByResumeIdOrderByVersionDesc(resumeId)
                .map(v -> v.getVersion() + 1)
                .orElse(1);
    }

    private void transition(ResumeVersion version, ResumeState to) {
        ResumeState next = StateMachines.resume()
                .transition(ResumeState.valueOf(version.getState()), to);
        version.setState(next.name());
    }

    private void replaceEvidence(ResumeVersion version, ResumeDto dto) {
        evidenceRepository.deleteAll(evidenceRepository.findByResumeVersionId(version.getId()));
        for (EvidenceExtractor.ExtractedEvidence item : evidenceExtractor.extract(dto)) {
            ResumeEvidence row = new ResumeEvidence();
            row.setResumeVersion(version);
            row.setSection(item.section());
            row.setSectionId(item.sectionId());
            row.setText(item.text());
            row.setNormalizedText(item.normalizedText());
            row.setMetadata(item.metadataJson());
            row.setClaimCategory(item.claimCategory());
            row.setSourceRefs(new UUID[0]);
            evidenceRepository.save(row);
        }
    }

    private ResumeVersion require(UUID versionId) {
        return versionRepository.findById(versionId).orElseThrow(() ->
                new ResumeNotFoundException("No resume version found for id " + versionId));
    }

    private static String messageOf(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private ResumeVersionResponse toResponse(ResumeVersion version) {
        List<ResumeEvidence> evidence = evidenceRepository.findByResumeVersionId(version.getId());
        long a = evidence.stream().filter(e -> "A".equals(e.getClaimCategory())).count();
        long b = evidence.stream().filter(e -> "B".equals(e.getClaimCategory())).count();
        long c = evidence.stream().filter(e -> "C".equals(e.getClaimCategory())).count();
        Map<String, Object> summary = Map.of("a", a, "b", b, "c", c, "total", evidence.size());
        return new ResumeVersionResponse(
                version.getId(),
                version.getResume().getId(),
                version.getVersion(),
                version.getState(),
                version.getRawText(),
                version.getStructuredData(),
                version.getSourceFilename(),
                version.getModelUsed(),
                version.getModelVersion(),
                version.getPromptVersion(),
                version.getTemperature(),
                version.getError(),
                summary,
                version.getCreatedAt(),
                version.getUpdatedAt());
    }
}