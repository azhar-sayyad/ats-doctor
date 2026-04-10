package com.atsdoctor.backend.api.tailor;

import com.atsdoctor.backend.infrastructure.export.ResumeTemplateCatalog;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resume template catalog (CL-020): GET /resume-templates lists the hardcoded
 * export templates (slug/name/description) for the export pickers. The style
 * definitions themselves live in the app ({@code resume-templates.yml}) and
 * are intentionally not exposed — the DB only stores the chosen slug.
 */
@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "ats.doctor.persistence.enabled", havingValue = "true")
public class ResumeTemplateController {

    private final ResumeTemplateCatalog catalog;

    public ResumeTemplateController(ResumeTemplateCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/resume-templates")
    public List<Map<String, Object>> list() {
        return catalog.list().stream().map(template -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("slug", template.slug());
            entry.put("name", template.name());
            entry.put("description", template.description());
            return entry;
        }).toList();
    }
}
