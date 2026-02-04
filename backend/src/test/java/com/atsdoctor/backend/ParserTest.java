package com.atsdoctor.backend;

import com.atsdoctor.backend.infrastructure.parsing.DocxParser;
import com.atsdoctor.backend.infrastructure.parsing.ParseException;
import com.atsdoctor.backend.infrastructure.parsing.PdfParser;
import com.atsdoctor.backend.infrastructure.parsing.TextExtractionService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** TASK-029/030 — PDF/DOCX text extraction with generated fixtures. */
class ParserTest {

    private final PdfParser pdfParser = new PdfParser();
    private final DocxParser docxParser = new DocxParser();
    private final TextExtractionService service =
            new TextExtractionService(pdfParser, docxParser);

    @Test
    void extracts_text_from_a_generated_pdf() throws Exception {
        byte[] pdf = generatePdf(true);

        String text = pdfParser.extract(pdf);

        assertThat(text).contains("Jane Doe").contains("Senior Backend Engineer");
    }

    @Test
    void image_only_pdf_fails_gracefully() throws Exception {
        byte[] pdf = generatePdf(false);

        assertThatThrownBy(() -> pdfParser.extract(pdf))
                .isInstanceOf(ParseException.class)
                .hasMessageContaining("image-based");
    }

    @Test
    void extracts_paragraphs_and_tables_from_a_generated_docx() throws Exception {
        byte[] docx = generateDocx();

        String text = docxParser.extract(docx);

        assertThat(text).contains("Software Engineer at Tech Corp")
                .contains("Python | PostgreSQL");
    }

    @Test
    void rejects_non_docx_content() {
        byte[] notDocx = "this is definitely not a zip archive".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> docxParser.extract(notDocx))
                .isInstanceOf(ParseException.class)
                .hasMessageContaining("not a valid .docx");
    }

    @Test
    void txt_passes_through() throws Exception {
        String text = service.extract(TextExtractionService.FileType.TXT,
                "Jane Doe\nSenior Engineer".getBytes(StandardCharsets.UTF_8));

        assertThat(text).contains("Jane Doe");
    }

    @Test
    void dispatches_pdf_through_the_service() throws Exception {
        byte[] pdf = generatePdf(true);

        String text = service.extract(TextExtractionService.FileType.PDF, pdf);

        assertThat(text).contains("Jane Doe");
    }

    private static byte[] generatePdf(boolean withText) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            if (withText) {
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(50, 700);
                    stream.showText("Jane Doe");
                    stream.newLineAtOffset(0, -16);
                    stream.showText("Senior Backend Engineer");
                    stream.endText();
                }
            }
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                document.save(out);
                return out.toByteArray();
            }
        }
    }

    private static byte[] generateDocx() throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText("Software Engineer at Tech Corp");

            XWPFTable table = document.createTable(2, 2);
            XWPFTableRow row = table.getRow(0);
            row.getCell(0).setText("Python");
            row.getCell(1).setText("PostgreSQL");

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                document.write(out);
                return out.toByteArray();
            }
        }
    }
}