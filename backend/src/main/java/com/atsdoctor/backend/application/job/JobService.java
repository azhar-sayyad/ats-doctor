package com.atsdoctor.backend.application.job;

import com.atsdoctor.backend.api.jobs.JobDto;
import com.atsdoctor.backend.api.jobs.JobResponse;
import com.atsdoctor.backend.domain.states.JobState;
import com.atsdoctor.backend.domain.states.StateMachines;
import com.atsdoctor.backend.infrastructure.files.LocalFileStorage;
import com.atsdoctor.backend.infrastructure.parsing.RequirementExtractor;
import com.atsdoctor.backend.infrastructure.persistence.Job;
import com.atsdoctor.backend.infrastructure.persistence.JobRepository;
import com.atsdoctor.backend.infrastructure.persistence.JobRequirement;
import com.atsdoctor.backend.infrastructure.persistence.JobRequirementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JD orchestration (FEAT-017/020/021, TASK-039/043/045). The pipeline runs
 * asynchronously ({@link JobPipeline}); every state change goes through
 * {@link StateMachines#job()}. {@link RequirementExtractor} normalizes the
 * {@code requirement_extraction} AI output into {@code job_requirements} rows.
 *
 * <p>Like the rest of the persistence stack, the jobs API exists only when
 * {@code ats.doctor.persistence.enabled=true} (the app must boot without a DB,
 * TASK-006).
 */
@Service
@ConditionalOnProperty(name = "ats.doctor.persistence.enabled", havingValue = "true")
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    /** Column widths from V3__create_job_tables.sql — persist-safe caps for AI-parsed values. */
    private static final int MAX_TITLE = 255;
    private static final int MAX_COMPANY = 255;
    private static final int MAX_LOCATION = 255;
    private static final int MAX_SENIORITY = 64;
    private static final int MAX_MODEL = 255;
    private static final int MAX_PROMPT_VERSION = 64;

    private final JobRepository jobRepository;
    private final JobRequirementRepository requirementRepository;
    private final LocalFileStorage storage;
    private final RequirementExtractor requirementExtractor;
    private final ApplicationEventPublisher publisher;

    public JobService(JobRepository jobRepository,
                      JobRequirementRepository requirementRepository,
                      LocalFileStorage storage,
                      RequirementExtractor requirementExtractor,
                      ApplicationEventPublisher publisher) {
        this.jobRepository = jobRepository;
        this.requirementRepository = requirementRepository;
        this.storage = storage;
        this.requirementExtractor = requirementExtractor;
        this.publisher = publisher;
    }

    /** Pasted text JD (FEAT-017): raw_text is already the JD, no file stored. */
    @Transactional
    public JobResponse createFromText(String text) {
        Job job = new Job();
        job.setState(JobState.CREATED.name());
        job.setRawText(text);
        Job saved = jobRepository.saveAndFlush(job);
        schedule(saved.getId());
        return toResponse(saved);
    }

    /** File JD: stores the original file, raw_text filled by the pipeline. */
    @Transactional
    public JobResponse createFromFile(String filename, byte[] content) {
        Job job = new Job();
        job.setState(JobState.CREATED.name());
        job.setSourceFilename(filename);
        job.setError(null);
        Job saved = jobRepository.saveAndFlush(job);
        try {
            storage.storeJob(saved.getId(), filename, content);
        } catch (IOException ex) {
            throw new JobStorageException("Could not store the JD file: " + ex.getMessage());
        }
        schedule(saved.getId());
        return toResponse(saved);
    }

    /** Pipeline step: CREATED → EXTRACTING, EXTRACTING → PARSING. */
    @Transactional
    public void mark(UUID jobId, JobState to) {
        Job job = require(jobId);
        transition(job, to);
        jobRepository.save(job);
    }

    /** Pipeline success: PARSING → READY with text, structured JD + requirements. */
    @Transactional
    public JobResponse completeSuccess(UUID jobId, String rawText, JobDto dto,
                                       String jdOutput, String requirementOutput,
                                       String modelUsed, String modelVersion, String promptVersion) {
        Job job = require(jobId);
        transition(job, JobState.READY);
        job.setRawText(rawText);
        job.setStructuredData(jdOutput);
        job.setTitle(cap(dto.job() == null ? null : dto.job().title(), MAX_TITLE));
        job.setCompany(cap(dto.job() == null ? null : dto.job().company(), MAX_COMPANY));
        job.setLocation(cap(dto.job() == null ? null : dto.job().location(), MAX_LOCATION));
        job.setSeniority(cap(dto.job() == null ? null : dto.job().seniority(), MAX_SENIORITY));
        job.setModelUsed(cap(modelUsed, MAX_MODEL));
        job.setModelVersion(cap(modelVersion, MAX_MODEL));
        job.setPromptVersion(cap(promptVersion, MAX_PROMPT_VERSION));
        job.setTemperature(dto.metadata() == null ? null : dto.metadata().temperature());
        job.setError(null);
        replaceRequirements(job, requirementOutput);
        jobRepository.save(job);
        return toResponse(job);
    }

    /** Pipeline failure: any active state → FAILED with recorded error. */
    @Transactional
    public JobResponse completeFailure(UUID jobId, Throwable error) {
        Job job = require(jobId);
        transition(job, JobState.FAILED);
        job.setError(messageOf(error));
        jobRepository.save(job);
        log.warn("JD pipeline failed for job {}: {}", jobId, messageOf(error));
        return toResponse(job);
    }

    /** Pipeline lookup: row needed to decide extraction vs already-having text. */
    @Transactional(readOnly = true)
    public JobLookup lookup(UUID jobId) {
        Job job = require(jobId);
        return new JobLookup(job.getRawText(), job.getSourceFilename());
    }

    @Transactional(readOnly = true)
    public List<JobResponse> list() {
        return jobRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public JobResponse byId(UUID jobId) {
        return toResponse(require(jobId));
    }

    /** Delete a job regardless of state (GET/DELETE endpoints, FEAT-021). */
    @Transactional
    public void delete(UUID jobId) {
        Job job = require(jobId);
        jobRepository.delete(job);
    }

    private void schedule(UUID jobId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publisher.publishEvent(new JobCreatedEvent(jobId));
            }
        });
    }

    private void transition(Job job, JobState to) {
        JobState next = StateMachines.job()
                .transition(JobState.valueOf(job.getState()), to);
        job.setState(next.name());
    }

    private void replaceRequirements(Job job, String requirementOutput) {
        requirementRepository.deleteAllInBatch(requirementRepository.findByJobId(job.getId()));
        for (RequirementExtractor.ExtractedRequirement item : requirementExtractor.extract(requirementOutput)) {
            JobRequirement row = new JobRequirement();
            row.setJob(job);
            row.setText(item.text());
            row.setType(item.type());
            row.setImportance(item.importance());
            row.setKeywords(item.keywords().toArray(String[]::new));
            requirementRepository.save(row);
        }
    }

    private Job require(UUID jobId) {
        return jobRepository.findById(jobId).orElseThrow(() ->
                new JobNotFoundException("No job found for id " + jobId));
    }

    private static String messageOf(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    /** Truncate an AI-parsed value to its DB column width so an overlong value can never fail the pipeline. */
    private static String cap(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private JobResponse toResponse(Job job) {
        List<JobRequirement> requirements = requirementRepository.findByJobId(job.getId());
        long high = requirements.stream().filter(r -> "high".equals(r.getImportance())).count();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", requirements.size());
        summary.put("high", high);
        return new JobResponse(
                job.getId(),
                job.getState(),
                job.getTitle(),
                job.getCompany(),
                job.getLocation(),
                job.getSeniority(),
                job.getRawText(),
                job.getStructuredData(),
                job.getSourceFilename(),
                job.getModelUsed(),
                job.getModelVersion(),
                job.getPromptVersion(),
                job.getTemperature(),
                job.getError(),
                summary,
                job.getCreatedAt(),
                job.getUpdatedAt());
    }

    /** Pipeline lookup result: raw_text already present (pasted) or file to read. */
    public record JobLookup(String rawText, String sourceFilename) {
    }
}