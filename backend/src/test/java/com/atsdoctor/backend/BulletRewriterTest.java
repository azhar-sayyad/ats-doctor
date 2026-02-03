package com.atsdoctor.backend;

import com.atsdoctor.backend.application.ai.AiRequest;
import com.atsdoctor.backend.application.ai.AiResult;
import com.atsdoctor.backend.application.ai.AiTask;
import com.atsdoctor.backend.application.ai.AIService;
import com.atsdoctor.backend.infrastructure.tailoring.BulletRewriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** TASK-061/063 — BulletRewriter: AI wiring, tolerant output parsing, summary variant. */
@ExtendWith(MockitoExtension.class)
class BulletRewriterTest {

    @Mock
    private AIService aiService;

    private BulletRewriter rewriter;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        rewriter = new BulletRewriter(aiService);
    }

    @Test
    void calls_resume_tailoring_with_prompt_variables() {
        when(aiService.generate(any())).thenReturn(result("Rewritten bullet text."));

        rewriter.rewriteBullet("Original bullet.", List.of("Kubernetes experience"), "evidence here");

        org.mockito.ArgumentCaptor<AiRequest> captor = org.mockito.ArgumentCaptor.forClass(AiRequest.class);
        verify(aiService).generate(captor.capture());
        AiRequest request = captor.getValue();
        assertThat(request.task()).isEqualTo(AiTask.RESUME_TAILORING);
        assertThat(request.variables())
                .containsKeys("jd_requirements", "resume_evidence", "original_bullet");
        assertThat(request.variables().get("original_bullet")).isEqualTo("Original bullet.");
    }

    @Test
    void parses_stub_json_output() {
        when(aiService.generate(any())).thenReturn(result(
                "{\"_stub\": true, \"tailored_text\": \"Built FastAPI services, with a focus on backend.\"}"));

        BulletRewriter.RewrittenText rewritten = rewriter.rewriteBullet(
                "Built FastAPI services.", List.of("5+ years backend"), "backend");

        assertThat(rewritten.text()).isEqualTo("Built FastAPI services, with a focus on backend.");
        assertThat(rewritten.promptVersion()).isEqualTo("tailor-bullet-v1");
    }

    @Test
    void uses_plain_text_output_and_strips_fences() {
        when(aiService.generate(any())).thenReturn(result("```\nRewritten bullet.\n```"));

        BulletRewriter.RewrittenText rewritten = rewriter.rewriteBullet("Old.", List.of("kw"), "e");

        assertThat(rewritten.text()).isEqualTo("Rewritten bullet.");
    }

    @Test
    void falls_back_to_original_on_blank_output() {
        when(aiService.generate(any())).thenReturn(result("\n   \n"));

        BulletRewriter.RewrittenText rewritten = rewriter.rewriteBullet("Original stays.", List.of("kw"), "e");

        assertThat(rewritten.text()).isEqualTo("Original stays.");
    }

    @Test
    void summary_variant_rewrites_the_summary() {
        when(aiService.generate(any())).thenReturn(result("Senior Engineer with backend focus."));

        BulletRewriter.RewrittenText rewritten = rewriter.rewriteSummary(
                "Senior Engineer.", List.of("backend experience"), "backend");

        assertThat(rewritten.text()).isEqualTo("Senior Engineer with backend focus.");
        org.mockito.ArgumentCaptor<AiRequest> captor = org.mockito.ArgumentCaptor.forClass(AiRequest.class);
        verify(aiService).generate(captor.capture());
        assertThat(captor.getValue().variables().get("original_bullet")).isEqualTo("Senior Engineer.");
    }

    @Test
    void clips_oversized_context() {
        when(aiService.generate(any())).thenReturn(result("fine"));
        String longRequirements = "req ".repeat(2000);

        rewriter.rewriteBullet("Bullet.", List.of(longRequirements), "e");

        org.mockito.ArgumentCaptor<AiRequest> captor = org.mockito.ArgumentCaptor.forClass(AiRequest.class);
        verify(aiService).generate(captor.capture());
        String jd = (String) captor.getValue().variables().get("jd_requirements");
        assertThat(jd.length()).isLessThanOrEqualTo(BulletRewriter.MAX_JD_CONTEXT_CHARS + 1);
    }

    private static AiResult result(String output) {
        return new AiResult(AiTask.RESUME_TAILORING, output, "stub", "stub", 5, Map.of());
    }
}