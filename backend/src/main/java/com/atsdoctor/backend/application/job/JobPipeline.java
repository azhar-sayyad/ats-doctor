package com.atsdoctor.backend.application.job;

import com.atsdoctor.backend.api.jobs.JobDto;
import com.atsdoctor.backend.application.ai.AiRequest;
import com.atsdoctor.backend.application.ai.AiResult;
import com.atsdoctor.backend.application.ai.AiTask;
import com.atsdoctor.backend.application.ai.AIService;
import com.atsdoctor.backend.domain.states.JobState;
import com.atsdoctor.backend.infrastructure.files.LocalFileStorage;
import com.atsdoctor.backend.infrastructure.parsing.JdParser;
import com.atsdoctor.backend.infrastructure.parsing.ParseException;
import com.atsdoctor.backend.infrastructure.parsing.TextExtractionService;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Async processing pipeline for a JD (FEAT-017/018/019/020/021,
 * TASK-039..045): EXTRACTING → (PDF/DOCX/TXT text when uploaded as a file) →
 * PARSING → (jd_parser AI call, §4.3 validation, requirement_extraction AI
 * call) → job_requirements rows → READY. Any failure lands the job in FAILED
 * with a recorded error. Runs on {@code resumeTaskExecutor}; each state
 * mutation is a committed transaction via {@link JobService}.
 */
@Component
@ConditionalOnProperty(name = "ats.doctor.persistence.enabled", havingValue = "true")
public class JobPipeline {

    private static final Logger log = LoggerFactory.getLogger(JobPipeline.class);

    private final JobService jobService;
    private final JdParser jdParser;
    private final LocalFileStorage storage;
    private final AIService ai;
    private final Validator validator;

    public JobPipeline(JobService jobService, JdParser jdParser, LocalFileStorage storage,
                       AIService ai, Validator validator) {
        this.jobService = jobService;
        this.jdParser = jdParser;
        this.storage = storage;
        this.ai = ai;
        this.validator = validator;
    }

    @Async("resumeTaskExecutor")
    @EventListener
    public void onCreated(JobCreatedEvent event) {
        UUID jobId = event.jobId();
        try {
            jobService.mark(jobId, JobState.EXTRACTING);
            JobService.JobLookup lookup = jobService.lookup(jobId);

            String rawText = lookup.rawText();
            if (rawText == null) {
                if (lookup.sourceFilename() == null) {
                    throw new ParseException("JD has neither pasted text nor an uploaded file for " + jobId);
                }
                TextExtractionService.FileType type = TextExtractionService.FileType.fromName(lookup.sourceFilename());
                if (type == null) {
                    throw new ParseException("Unsupported file type for " + lookup.sourceFilename());
                }
                Path stored = storage.resolveJob(jobId, lookup.sourceFilename());
                rawText = jdParser.extract(type, Files.readAllBytes(stored));
            }

            jobService.mark(jobId, JobState.PARSING);
            AiResult parsed = jdParser.structure(rawText);
            JobDto dto = JobDto.parse(parsed.output(), validator);

            AiResult requirements = ai.generate(AiRequest.of(AiTask.REQUIREMENT_EXTRACTION, JobDto.toJson(dto)));

            jobService.completeSuccess(
                    jobId, rawText, dto, parsed.output(), requirements.output(),
                    parsed.provider(), parsed.model(), parsed.task().promptVersion());
            log.info("Job {} reached READY", jobId);
        } catch (Exception ex) {
            jobService.completeFailure(jobId, ex);
        }
    }
}