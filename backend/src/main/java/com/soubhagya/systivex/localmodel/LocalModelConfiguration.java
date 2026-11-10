package com.soubhagya.systivex.localmodel;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LocalModelProperties.class)
@ConditionalOnProperty(name = "systivex.local.enabled", havingValue = "true", matchIfMissing = false)
public class LocalModelConfiguration {

    @Bean
    public LocalModelAdapter localModelAdapter(LocalModelProperties properties) {
        return LocalModelAdapter.create(properties);
    }
}
