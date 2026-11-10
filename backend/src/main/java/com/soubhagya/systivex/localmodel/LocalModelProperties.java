package com.soubhagya.systivex.localmodel;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("systivex.local")
public class LocalModelProperties {

    private boolean enabled = false;

    private String baseUrl = "http://127.0.0.1:11434";

    private String model;

    private String expectedDigest;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = trim(baseUrl);
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = trim(model);
    }

    public String getExpectedDigest() {
        return expectedDigest;
    }

    public void setExpectedDigest(String expectedDigest) {
        this.expectedDigest = trim(expectedDigest);
    }

    @PostConstruct
    void validate() {
        if (!enabled) {
            return;
        }
        OllamaEndpointValidator.validate(baseUrl);
        if (isBlank(model)) {
            throw new IllegalStateException("systivex.local.model is required when the local provider is enabled");
        }
        if (isBlank(expectedDigest)) {
            throw new IllegalStateException(
                    "systivex.local.expected-digest is required when the local provider is enabled");
        }
        ModelDigest.validate(expectedDigest);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
