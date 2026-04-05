package com.atsdoctor.backend.infrastructure.ai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Constrains Spring AI's wire transport to plain HTTP/1.1 (DEC-024).
 *
 * The JDK HttpClient behind Spring AI's RestClient negotiates HTTP/2 by
 * default and upgrades every request with {@code Upgrade: h2c}. HTTP/1.1-only
 * gateway proxies (OmniRoute and local dev gateways) reset such connections
 * immediately, surfacing as "HTTP/1.1 header parser received no bytes" in the
 * AI facade. Version-locking the client removes the upgrade header; provider
 * switching, profiles and routing are untouched — this only swaps the transport.
 */
@Configuration(proxyBeanMethods = false)
public class AiHttpClientConfig {

    @Bean
    public RestClient.Builder aiHttpClientBuilder() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(Duration.ofSeconds(10))
                        .build());
        // Slack lives with the facade's own retry/timeout budget
        // (ats.doctor.ai.timeout-seconds); only cap a single read here.
        factory.setReadTimeout(Duration.ofSeconds(120));
        return RestClient.builder().requestFactory(factory);
    }
}