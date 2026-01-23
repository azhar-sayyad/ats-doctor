package com.atsdoctor.backend.infrastructure.ai;

import com.atsdoctor.backend.application.ai.AiRequest;

/**
 * Internal provider seam. Implementations: StubAiProvider (dev) and
 * SpringAiChatGateway (omniroute mode). App code never touches these directly.
 */
public interface AiProvider {

    String name();

    ProviderResponse complete(ProviderRequest request) throws AiProviderException;
}
