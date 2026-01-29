package com.atsdoctor.backend.application.export;

import com.atsdoctor.backend.application.tailoring.TailoringConflictException;
import com.atsdoctor.backend.application.tailoring.TailoringNotFoundException;
import com.atsdoctor.backend.infrastructure.persistence.TailoredChangeRepository;
import com.atsdoctor.backend.infrastructure.persistence.TailoredResume;
import com.atsdoctor.backend.infrastructure.persistence.TailoredResumeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.DocumentException;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Tailored-resume export (FEAT-037, SPRINT-06, TASK-079..081): renders the
 * tailored document (post-review changes) as ATS-friendly HTML
 * ({@code templates/export/resume.html}), PDF via Flying Saucer + OpenPDF and
 * DOCX via Apache POI XWPF. Artifacts are stored under
 * {@code ats.doctor.storage.exports-dir}; {@code html}, {@code pdf_path} and
 * {@code docx_path} track them on the row.
 *
 * <p>Gate (locked with the sprint plan): an export is 409 until {@code state}
 * is past the pipeline (READY / NEEDS_REVIEW / APPROVED), every change is
 * resolved (no PENDING) AND the persisted validation report is
 * {@code valid=true}. JSON export reuses the GET /tailored/{id} shape
 * (controller side — §8.2).
 */
