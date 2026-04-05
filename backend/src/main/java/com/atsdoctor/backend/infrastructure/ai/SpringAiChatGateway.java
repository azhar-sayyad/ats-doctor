package com.atsdoctor.backend.infrastructure.ai;

import com.atsdoctor.backend.application.ai.AiRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Spring AI ChatClient gateway (DEC-024). Active when ats.doctor.ai.mode=
 * omniroute: base-url/model come only from SPRING_AI_OPENAI_* env config,
 * so provider switching is a deployment change, never a code change.
 */
@Component
@ConditionalOnProperty(name = "ats.doctor.ai.mode", havingValue = "omniroute")
public class SpringAiChatGateway implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(SpringAiChatGateway.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChatClient chatClient;
    private final String defaultModel;

    public SpringAiChatGateway(
            ChatClient.Builder builder,
            @Value("${spring.ai.openai.chat.options.model:unknown}") String defaultModel) {
        this.chatClient = builder.build();
        this.defaultModel = defaultModel;
    }

    @Override
    public String name() {
        return "omniroute";
    }

    @Override
    public ProviderResponse complete(ProviderRequest request) throws AiProviderException {
        AiRequest aiRequest = request.request();
        try {
            String model = request.model() == null ? defaultModel : request.model();
            var call = chatClient.prompt()
                    .user(aiRequest.input());
            if (request.model() != null) {
                call = call.options(OpenAiChatOptions.builder().model(model).build());
            }
            String content = call.call().content();
            return new ProviderResponse(sanitizeOutput(content), name(), model);
        } catch (RuntimeException ex) {
            log.warn("OmniRoute completion failed for task {}: {}", aiRequest.task().id(), ex.getMessage());
            throw new AiProviderException("OmniRoute completion failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Normalizes raw provider output at the boundary (DEC-024): strips markdown
     * code fences, then — when the response wraps JSON in prose (e.g. "Here is
     * the tailored bullet: {{...}}") — extracts the first balanced JSON value.
     * Clean JSON passes through untouched; pure prose passes through untouched
     * so downstream validation can fail with a meaningful error.
     */
    public static String sanitizeOutput(String output) {
        String s = stripCodeFences(output);
        if (s.isEmpty()) {
            return s;
        }
        String json = extractJson(s);
        if (json != null && !json.equals(s)) {
            try {
                MAPPER.readTree(json);
                return json;
            } catch (JsonProcessingException ignored) {
                // extracted candidate did not parse — keep the original
            }
        }
        return s;
    }

    /**
     * Scans for the first {@code {} or {@code [} and returns the text through the
     * matching closing brace/bracket, respecting string literals and escapes.
     * Returns {@code null} when no balanced JSON value is found.
     */
    public static String extractJson(String s) {
        int brace = s.indexOf('{');
        int bracket = s.indexOf('[');
        int start;
        if (brace < 0) {
            start = bracket;
        } else if (bracket < 0) {
            start = brace;
        } else {
            start = Math.min(brace, bracket);
        }
        if (start < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
                if (depth == 0) {
                    return s.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    /**
     * Real providers often wrap the promised raw JSON in a markdown code fence
     * (```json … ```) despite the prompt contract. Strip the fence at the
     * provider boundary so downstream parsing and the ai_runs jsonb column both
     * see clean JSON (parse layers keep their own fence-stripping as
     * defense-in-depth).
     */
    public static String stripCodeFences(String output) {
        String s = output == null ? "" : output.trim();
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            if (firstNewline >= 0) {
                s = s.substring(firstNewline + 1);
            } else {
                s = "";
            }
            if (s.endsWith("```")) {
                s = s.substring(0, s.length() - 3);
            }
        }
        return s.trim();
    }
}
