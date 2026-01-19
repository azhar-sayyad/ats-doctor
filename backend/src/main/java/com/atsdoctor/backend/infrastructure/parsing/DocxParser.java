package com.atsdoctor.backend.infrastructure.parsing;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * DOCX text extraction via Apache POI XWPF (FEAT-012, TASK-030): paragraphs
 * plus table cell contents. Anything that is not a valid OOXML document is
 * rejected.
 */
@Component
public class DocxParser {

    private static final Logger log = LoggerFactory.getLogger(DocxParser.class);

    public String extract(byte[] content) throws ParseException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            StringBuilder text = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                appendLine(text, paragraph.getText());
            }
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    List<String> cells = row.getTableCells().stream()
                            .map(XWPFTableCell::getText)
                            .collect(Collectors.toList());
                    appendLine(text, String.join(" | ", cells));
                }
            }
            String result = text.toString().trim();
            if (result.isEmpty()) {
                throw new ParseException("The DOCX file contains no extractable text.");
            }
            return result;
        } catch (IOException | RuntimeException ex) {
            log.warn("DOCX parsing failed: {}", ex.getMessage());
            throw new ParseException(
                    "Could not read DOCX content — the file is not a valid .docx document.", ex);
        }
    }

    private static void appendLine(StringBuilder text, String line) {
        if (line != null && !line.isBlank()) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(line.trim());
        }
    }
}