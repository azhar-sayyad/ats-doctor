package com.atsdoctor.backend;

import com.atsdoctor.backend.infrastructure.ai.AiHttpClientConfig;
import com.atsdoctor.backend.infrastructure.ai.SpringAiChatGateway;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Field;
import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;

/** DEC-024 transport guard — Spring AI must talk plain HTTP/1.1 to gateways. */
class AiHttpClientConfigTest {

    @Test
    void builder_uses_http_1_1_jdk_client_with_read_timeout() throws Exception {
        RestClient client = new AiHttpClientConfig().aiHttpClientBuilder().build();
        Object requestFactory = requestFactoryOf(client);

        assertThat(requestFactory).isInstanceOf(JdkClientHttpRequestFactory.class);
        Field field = JdkClientHttpRequestFactory.class.getDeclaredField("httpClient");
        field.setAccessible(true);
        HttpClient httpClient = (HttpClient) field.get(requestFactory);
        assertThat(httpClient.version()).isEqualTo(HttpClient.Version.HTTP_1_1);
    }

    @Test
    void gateway_strips_markdown_code_fences_and_leaves_clean_json() {
        assertThat(SpringAiChatGateway.stripCodeFences("```json\n{\"task\": \"resume_parser\"}\n```"))
                .isEqualTo("{\"task\": \"resume_parser\"}");
        assertThat(SpringAiChatGateway.stripCodeFences("```\n{\"a\": 1}\n```"))
                .isEqualTo("{\"a\": 1}");
        assertThat(SpringAiChatGateway.stripCodeFences("{\"a\": 1}"))
                .isEqualTo("{\"a\": 1}");
        assertThat(SpringAiChatGateway.stripCodeFences("  {\"a\": 1}  "))
                .isEqualTo("{\"a\": 1}");
    }

    @Test
    void gateway_extracts_json_from_prose_prefixed_provider_output() {
        assertThat(SpringAiChatGateway.sanitizeOutput("Here is the tailored bullet: {\"tailored_text\": \"x\"}"))
                .isEqualTo("{\"tailored_text\": \"x\"}");
        assertThat(SpringAiChatGateway.sanitizeOutput("Sure! ```json\n[{\"a\": 1}]\n```"))
                .isEqualTo("[{\"a\": 1}]");
        assertThat(SpringAiChatGateway.sanitizeOutput("{\"a\": 1}"))
                .isEqualTo("{\"a\": 1}");
        assertThat(SpringAiChatGateway.sanitizeOutput("Just plain prose, no JSON here"))
                .isEqualTo("Just plain prose, no JSON here");
        assertThat(SpringAiChatGateway.sanitizeOutput("First {\"a\": 1} then {\"b\": 2}"))
                .isEqualTo("{\"a\": 1}");
    }

    @Test
    void gateway_extraction_respects_strings_containing_braces_and_escapes() {
        String output = "{\"text\": \"braces {nested} and \\\"quotes\\\"\", \"b\": [1, 2]}";
        assertThat(SpringAiChatGateway.extractJson(output)).isEqualTo(output);
        assertThat(SpringAiChatGateway.extractJson("no json tokens at all")).isNull();
        assertThat(SpringAiChatGateway.extractJson("{\"unbalanced\": true")).isNull();
    }

    private static Object requestFactoryOf(RestClient client) throws Exception {
        Class<?> impl = Class.forName("org.springframework.web.client.DefaultRestClient");
        Field field = impl.getDeclaredField("clientRequestFactory");
        field.setAccessible(true);
        return field.get(client);
    }
}