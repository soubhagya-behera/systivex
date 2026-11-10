package com.soubhagya.systivex.localmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocalModelDigestVerificationTest {

    private static final String EXPECTED_DIGEST =
            "sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final String OTHER_DIGEST =
            "sha256:0000000000000000000000000000000000000000000000000000000000000000";

    private HttpServer server;
    private final AtomicInteger tagsCalls = new AtomicInteger();
    private final AtomicInteger chatCalls = new AtomicInteger();
    private final List<String> events = new CopyOnWriteArrayList<>();
    private String tagsStatus = "200";
    private String tagsBody = tagsBody("qwen3:8b", EXPECTED_DIGEST);

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/tags", exchange -> {
            tagsCalls.incrementAndGet();
            events.add("tags");
            byte[] body = tagsBody.getBytes(StandardCharsets.UTF_8);
            int status = Integer.parseInt(tagsStatus);
            if (status == 200) {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
            }
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.createContext("/api/chat", exchange -> {
            chatCalls.incrementAndGet();
            events.add("chat");
            byte[] body = validChatBody();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private LocalModelProperties properties() {
        LocalModelProperties properties = new LocalModelProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setModel("qwen3:8b");
        properties.setExpectedDigest(EXPECTED_DIGEST);
        return properties;
    }

    private static String tagsBody(String name, String digest) {
        return "{\"models\":[{\"name\":\"" + name + "\",\"model\":\"" + name + "\","
                + "\"modified_at\":\"2026-01-01T00:00:00Z\",\"size\":123,"
                + "\"digest\":\"" + digest + "\",\"details\":{}}]}";
    }

    private static byte[] validChatBody() {
        return ("{\"model\":\"qwen3:8b\",\"created_at\":\"2026-01-01T00:00:00Z\","
                + "\"message\":{\"role\":\"assistant\",\"content\":\"hello\"},\"done\":true,"
                + "\"done_reason\":\"stop\",\"total_duration\":1,\"load_duration\":1,"
                + "\"prompt_eval_count\":1,\"prompt_eval_duration\":1,\"eval_count\":1,"
                + "\"eval_duration\":1}")
                .getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void matchingDigestVerificationSucceedsAndInferenceProceedsExactlyOnce() {
        LocalModelAdapter adapter = LocalModelAdapter.create(properties());
        assertEquals("hello", adapter.infer("ping"));
        assertEquals(1, tagsCalls.get());
        assertEquals(1, chatCalls.get());
        assertEquals(List.of("tags", "chat"), events);
    }

    @Test
    void verificationIsNotCachedAcrossInferenceCalls() {
        LocalModelAdapter adapter = LocalModelAdapter.create(properties());
        assertEquals("hello", adapter.infer("ping"));
        assertEquals("hello", adapter.infer("ping"));
        assertEquals(2, tagsCalls.get());
        assertEquals(2, chatCalls.get());
        assertEquals(List.of("tags", "chat", "tags", "chat"), events);
    }

    @Test
    void mismatchedDigestFailsClosedAndChatNotCalled() {
        tagsBody = tagsBody("qwen3:8b", OTHER_DIGEST);
        LocalModelAdapter adapter = LocalModelAdapter.create(properties());
        assertThrows(LocalModelAdapter.ModelIdentityMismatchException.class, () -> adapter.infer("ping"));
        assertEquals(1, tagsCalls.get());
        assertEquals(0, chatCalls.get());
        assertEquals(List.of("tags"), events);
    }

    @Test
    void missingModelFailsClosedAndChatNotCalled() {
        tagsBody = tagsBody("other:8b", EXPECTED_DIGEST);
        LocalModelAdapter adapter = LocalModelAdapter.create(properties());
        assertThrows(LocalModelAdapter.ModelIdentityMismatchException.class, () -> adapter.infer("ping"));
        assertEquals(0, chatCalls.get());
    }

    @Test
    void missingExpectedDigestFailsClosed() {
        LocalModelProperties properties = properties();
        properties.setExpectedDigest(null);
        assertThrows(IllegalArgumentException.class, () -> LocalModelAdapter.create(properties));
        assertEquals(0, tagsCalls.get());
        assertEquals(0, chatCalls.get());
    }

    @Test
    void blankExpectedDigestFailsClosed() {
        LocalModelProperties properties = properties();
        properties.setExpectedDigest("   ");
        assertThrows(IllegalArgumentException.class, () -> LocalModelAdapter.create(properties));
        assertEquals(0, tagsCalls.get());
        assertEquals(0, chatCalls.get());
    }

    @Test
    void malformedExpectedDigestFailsClosed() {
        LocalModelProperties properties = properties();
        properties.setExpectedDigest("sha256:not-hex");
        assertThrows(IllegalArgumentException.class, () -> LocalModelAdapter.create(properties));
        assertEquals(0, tagsCalls.get());
        assertEquals(0, chatCalls.get());
    }

    @Test
    void missingProviderDigestFailsClosed() {
        tagsBody = "{\"models\":[{\"name\":\"qwen3:8b\",\"model\":\"qwen3:8b\","
                + "\"modified_at\":\"2026-01-01T00:00:00Z\",\"size\":123,\"details\":{}}]}";
        LocalModelAdapter adapter = LocalModelAdapter.create(properties());
        assertThrows(LocalModelAdapter.ModelIdentityMismatchException.class, () -> adapter.infer("ping"));
        assertEquals(0, chatCalls.get());
    }

    @Test
    void providerUnavailableDuringVerificationFailsClosed() {
        tagsStatus = "500";
        tagsBody = "{\"error\":\"boom\"}";
        LocalModelAdapter adapter = LocalModelAdapter.create(properties());
        assertThrows(LocalModelAdapter.AnalysisProviderUnavailableException.class,
                () -> adapter.infer("ping"));
        assertEquals(0, chatCalls.get());
    }

    @Test
    void malformedModelListResponseFailsClosed() {
        tagsBody = "not json";
        LocalModelAdapter adapter = LocalModelAdapter.create(properties());
        assertThrows(LocalModelAdapter.AnalysisProviderUnavailableException.class,
                () -> adapter.infer("ping"));
        assertEquals(0, chatCalls.get());
    }

    @Test
    void untaggedConfiguredModelMatchesLatestTag() {
        LocalModelProperties properties = properties();
        properties.setModel("qwen3");
        tagsBody = tagsBody("qwen3:latest", EXPECTED_DIGEST);
        LocalModelAdapter adapter = LocalModelAdapter.create(properties);
        assertEquals("hello", adapter.infer("ping"));
        assertEquals(1, chatCalls.get());
    }

    @Test
    void directInferCannotBypassVerification() {
        LocalModelAdapter adapter = LocalModelAdapter.create(properties());
        assertEquals("hello", adapter.infer("ping"));
        assertTrue(events.indexOf("tags") < events.indexOf("chat"));
    }
}
