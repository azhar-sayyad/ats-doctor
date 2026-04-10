package com.atsdoctor.backend;

import com.atsdoctor.backend.api.tailor.ResumeTemplateController;
import com.atsdoctor.backend.infrastructure.export.ResumeTemplateCatalog;
import com.atsdoctor.backend.infrastructure.export.ResumeTemplateCatalog.HtmlStyle;
import com.atsdoctor.backend.infrastructure.export.ResumeTemplateCatalog.DocxStyle;
import com.atsdoctor.backend.infrastructure.export.ResumeTemplateCatalog.LatexStyle;
import com.atsdoctor.backend.infrastructure.export.ResumeTemplateCatalog.ResumeTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CL-020 — GET /resume-templates lists slug/name/description for the export pickers. */
@WebMvcTest(value = ResumeTemplateController.class, properties = "ats.doctor.persistence.enabled=true")
class ResumeTemplateControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ResumeTemplateCatalog catalog;

    @Test
    void lists_the_catalog_metadata() throws Exception {
        when(catalog.list()).thenReturn(List.of(template("ats_clean", "ATS Clean Standard", "Single column, uppercase headings."),
                template("modern_minimal", "Modern Minimal", "Clean sans-serif.")));

        mvc.perform(get("/api/v1/resume-templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].slug").value("ats_clean"))
                .andExpect(jsonPath("$[0].name").value("ATS Clean Standard"))
                .andExpect(jsonPath("$[0].description").value("Single column, uppercase headings."))
                .andExpect(jsonPath("$[1].slug").value("modern_minimal"));
    }

    private static ResumeTemplate template(String slug, String name, String description) {
        HtmlStyle html = new HtmlStyle("Helvetica", "Helvetica", 10, 18, 12, 10.5f,
                1.4f, 0.5f, "#1e293b", "#0f172a", "#0f172a", "#cbd5e1",
                "#475569", "#94a3b8", "#f1f5f9", "#e2e8f0", true, true);
        DocxStyle docx = new DocxStyle("Helvetica", "Helvetica", 11, 16, 12, 11, "000000", false);
        LatexStyle latex = new LatexStyle(10, 0.7f);
        return new ResumeTemplate(slug, name, description, html, docx, latex);
    }
}
