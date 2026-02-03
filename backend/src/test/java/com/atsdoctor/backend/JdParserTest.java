package com.atsdoctor.backend;

import com.atsdoctor.backend.application.ai.AiRequest;
import com.atsdoctor.backend.application.ai.AiResult;
import com.atsdoctor.backend.application.ai.AiTask;
import com.atsdoctor.backend.application.ai.AIService;
import com.atsdoctor.backend.infrastructure.parsing.DocxParser;
import com.atsdoctor.backend.infrastructure.parsing.JdParser;
import com.atsdoctor.backend.infrastructure.parsing.PdfParser;
import com.atsdoctor.backend.infrastructure.parsing.ParseException;
import com.atsdoctor.backend.infrastructure.parsing.TextExtractionService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** TASK-040/041 — JD extraction reuses the PDF/DOCX/TXT pipeline; structure → jd_parser. */
@ExtendWith(MockitoExtension.class)
class JdParserTest {

    @Mock
    private AIService ai;

    private JdParser parser() {
        return new JdParser(new TextExtractionService(new PdfParser(), new DocxParser()), ai);
    }

    @Test
    void extracts_txt_jd_text() throws Exception {
        JdParser parser = parser();

        String text = parser.extract(TextExtractionService.FileType.TXT,
                "Senior Backend Engineer\nPython, FastAPI".getBytes(StandardCharsets.UTF_8));

        assertThat(text).contains("Senior Backend Engineer").contains("Python");
    }

    @Test
    void extracts_pdf_jd_text() throws Exception {
        JdParser parser = parser();

        String text = parser.extract(TextExtractionService.FileType.PDF, generatePdf());

        assertThat(text).contains("Senior Backend Engineer");
    }

    @Test
    void image_only_pdf_fails_gracefully() throws Exception {
        JdParser parser = parser();

        assertThatThrownBy(() -> parser.extract(TextExtractionService.FileType.PDF, generatePdfWithoutText()))
                .isInstanceOf(ParseException.class)
                .hasMessageContaining("image-based");
    }

    @Test
    void structure_calls_the_jd_parser_task() {
        when(ai.generate(any(AiRequest.class))).thenReturn(new AiResult(
                AiTask.JD_PARSER, "{\"job\": {\"title\": \"Backend Engineer\"}}",
                "stub", "stub", 5L, Map.of()));

        AiResult result = parser().structure("Senior Backend Engineer\nPython");

        assertThat(result.task()).isEqualTo(AiTask.JD_PARSER);
        verify(ai).generate(any(AiRequest.class));
    }

    private static byte[] generatePdf() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText("Senior Backend Engineer");
                stream.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] generatePdfWithoutText() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }
}