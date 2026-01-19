package com.atsdoctor.backend.infrastructure.parsing;

import com.atsdoctor.backend.application.ai.AiRequest;
import com.atsdoctor.backend.application.ai.AiResult;
import com.atsdoctor.backend.application.ai.AiTask;
import com.atsdoctor.backend.application.ai.AIService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * JD parsing seam (FEAT-018/019, TASK-040/041): text extraction reuses the
 * PDF/DOCX/TXT pipeline from SPRINT-01; structuring calls the {@code jd_parser}
 * AI task (jd-parser-v1) and every call is recorded in {@code ai_runs} by the
 * facade. Only exists with persistence enabled (used by {@code JobPipeline}).
 */
@Component
@ConditionalOnProperty(name = "ats.doctor.persistence.enabled", havingValue = "true")
public class JdParser {

    private final TextExtractionService textExtraction;
    private final AIService ai;

    public JdParser(TextExtractionService textExtraction, AIService ai) {
        this.textExtraction = textExtraction;
        this.ai = ai;
    }

    /** Extracts raw JD text (PDF/DOCX/TXT). @throws ParseException on bad files */
    public String extract(TextExtractionService.FileType type, byte[] content) throws ParseException {
        return textExtraction.extract(type, content);
    }

    /** Structures raw JD text via {@code jd_parser} (TASK-041). */
    public AiResult structure(String rawText) {
        return ai.generate(AiRequest.of(AiTask.JD_PARSER, rawText));
    }
}