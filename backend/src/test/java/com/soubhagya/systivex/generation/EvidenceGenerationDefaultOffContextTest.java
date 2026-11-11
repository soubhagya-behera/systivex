package com.soubhagya.systivex.generation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soubhagya.systivex.localmodel.LocalModelAdapter;
import com.soubhagya.systivex.twin.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest
class EvidenceGenerationDefaultOffContextTest extends AbstractPostgresIntegrationTest {

    private final ApplicationContext applicationContext;

    EvidenceGenerationDefaultOffContextTest(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Test
    void providerDisabledHasNoGenerationBeans() {
        assertThatThrownBy(() -> applicationContext.getBean(LocalModelAdapter.class))
                .isInstanceOf(NoSuchBeanDefinitionException.class);
        assertThatThrownBy(() -> applicationContext.getBean(EvidenceGenerationService.class))
                .isInstanceOf(NoSuchBeanDefinitionException.class);
    }
}
