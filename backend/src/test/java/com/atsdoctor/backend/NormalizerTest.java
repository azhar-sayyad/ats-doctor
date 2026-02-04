package com.atsdoctor.backend;

import com.atsdoctor.backend.infrastructure.matching.Normalizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** TASK-046 — normalization utilities. */
class NormalizerTest {

    @Test
    void lowercases_and_collapses_whitespace() {
        assertThat(Normalizer.normalize("  Python  and   FastAPI ")).isEqualTo("python and fastapi");
    }

    @Test
    void strips_punctuation() {
        assertThat(Normalizer.normalize("Strong knowledge of PostgreSQL, Redis!")).isEqualTo("strong knowledge of postgresql redis");
    }

    @Test
    void keeps_plus_and_dot_inside_tokens() {
        assertThat(Normalizer.normalize("5+ years, C# & node.js")).isEqualTo("5+ years c# node.js");
    }

    @Test
    void handles_nulls_and_blank() {
        assertThat(Normalizer.normalize(null)).isEmpty();
        assertThat(Normalizer.normalize("  ")).isEmpty();
    }

    @Test
    void tokens_are_deduplicated_and_ordered() {
        assertThat(Normalizer.tokens("Python Python, FastAPI")).containsExactly("python", "fastapi");
    }

    @Test
    void tokens_contained_requires_all_tokens() {
        assertThat(Normalizer.tokensContained("Python FastAPI", "Built FastAPI services in Python")).isTrue();
        assertThat(Normalizer.tokensContained("Python Redis", "Built FastAPI services in Python")).isFalse();
        assertThat(Normalizer.tokensContained("", "anything")).isFalse();
    }
}
