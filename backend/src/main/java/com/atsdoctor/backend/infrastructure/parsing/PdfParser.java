package com.atsdoctor.backend.infrastructure.parsing;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * PDF text extraction via Apache PDFBox (FEAT-011, TASK-029). Image-only PDFs
 * yield no text and are rejected with a graceful error.
 */
@Component
public class PdfParser {

    private static final Logger log = LoggerFactory.getLogger(PdfParser.class);

    public String extract(byte[] content) throws ParseException {
        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            if (text == null || text.isBlank()) {
                throw new ParseException(
                        "No extractable text found — the PDF appears to be image-based. "
                                + "Upload a text-based PDF or a .txt/.docx resume.");
            }
            return text;
        } catch (ParseException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ParseException("Could not read PDF content: " + ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            log.warn("Unexpected PDF parsing failure", ex);
            throw new ParseException("Could not parse the PDF file.", ex);
        }
    }
}