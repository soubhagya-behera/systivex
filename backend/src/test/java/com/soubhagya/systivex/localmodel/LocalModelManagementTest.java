package com.soubhagya.systivex.localmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.web.reactive.function.client.WebClient;

class LocalModelManagementTest {

    @Test
    void modelPullStrategyRemainsNever() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger tagsCalls = new AtomicInteger();
        AtomicInteger pullCalls = new AtomicInteger();
        server.createContext("/api/tags", exchange -> {
            tagsCalls.incrementAndGet();
            byte[] body = ("{\"models\":[{\"name\":\"qwen3:8b\",\"model\":\"qwen3:8b\","
                    + "\"modified_at\":\"2026-01-01T00:00:00Z\",\"size\":1,\"digest\":\"sha256:aaa\",\"details\":{}}]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.createContext("/api/pull", exchange -> {
            pullCalls.incrementAndGet();
            exchange.sendResponseHeaders(200, 0);
        });
        server.start();
        try {
            LocalModelTransport transport = new LocalModelTransport();
            OllamaApi api = OllamaApi.builder()
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                    .restClientBuilder(transport.restClientBuilder())
                    .webClientBuilder(WebClient.builder())
                    .build();
            new org.springframework.ai.ollama.management.OllamaModelManager(
                    api, ModelManagementOptions.builder()
                            .pullModelStrategy(PullModelStrategy.NEVER)
                            .maxRetries(0)
                            .build());
            assertEquals(0, pullCalls.get());
            assertEquals(0, tagsCalls.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void chatModelConstructionUsesNoRetryTemplate() {
        LocalModelTransport transport = new LocalModelTransport();
        OllamaApi api = OllamaApi.builder()
                .baseUrl("http://127.0.0.1:11434")
                .restClientBuilder(transport.restClientBuilder())
                .webClientBuilder(WebClient.builder())
                .build();
        var model = org.springframework.ai.ollama.OllamaChatModel.builder()
                .ollamaApi(api)
                .options(OllamaChatOptions.builder().model("qwen3:8b").build())
                .modelManagementOptions(ModelManagementOptions.builder()
                        .pullModelStrategy(PullModelStrategy.NEVER)
                        .maxRetries(0)
                        .build())
                .retryTemplate(new RetryTemplate(RetryPolicy.builder().maxRetries(0).build()))
                .build();
        org.junit.jupiter.api.Assertions.assertNotNull(model);
    }

    @Test
    void endpointValidatorRejectsUnsafeUrls() {
        assertThrows(IllegalStateException.class, () -> OllamaEndpointValidator.validate(null));
        assertThrows(IllegalStateException.class, () -> OllamaEndpointValidator.validate("https://127.0.0.1:11434"));
        assertThrows(IllegalStateException.class,
                () -> OllamaEndpointValidator.validate("http://user@127.0.0.1:11434"));
        assertThrows(IllegalStateException.class,
                () -> OllamaEndpointValidator.validate("http://localhost:11434"));
        assertThrows(IllegalStateException.class,
                () -> OllamaEndpointValidator.validate("http://ollama.example.com:11434"));
        assertThrows(IllegalStateException.class,
                () -> OllamaEndpointValidator.validate("http://192.168.1.5:11434"));
        assertThrows(IllegalStateException.class,
                () -> OllamaEndpointValidator.validate("http://host.docker.internal:11434"));
        assertThrows(IllegalStateException.class, () -> OllamaEndpointValidator.validate("http://127.0.0.1"));
    }

    @Test
    void endpointValidatorAcceptsLoopbackLiterals() {
        assertEquals("http://127.0.0.1:11434/", OllamaEndpointValidator.validate("http://127.0.0.1:11434/").toString());
        assertEquals("http://[::1]:11434", OllamaEndpointValidator.validate("http://[::1]:11434").toString());
    }
}
