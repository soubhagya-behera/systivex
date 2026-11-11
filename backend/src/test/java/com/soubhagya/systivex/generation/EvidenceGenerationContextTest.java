package com.soubhagya.systivex.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soubhagya.systivex.localmodel.LocalModelAdapter;
import com.soubhagya.systivex.localmodel.LocalModelConfiguration;
import com.soubhagya.systivex.localmodel.LocalModelProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;import tools.jackson.databind.ObjectMapper;

class EvidenceGenerationContextTest {

    @Configuration
    @Import(LocalModelConfiguration.class)
    static class TestConfiguration {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(LocalModelAdapter.class)
        EvidenceGenerationService evidenceGenerationService(LocalModelAdapter adapter, ObjectMapper objectMapper) {
            return new EvidenceGenerationService(adapter, objectMapper);
        }
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TestConfiguration.class));
    }

    @Test
    void providerDisabledHasNoGenerationService() {
        runner()
                .withPropertyValues("systivex.local.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(LocalModelAdapter.class);
                    assertThat(context).doesNotHaveBean(EvidenceGenerationService.class);
                });
    }

    @Test
    void validProviderEnablesGenerationService() {
        runner()
                .withPropertyValues(
                        "systivex.local.enabled=true",
                        "systivex.local.base-url=http://127.0.0.1:11434",
                        "systivex.local.model=test-model",
                        "systivex.local.expected-digest=sha256:" + "0".repeat(64))
                .run(context -> {
                    assertThat(context).hasSingleBean(LocalModelAdapter.class);
                    assertThat(context).hasSingleBean(EvidenceGenerationService.class);
                });
    }

    @Test
    void invalidProviderConfigurationFailsClosed() {
        runner()
                .withPropertyValues("systivex.local.enabled=true")
                .run(context -> assertThat(context.getStartupFailure()).isNotNull());
    }
}
