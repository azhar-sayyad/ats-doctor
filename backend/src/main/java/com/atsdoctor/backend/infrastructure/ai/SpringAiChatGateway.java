package com.atsdoctor.backend.infrastructure.ai;

import com.atsdoctor.backend.application.ai.AiRequest;
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
            return new ProviderResponse(content, name(), model);
        } catch (RuntimeException ex) {
            log.warn("OmniRoute completion failed for task {}: {}", aiRequest.task().id(), ex.getMessage());
            throw new AiProviderException("OmniRoute completion failed: " + ex.getMessage(), ex);
        }
    }
}