@Service
@ConditionalOnProperty(name = "ats.doctor.persistence.enabled", havingValue = "true")
public class ExportService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ISO_INSTANT;

    private final TailoredResumeRepository tailoredResumeRepository;
    private final TailoredChangeRepository tailoredChangeRepository;
    private final SpringTemplateEngine templateEngine;
    private final Path exportsRoot;

    public ExportService(TailoredResumeRepository tailoredResumeRepository,
                         TailoredChangeRepository tailoredChangeRepository,
                         SpringTemplateEngine exportTemplateEngine,
                         @Value("${ats.doctor.storage.exports-dir:data/exports}") String exportsDir) {
        this.tailoredResumeRepository = tailoredResumeRepository;
        this.tailoredChangeRepository = tailoredChangeRepository;
        this.templateEngine = exportTemplateEngine;
        this.exportsRoot = Path.of(exportsDir).toAbsolutePath().normalize();
    }

    public record ExportArtifact(String mediaType, String filename, byte[] content) {
    }

    /** One exportable section: heading (company + title) and tailored bullets. */
    public record Section(String heading, List<String> bullets) {
    }

    /** Rendered-document model shared by the HTML template and the DOCX writer. */
    public record DocModel(String title, String summary, List<Section> sections,
                           Integer scoreBefore, Integer scoreAfter, String generatedAt) {
    }

    /** Export gate — 409 until every change is resolved AND validation passed; 404 unknown. */
    @Transactional(readOnly = true)
    public void assertExportable(UUID tailoredResumeId) {
        TailoredResume tailored = require(tailoredResumeId);
        String state = tailored.getState();
        if (!"READY".equals(state) && !"NEEDS_REVIEW".equals(state) && !"APPROVED".equals(state)) {
            throw new TailoringConflictException(
                    "Tailored resume " + tailoredResumeId + " is not ready for export (state=" + state + ")");
        }
        boolean pending = tailoredChangeRepository.findByTailoredResumeIdOrderByCreatedAtAsc(tailoredResumeId)
                .stream().anyMatch(c -> "PENDING".equals(c.getStatus()));
        if (pending) {
            throw new TailoringConflictException(
                    "Tailored resume " + tailoredResumeId + " has changes still pending review — cannot export");
        }
        if (tailored.getValidation() == null
                || !validationValid(tailored.getValidation())) {
            throw new TailoringConflictException(
                    "Tailored resume " + tailoredResumeId + " has not passed validation — cannot export");
        }
    }

    @Transactional
    public byte[] html(UUID tailoredResumeId) {
        TailoredResume tailored = require(tailoredResumeId);
        assertExportable(tailoredResumeId);
        String html = render(modelOf(tailored));
        tailored.setHtml(html);
        tailoredResumeRepository.save(tailored);
        return html.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Transactional
    public ExportArtifact pdf(UUID tailoredResumeId) throws ExportException {
        TailoredResume tailored = require(tailoredResumeId);
        assertExportable(tailoredResumeId);
        String html = render(modelOf(tailored));
        byte[] content = renderPdf(html);
        Path stored = store(exportsRoot, tailoredResumeId, "pdf", content);
        tailored.setHtml(html);
        tailored.setPdfPath(stored.toString());
        tailoredResumeRepository.save(tailored);
        return new ExportArtifact("application/pdf", "tailored-resume-" + tailoredResumeId + ".pdf", content);
    }

    @Transactional
    public ExportArtifact docx(UUID tailoredResumeId) throws ExportException {
        TailoredResume tailored = require(tailoredResumeId);
        assertExportable(tailoredResumeId);
        byte[] content = renderDocx(modelOf(tailored));
        Path stored = store(exportsRoot, tailoredResumeId, "docx", content);
        tailored.setDocxPath(stored.toString());
        tailoredResumeRepository.save(tailored);
        return new ExportArtifact(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "tailored-resume-" + tailoredResumeId + ".docx", content);
    }

    // ------------------------------------------------------------------

    private static boolean validationValid(String stored) {
        try {
            return MAPPER.readTree(stored).path("valid").asBoolean(false);
        } catch (Exception ex) {
            return false;
        }
    }

    /** Build the document model from the tailored content JSONB (PRD §7.7). */
    private DocModel modelOf(TailoredResume tailored) {
        JsonNode content = parse(tailored.getContent());
        String summary = null;
        if (content.path("summary").isObject() && content.path("summary").has("tailored")) {
            summary = content.path("summary").path("tailored").asText();
            if (summary.isBlank()) {
                summary = null;
            }
        }

        List<JsonNode> entries = new ArrayList<>();
        if (content.path("order").isArray()) {
            for (JsonNode id : content.path("order")) {
                findEntry(content.path("experience"), id.asText()).ifPresent(entries::add);
            }
        }
        if (entries.isEmpty()) {
            content.path("experience").forEach(entries::add);
        }

        List<Section> sections = new ArrayList<>();
        java.util.Set<String> rejectedOriginals = new java.util.HashSet<>();
        for (var row : tailoredChangeRepository.findByTailoredResumeIdOrderByCreatedAtAsc(tailored.getId())) {
            if ("REJECTED".equals(row.getStatus())) {
                rejectedOriginals.add(row.getOriginalText());
            }
        }
        for (JsonNode entry : entries) {
            String company = entry.path("company").asText("");
            String title = entry.path("title").asText("");
            String heading = join(company, title);
            List<String> bullets = new ArrayList<>();
            for (JsonNode bullet : entry.path("bullets")) {
                // Rejected rewrites never ship — the reviewer's "no" restores
                // the original, evidence-derived text in the document.
                String text = rejectedOriginals.contains(bullet.path("original_text").asText(""))
                        ? bullet.path("original_text").asText("")
                        : bullet.path("tailored_text").asText("");
                if (!text.isBlank()) {
                    bullets.add(text);
                }
            }
            if (!bullets.isEmpty()) {
                sections.add(new Section(heading, bullets));
            }
        }
        return new DocModel(
                "Tailored Resume",
                summary,
                sections,
                tailored.getScoreBefore(),
                tailored.getScoreAfter(),
                STAMP.format(Instant.now()));
    }

    private static java.util.Optional<JsonNode> findEntry(JsonNode experience, String id) {
        for (JsonNode entry : experience) {
            if (id.equals(entry.path("id").asText(""))) {
                return java.util.Optional.of(entry);
            }
        }
        return java.util.Optional.empty();
    }

    private static String join(String company, String title) {
        if (company.isBlank()) {
            return title;
        }
        if (title.isBlank()) {
            return company;
        }
        return company + ", " + title;
    }

    private String render(DocModel model) {
        return templateEngine.process("resume", new Context(Locale.ROOT, modelMap(model)));
    }

    private static Map<String, Object> modelMap(DocModel model) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", model.title());
        map.put("summary", model.summary());
        map.put("generated_at", model.generatedAt());
        map.put("sections", model.sections());
        map.put("score_before", model.scoreBefore());
        map.put("score_after", model.scoreAfter());
        return map;
    }

    private static byte[] renderPdf(String html) throws ExportException {
        ITextRenderer renderer = new ITextRenderer();
        renderer.setDocumentFromString(html);
        renderer.layout();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            renderer.createPDF(out);
            return out.toByteArray();
        } catch (DocumentException | IOException ex) {
            throw new ExportException("Could not render PDF", ex);
        }
    }

    private static byte[] renderDocx(DocModel model) throws ExportException {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            heading(doc, model.title(), 16);
            meta(doc, "Generated by ATS Doctor on " + model.generatedAt());
            if (model.summary() != null) {
                heading(doc, "Summary", 12);
                body(doc, model.summary());
            }
            for (Section section : model.sections()) {
                heading(doc, section.heading(), 12);
                for (String bullet : section.bullets()) {
                    bullet(doc, bullet);
                }
            }
            doc.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new ExportException("Could not render DOCX", ex);
        }
    }

    private static void heading(XWPFDocument doc, String text, int size) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.LEFT);
        XWPFRun run = p.createRun();
        run.setBold(true);
        run.setFontSize(size);
        run.setText(text);
    }

    private static void meta(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun run = p.createRun();
        run.setItalic(true);
        run.setFontSize(9);
        run.setText(text);
    }

    private static void body(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.createRun().setText(text);
    }

    private static void bullet(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setIndentationLeft(360);
        p.createRun().setText("\u2022 " + text);
    }

    private static Path store(Path root, UUID id, String ext, byte[] content) throws ExportException {
        try {
            Files.createDirectories(root);
            Path target = root.resolve(id + "." + ext).normalize();
            if (!target.getParent().equals(root)) {
                throw new ExportException("Unsafe export path for " + id);
            }
            Files.write(target, content);
            return target;
        } catch (IOException ex) {
            throw new ExportException("Could not store export for " + id, ex);
        }
    }

    private static JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            return MAPPER.createObjectNode();
        }
        try {
            return MAPPER.readTree(json);
        } catch (Exception ex) {
            return MAPPER.createObjectNode();
        }
    }

    private TailoredResume require(UUID tailoredResumeId) {
        return tailoredResumeRepository.findById(tailoredResumeId)
                .orElseThrow(() -> new TailoringNotFoundException(
                        "No tailored resume found for id " + tailoredResumeId));
    }

    /** Unchecked export failure marker (rendering/storage). */
    public static class ExportException extends RuntimeException {
        public ExportException(String message) {
            super(message);
        }

        public ExportException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}