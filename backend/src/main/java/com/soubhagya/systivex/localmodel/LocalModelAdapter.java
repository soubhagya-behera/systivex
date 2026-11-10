package com.soubhagya.systivex.localmodel;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Local model adapter for D41 Slice 2. Installed-model digest verification confirms the installed
 * model only; it does not cryptographically prove per-request model identity (D41 G-4).
 */
public final class LocalModelAdapter {

    private static final Semaphore INFERENCE_PERMIT = new Semaphore(1, true);

    public static final Duration OVERALL_OPERATION_DEADLINE = Duration.ofSeconds(35);

    private final OllamaApi ollamaApi;

    private final OllamaChatModel chatModel;

    private final LocalModelTransport transport;

    private final MonotonicClock clock;

    private final String modelName;

    private final String expectedDigest;

    private final Duration overallOperationDeadline;

    public LocalModelAdapter(OllamaApi ollamaApi, OllamaChatModel chatModel, LocalModelTransport transport,
                             String modelName, String expectedDigest) {
        this(ollamaApi, chatModel, transport, modelName, expectedDigest, OVERALL_OPERATION_DEADLINE);
    }

    LocalModelAdapter(OllamaApi ollamaApi, OllamaChatModel chatModel, LocalModelTransport transport,
                      String modelName, String expectedDigest, Duration overallOperationDeadline) {
        this.ollamaApi = Objects.requireNonNull(ollamaApi, "ollamaApi");
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.clock = Objects.requireNonNull(transport.getClock(), "clock");
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException("modelName must not be blank");
        }
        this.modelName = modelName.strip();
        ModelDigest.validate(expectedDigest);
        this.expectedDigest = expectedDigest.strip();
        this.overallOperationDeadline =
                Objects.requireNonNull(overallOperationDeadline, "overallOperationDeadline");
    }

    public String infer(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
        Deadline deadline = Deadline.after(overallOperationDeadline, clock);
        transport.setDeadline(deadline);
        try {
            requireInstalledModelDigest();
            deadline.throwIfExpired();
            Prompt request = new Prompt(new UserMessage(prompt));
            boolean acquired;
            try {
                acquired = INFERENCE_PERMIT.tryAcquire(0, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AnalysisProviderUnavailableException(
                        "Local model inference was interrupted before the permit could be acquired", ex);
            }
            if (!acquired) {
                throw new AnalysisBusyException("Another inference in progress");
            }
            try {
                ChatResponse response = chatModel.call(request);
                AssistantMessage message = response.getResult().getOutput();
                return message == null || message.getText() == null ? "" : message.getText();
            } catch (RuntimeException ex) {
                if (causeIs(ex, LocalModelTransport.ResponseTooLargeException.class)) {
                    throw new AnalysisResponseTooLargeException("Ollama response body exceeds 65536 bytes", ex);
                }
                if (causeIs(ex, OperationDeadlineExceededException.class)) {
                    throw new AnalysisDeadlineExceededException(
                            "Local model operation exceeded the 35-second overall deadline", ex);
                }
                throw new AnalysisProviderUnavailableException("Local model inference failed", ex);
            } finally {
                INFERENCE_PERMIT.release();
            }
        } catch (OperationDeadlineExceededException ex) {
            throw new AnalysisDeadlineExceededException(
                    "Local model operation exceeded the 35-second overall deadline", ex);
        } finally {
            transport.clearDeadline();
        }
    }

    /**
     * Verifies the installed model digest against the expected digest. This confirms the installed
     * model only; it does not cryptographically prove per-request model identity (D41 G-4).
     */
    public boolean verifyInstalledModelDigest(String modelName, String expectedDigest) {
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException("modelName must not be blank");
        }
        ModelDigest.validate(expectedDigest);
        try {
            verifyInstalledModelDigestInternal(modelName, expectedDigest);
            return true;
        } catch (ModelIdentityMismatchException ex) {
            return false;
        }
    }

    public void requireInstalledModelDigest(String modelName, String expectedDigest) {
        if (!verifyInstalledModelDigest(modelName, expectedDigest)) {
            throw new ModelIdentityMismatchException(
                    "Installed local model digest does not match the expected digest");
        }
    }

    private void requireInstalledModelDigest() {
        verifyInstalledModelDigestInternal(modelName, expectedDigest);
    }

    private void verifyInstalledModelDigestInternal(String modelName, String expectedDigest) {
        OllamaApi.ListModelResponse listResponse;
        try {
            listResponse = ollamaApi.listModels();
        } catch (OperationDeadlineExceededException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            if (causeIs(ex, OperationDeadlineExceededException.class)) {
                throw new OperationDeadlineExceededException(
                        "Local model availability check exceeded the overall deadline", ex);
            }
            throw new AnalysisProviderUnavailableException("Local model availability check failed", ex);
        }
        if (listResponse == null || listResponse.models() == null) {
            throw new ModelIdentityMismatchException("Local model list is missing or malformed");
        }
        String normalized = normalizeModelName(modelName);
        OllamaApi.Model model = listResponse.models().stream()
                .filter(candidate -> candidate != null && normalized.equals(candidate.name()))
                .findFirst()
                .orElseThrow(() -> new ModelIdentityMismatchException(
                        "Installed local model not found: " + normalized));
        if (model.digest() == null || model.digest().isBlank()) {
            throw new ModelIdentityMismatchException(
                    "Installed local model digest is missing for model: " + normalized);
        }
        if (!expectedDigest.equals(model.digest())) {
            throw new ModelIdentityMismatchException(
                    "Installed local model digest does not match the expected digest");
        }
    }

    private static String normalizeModelName(String modelName) {
        String trimmed = modelName.strip();
        return trimmed.contains(":") ? trimmed : trimmed + ":latest";
    }

    private static boolean causeIs(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public static final class AnalysisBusyException extends RuntimeException {
        public AnalysisBusyException(String message) {
            super(message);
        }
    }

    public static final class AnalysisProviderUnavailableException extends RuntimeException {
        public AnalysisProviderUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final class AnalysisResponseTooLargeException extends RuntimeException {
        public AnalysisResponseTooLargeException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final class AnalysisDeadlineExceededException extends RuntimeException {
        public AnalysisDeadlineExceededException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final class ModelIdentityMismatchException extends RuntimeException {
        public ModelIdentityMismatchException(String message) {
            super(message);
        }
    }

    public static LocalModelAdapter create(LocalModelProperties properties) {
        return create(properties, OVERALL_OPERATION_DEADLINE, MonotonicClock.system());
    }

    static LocalModelAdapter create(LocalModelProperties properties, MonotonicClock clock) {
        return create(properties, OVERALL_OPERATION_DEADLINE, clock);
    }

    static LocalModelAdapter create(LocalModelProperties properties, Duration overallOperationDeadline,
                                    MonotonicClock clock) {
        Objects.requireNonNull(properties, "properties");
        LocalModelTransport transport = new LocalModelTransport(clock);
        OllamaApi api = OllamaApi.builder()
                .baseUrl(properties.getBaseUrl())
                .restClientBuilder(transport.restClientBuilder())
                .webClientBuilder(WebClient.builder()
                        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(65_536)))
                .build();
        OllamaChatModel model = OllamaChatModel.builder()
                .ollamaApi(api)
                .options(OllamaChatOptions.builder().model(properties.getModel()).build())
                .toolCallingManager(ToolCallingManager.builder().build())
                .modelManagementOptions(ModelManagementOptions.builder()
                        .pullModelStrategy(PullModelStrategy.NEVER)
                        .maxRetries(0)
                        .build())
                .retryTemplate(new RetryTemplate(RetryPolicy.builder().maxRetries(0).build()))
                .build();
        return new LocalModelAdapter(api, model, transport, properties.getModel(), properties.getExpectedDigest(),
                overallOperationDeadline);
    }
}
