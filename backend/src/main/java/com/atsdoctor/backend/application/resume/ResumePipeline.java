package com.atsdoctor.backend.application.resume;

import com.atsdoctor.backend.api.resume.ResumeDto;
import com.atsdoctor.backend.application.ai.AiRequest;
import com.atsdoctor.backend.application.ai.AiResult;
import com.atsdoctor.backend.application.ai.AiTask;
import com.atsdoctor.backend.application.ai.AIService;
import com.atsdoctor.backend.domain.states.ResumeState;
import com.atsdoctor.backend.infrastructure.files.LocalFileStorage;
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
import java.util.UUID;

/**
 * Async processing pipeline for an uploaded resume (FEAT-010/011/012/013/014,
 * TASK-026..033/036): EXTRACTING → (PDF/DOCX/TXT text) → PARSING → (resume_parser
 * AI call, §4.2 validation) → evidence extraction → READY. Any failure lands the
 * version in FAILED with a recorded error. Runs on {@code resumeTaskExecutor};
 * each state mutation is a committed transaction via {@link ResumeService}.
 */
@Component
@ConditionalOnProperty(name = "ats.doctor.persistence.enabled", havingValue = "true")
public class ResumePipeline {

    private static final Logger log = LoggerFactory.getLogger(ResumePipeline.class);

    private final ResumeService resumeService;
    private final LocalFileStorage storage;
    private final TextExtractionService textExtraction;
    private final AIService ai;
    private final Validator validator;

    public ResumePipeline(ResumeService resumeService, LocalFileStorage storage,
                          TextExtractionService textExtraction, AIService ai,
                          Validator validator) {
        this.resumeService = resumeService;
        this.storage = storage;
        this.textExtraction = textExtraction;
        this.ai = ai;
        this.validator = validator;
    }

    @Async("resumeTaskExecutor")
    @EventListener
    public void onUpload(ResumeUploadedEvent event) {
        UUID versionId = event.versionId();
        try {
            resumeService.mark(versionId, ResumeState.EXTRACTING);
            String filename = resumeService.sourceFilename(versionId);
            TextExtractionService.FileType type = TextExtractionService.FileType.fromName(filename);
            if (type == null) {
                throw new ParseException("Unsupported file type for " + filename);
            }
            String rawText = textExtraction.extract(type, Files.readAllBytes(storage.resolve(versionId, filename)));

            resumeService.mark(versionId, ResumeState.PARSING);
            AiResult result = ai.generate(AiRequest.of(AiTask.RESUME_PARSER, rawText));
            ResumeDto dto = ResumeDto.parse(result.output(), validator);

            resumeService.completeSuccess(
                    versionId, rawText, dto, result.output(),
                    result.provider(), result.model(), result.task().promptVersion());
            log.info("Resume version {} reached READY", versionId);
        } catch (Exception ex) {
            resumeService.completeFailure(versionId, ex);
        }
    }
}