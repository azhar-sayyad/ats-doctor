package com.atsdoctor.backend.infrastructure.parsing;

import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Dispatches resume files to the right extractor by extension (FEAT-011/012,
 * TASK-029/030). TXT passes through; parsers validate the actual content and
 * reject mislabeled files.
 */
@Service
public class TextExtractionService {

    public enum FileType {
        PDF, DOCX, TXT;

        public static FileType fromName(String filename) {
            if (filename == null) {
                return null;
            }
            String lower = filename.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".pdf")) {
                return PDF;
            }
            if (lower.endsWith(".docx")) {
                return DOCX;
            }
            if (lower.endsWith(".txt")) {
                return TXT;
            }
            return null;
        }
    }

    private final PdfParser pdfParser;
    private final DocxParser docxParser;

    public TextExtractionService(PdfParser pdfParser, DocxParser docxParser) {
        this.pdfParser = pdfParser;
        this.docxParser = docxParser;
    }

    public String extract(FileType type, byte[] content) throws ParseException {
        return switch (type) {
            case PDF -> pdfParser.extract(content);
            case DOCX -> docxParser.extract(content);
            case TXT -> new String(content, java.nio.charset.StandardCharsets.UTF_8);
        };
    }
}