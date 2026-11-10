package com.soubhagya.systivex.localmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOError;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;

class LocalModelTransportSecurityTest {

    private static final String EXPECTED_DIGEST =
            "sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final String OTHER_DIGEST =
            "sha256:0000000000000000000000000000000000000000000000000000000000000000";

    private HttpServer server;
    private final AtomicInteger requestCount = new AtomicInteger();
    private final AtomicInteger redirectTargetCount = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
            requestCount.incrementAndGet();
            byte[] body = validChatBody();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.createContext("/api/tags", exchange -> {
            requestCount.incrementAndGet();
            byte[] body = ("{\"models\":[{\"name\":\"qwen3:8b\",\"model\":\"qwen3:8b\",\"modified_at\":\"2026-01-01T00:00:00Z\","
                    + "\"size\":123,\"digest\":\"" + EXPECTED_DIGEST + "\",\"details\":{}}]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.createContext("/redirect302", exchange -> {
            redirect(exchange, 302);
        });
        server.createContext("/redirect307", exchange -> {
            redirect(exchange, 307);
        });
        server.createContext("/target", exchange -> {
            redirectTargetCount.incrementAndGet();
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    private void redirect(com.sun.net.httpserver.HttpExchange exchange, int status) throws IOException {
        exchange.getResponseHeaders().set("Location",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/target");
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static byte[] validChatBody() {
        return ("{\"model\":\"qwen3:8b\",\"created_at\":\"2026-01-01T00:00:00Z\","
                + "\"message\":{\"role\":\"assistant\",\"content\":\"hello\"},\"done\":true,\"done_reason\":\"stop\","
                + "\"total_duration\":1,\"load_duration\":1,\"prompt_eval_count\":1,\"prompt_eval_duration\":1,"
                + "\"eval_count\":1,\"eval_duration\":1}")
                .getBytes(StandardCharsets.UTF_8);
    }

    private LocalModelProperties properties() {
        LocalModelProperties properties = new LocalModelProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setModel("qwen3:8b");
        properties.setExpectedDigest(EXPECTED_DIGEST);
        return properties;
    }

    @Test
    void selectorReturnsNoProxy() {
        List<Proxy> selected = LocalModelTransport.directProxySelector()
                .select(URI.create("http://127.0.0.1:11434/api/chat"));
        assertEquals(List.of(Proxy.NO_PROXY), selected);
        assertSame(LocalModelTransport.directProxySelector(), LocalModelTransport.directProxySelector());
    }

    @Test
    void transportChainInstancesExist() {
        LocalModelTransport transport = new LocalModelTransport();
        assertNotNull(transport.httpClient());
        assertNotNull(transport.jdkFactory());
        assertNotNull(transport.boundedFactory());
        assertNotNull(transport.restClientBuilder());
    }

    @Test
    void redirect302NotFollowed() throws Exception {
        assertRedirectNotFollowed("/redirect302", 302);
    }

    @Test
    void redirect307NotFollowed() throws Exception {
        assertRedirectNotFollowed("/redirect307", 307);
    }

    private void assertRedirectNotFollowed(String path, int expectedStatus) throws Exception {
        LocalModelTransport transport = new LocalModelTransport();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + path;
        ClientHttpRequest request = transport.boundedFactory().createRequest(URI.create(url), HttpMethod.GET);
        ClientHttpResponse response = request.execute();
        assertEquals(expectedStatus, response.getStatusCode().value());
        response.close();
        assertEquals(0, redirectTargetCount.get());
    }

    @Test
    void jvmProxyPropertiesDoNotReplaceDirectSelector() {
        System.setProperty("http.proxyHost", "127.0.0.1");
        System.setProperty("http.proxyPort", "8080");
        try {
            LocalModelAdapter adapter = LocalModelAdapter.create(properties());
            assertEquals("hello", adapter.infer("ping"));
        } finally {
            System.clearProperty("http.proxyHost");
            System.clearProperty("http.proxyPort");
        }
    }

    @Test
    void inferenceUsesConfiguredTransportAndReturnsText() {
        LocalModelAdapter adapter = LocalModelAdapter.create(properties());
        assertEquals("hello", adapter.infer("ping"));
        assertTrue(requestCount.get() > 0);
    }

    @Test
    void failingProviderMakesExactlyOneRequest() throws Exception {
        HttpServer failing = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger count = new AtomicInteger();
        failing.createContext("/api/tags", exchange -> {
            byte[] tagsBody = ("{\"models\":[{\"name\":\"qwen3:8b\",\"model\":\"qwen3:8b\","
                    + "\"modified_at\":\"2026-01-01T00:00:00Z\",\"size\":123,"
                    + "\"digest\":\"" + EXPECTED_DIGEST + "\",\"details\":{}}]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, tagsBody.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(tagsBody);
            }
        });
        failing.createContext("/api/chat", exchange -> {
            count.incrementAndGet();
            byte[] body = "{\"error\":\"boom\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        failing.start();
        try {
            LocalModelProperties properties = properties();
            properties.setBaseUrl("http://127.0.0.1:" + failing.getAddress().getPort());
            LocalModelAdapter adapter = LocalModelAdapter.create(properties);
            assertThrows(LocalModelAdapter.AnalysisProviderUnavailableException.class, () -> adapter.infer("ping"));
            assertEquals(1, count.get());
        } finally {
            failing.stop(0);
        }
    }

    @Test
    void concurrentPermitRejectsSecondInference() throws Exception {
        HttpServer blocking = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        CountDownLatch arrived = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger tagsCount = new AtomicInteger();
        blocking.createContext("/api/tags", exchange -> {
            tagsCount.incrementAndGet();
            byte[] tagsBody = ("{\"models\":[{\"name\":\"qwen3:8b\",\"model\":\"qwen3:8b\","
                    + "\"modified_at\":\"2026-01-01T00:00:00Z\",\"size\":123,"
                    + "\"digest\":\"" + EXPECTED_DIGEST + "\",\"details\":{}}]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, tagsBody.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(tagsBody);
            }
        });
        blocking.createContext("/api/chat", exchange -> {
            arrived.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            byte[] body = validChatBody();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        blocking.start();
        try {
            LocalModelProperties properties = properties();
            properties.setBaseUrl("http://127.0.0.1:" + blocking.getAddress().getPort());
            LocalModelAdapter adapter = LocalModelAdapter.create(properties);
            AtomicReference<Throwable> firstFailure = new AtomicReference<>();
            Thread first = new Thread(() -> {
                try {
                    adapter.infer("first");
                } catch (Throwable throwable) {
                    firstFailure.set(throwable);
                }
            });
            first.start();
            assertTrue(arrived.await(5, TimeUnit.SECONDS));
            assertThrows(LocalModelAdapter.AnalysisBusyException.class, () -> adapter.infer("second"));
            assertEquals(2, tagsCount.get());
            release.countDown();
            first.join(5000);
            assertFalse(first.isAlive());
            if (firstFailure.get() != null) {
                throw new IOError(firstFailure.get());
            }
            assertEquals("hello", adapter.infer("third"));
        } finally {
            release.countDown();
            blocking.stop(0);
        }
    }

    @Test
    void installedModelDigestVerified() {
        LocalModelAdapter adapter = LocalModelAdapter.create(properties());
        adapter.requireInstalledModelDigest("qwen3:8b", EXPECTED_DIGEST);
        assertThrows(LocalModelAdapter.ModelIdentityMismatchException.class,
                () -> adapter.requireInstalledModelDigest("qwen3:8b", OTHER_DIGEST));
    }
}
