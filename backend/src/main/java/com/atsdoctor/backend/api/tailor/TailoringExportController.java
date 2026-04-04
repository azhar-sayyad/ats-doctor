package com.atsdoctor.backend.api.tailor;

import com.atsdoctor.backend.application.export.ExportService;
import com.atsdoctor.backend.application.tailoring.TailoringService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Tailored-resume export (07-api-contract §8.2, FEAT-037, TASK-082):
 * GET /tailored/{id}/export/{format} with format ∈ {pdf, docx, latex, json}.
 * pdf/docx render ATS-friendly artifacts (Thymeleaf → Flying Saucer / POI);
 * latex renders a .tex source via the TEXT-mode {@code latex.tex} template;
 * json reuses the GET /tailored/{id} shape. All formats share the export gate
 * (409 until every change is resolved and validation passed — {@link
 * ExportService#assertExportable}), 404 unknown.
 */

@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "ats.doctor.persistence.enabled", havingValue = "true")
public class TailoringExportController {

    private final ExportService exportService;
    private final TailoringService tailoringService;

    public TailoringExportController(ExportService exportService, TailoringService tailoringService) {
        this.exportService = exportService;
        this.tailoringService = tailoringService;
    }

    @GetMapping("/tailored/{tailoredId}/export/{format}")
    public ResponseEntity<?> export(@PathVariable("tailoredId") UUID tailoredId,
                                    @PathVariable("format") String format) {
        return switch (format.toLowerCase()) {
            case "pdf" -> artifact(exportService.pdf(tailoredId));
            case "docx" -> artifact(exportService.docx(tailoredId));
            case "latex" -> artifact(exportService.latex(tailoredId));
            case "json" -> {
                exportService.assertExportable(tailoredId);
                yield ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(TailoredResponse.of(tailoringService.byId(tailoredId)).toMap());
            }
            default -> throw new com.atsdoctor.backend.application.tailoring.TailoringValidationException(
                    "Unknown export format '" + format + "' (pdf|docx|json|latex)");
        };
    }

    private static ResponseEntity<byte[]> artifact(ExportService.ExportArtifact artifact) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(artifact.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + artifact.filename() + "\"")
                .body(artifact.content());
    }
}