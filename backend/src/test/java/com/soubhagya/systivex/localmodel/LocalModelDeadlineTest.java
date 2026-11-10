package com.soubhagya.systivex.localmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;

class LocalModelDeadlineTest {

    private static final String EXPECTED_DIGEST =
            "sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private FakeMonotonicClock clock;
    private HttpServer server;
    private java.util.concurrent.ExecutorService serverExecutor;
    private Duration tagsAdvance = Duration.ZERO;
    private Duration chatAdvance = Duration.ZERO;
    private final AtomicInteger tagsCalls = new AtomicInteger();
    private final AtomicInteger chatCalls = new AtomicInteger();
    private final CountDownLatch chatArrived = new CountDownLatch(1);
    private final CountDownLatch chatGate = new CountDownLatch(1);
    private volatile boolean chatStalls = false;
    private volatile boolean chatStallsBeforeBody = false;
    private volatile boolean chatWritePartialThenStall = false;

    @BeforeEach
    void startServer() throws IOException {
        clock = new FakeMonotonicClock();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.createContext("/api/tags", exchange -> {
            tagsCalls.incrementAndGet();
            clock.advance(tagsAdvance);
            byte[] body = ("{\"models\":[{\"name\":\"qwen3:8b\",\"model\":\"qwen3:8b\","
                    + "\"modified_at\":\"2026-01-01T00:00:00Z\",\"size\":123,"
                    + "\"digest\":\"" + EXPECTED_DIGEST + "\",\"details\":{}}]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.createContext("/api/chat", exchange -> {
            chatCalls.incrementAndGet();
            chatArrived.countDown();
            clock.advance(chatAdvance);
            byte[] body = ("{\"model\":\"qwen3:8b\",\"created_at\":\"2026-01-01T00:00:00Z\","
                    + "\"message\":{\"role\":\"assistant\",\"content\":\"hello\"},\"done\":true,"
                    + "\"done_reason\":\"stop\",\"total_duration\":1,\"load_duration\":1,"
                    + "\"prompt_eval_count\":1,\"prompt_eval_duration\":1,\"eval_count\":1,"
                    + "\"eval_duration\":1}")
                    .getBytes(StandardCharsets.UTF_8);
            if (chatStallsBeforeBody) {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                awaitChatGate();
                OutputStream out = exchange.getResponseBody();
                out.write(body);
                out.close();
                return;
            }
            if (chatWritePartialThenStall) {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                OutputStream out = exchange.getResponseBody();
                out.write(body, 0, 10);
                out.flush();
                awaitChatGate();
                out.write(body, 10, body.length - 10);
                out.close();
                return;
            }
            if (chatStalls) {
                awaitChatGate();
            }
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
            server = null;
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
            try {
                serverExecutor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            serverExecutor = null;
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

    @Test
    void timeoutConstantsArePreserved() {
        assertEquals(Duration.ofSeconds(5), LocalModelTransport.CONNECT_TIMEOUT);
        assertEquals(Duration.ofSeconds(30), LocalModelTransport.READ_TIMEOUT);
        assertEquals(Duration.ofSeconds(35), LocalModelAdapter.OVERALL_OPERATION_DEADLINE);
        assertEquals(LocalModelTransport.CONNECT_TIMEOUT.plus(LocalModelTransport.READ_TIMEOUT),
                LocalModelAdapter.OVERALL_OPERATION_DEADLINE);
    }

    @Test
    void deadlineArithmeticIsMonotonic() {
        Deadline deadline = Deadline.after(Duration.ofSeconds(35), clock);
        assertFalse(deadline.isExpired());
        assertEquals(Duration.ofSeconds(35), deadline.remaining());
        clock.advance(Duration.ofSeconds(34));
        assertFalse(deadline.isExpired());
        assertEquals(Duration.ofSeconds(1), deadline.remaining());
        clock.advance(Duration.ofSeconds(1));
        assertTrue(deadline.isExpired());
        assertEquals(Duration.ZERO, deadline.remaining());
        assertThrows(OperationDeadlineExceededException.class, deadline::throwIfExpired);
    }

    @Test
    void deadlineInputStreamAllowsReadsBeforeDeadlineAndFailsAfter() throws IOException {
        Deadline deadline = Deadline.after(Duration.ofSeconds(35), clock);
        byte[] bytes = new byte[10];
        InputStream stream = new LocalModelTransport.DeadlineInputStream(
                new ByteArrayInputStream(bytes), deadline);
        byte[] buffer = new byte[5];
        assertEquals(5, stream.read(buffer, 0, 5));
        clock.advance(Duration.ofSeconds(35));
        assertThrows(OperationDeadlineExceededException.class, stream::read);
    }

    @Test
    void deadlineInputStreamBoundsReadAllBytesAndTransferTo() {
        Deadline deadline = Deadline.after(Duration.ofSeconds(35), clock);
        clock.advance(Duration.ofSeconds(35));
        InputStream readAll = new LocalModelTransport.DeadlineInputStream(
                new ByteArrayInputStream(new byte[10]), deadline);
        assertThrows(OperationDeadlineExceededException.class, readAll::readAllBytes);
        InputStream transfer = new LocalModelTransport.DeadlineInputStream(
                new ByteArrayInputStream(new byte[10]), deadline);
        assertThrows(OperationDeadlineExceededException.class,
                () -> transfer.transferTo(new ByteArrayOutputStream()));
    }

    @Test
    void transportRefusesRequestAfterDeadline() throws IOException {
        LocalModelTransport transport = new LocalModelTransport(clock);
        Deadline deadline = Deadline.after(Duration.ofSeconds(35), clock);
        transport.setDeadline(deadline);
        clock.advance(Duration.ofSeconds(36));
        ClientHttpRequest request = transport.boundedFactory().createRequest(
                URI.create("http://127.0.0.1:11434/api/chat"), HttpMethod.POST);
        assertThrows(OperationDeadlineExceededException.class, request::execute);
        transport.clearDeadline();
    }

    @Test
    void combinedPreflightAndInferenceUnderBudgetSucceeds() {
        tagsAdvance = Duration.ofSeconds(20);
        chatAdvance = Duration.ofSeconds(14);
        LocalModelAdapter adapter = LocalModelAdapter.create(properties(), clock);
        assertEquals("hello", adapter.infer("ping"));
        assertEquals(1, tagsCalls.get());
        assertEquals(1, chatCalls.get());
        assertEquals(Duration.ofSeconds(34), Duration.ofNanos(clock.nanoTime()));
    }

    @Test
    void combinedPreflightAndInferenceCannotExceedBudget() {
        tagsAdvance = Duration.ofSeconds(20);
        chatAdvance = Duration.ofSeconds(15);
        LocalModelAdapter adapter = LocalModelAdapter.create(properties(), clock);
        assertThrows(LocalModelAdapter.AnalysisDeadlineExceededException.class,
                () -> adapter.infer("ping"));
        assertEquals(1, tagsCalls.get());
        assertEquals(1, chatCalls.get());
        assertEquals(Duration.ofSeconds(35), Duration.ofNanos(clock.nanoTime()));
    }

    @Test
    void budgetIsSharedAcrossSequentialRequests() {
        tagsAdvance = Duration.ofSeconds(30);
        chatAdvance = Duration.ofSeconds(4);
        LocalModelAdapter adapter = LocalModelAdapter.create(properties(), clock);
        assertEquals("hello", adapter.infer("ping"));
        assertEquals(1, chatCalls.get());
        assertEquals(Duration.ofSeconds(34), Duration.ofNanos(clock.nanoTime()));
    }

    @Test
    void budgetIsNotResetBySequentialRequests() {
        tagsAdvance = Duration.ofSeconds(30);
        chatAdvance = Duration.ofSeconds(5);
        LocalModelAdapter adapter = LocalModelAdapter.create(properties(), clock);
        assertThrows(LocalModelAdapter.AnalysisDeadlineExceededException.class,
                () -> adapter.infer("ping"));
        assertEquals(1, chatCalls.get());
        assertEquals(Duration.ofSeconds(35), Duration.ofNanos(clock.nanoTime()));
    }

    @Test
    void preflightConsumingEntireBudgetPreventsInference() {
        tagsAdvance = Duration.ofSeconds(35);
        chatAdvance = Duration.ZERO;
        LocalModelAdapter adapter = LocalModelAdapter.create(properties(), clock);
        assertThrows(LocalModelAdapter.AnalysisDeadlineExceededException.class,
                () -> adapter.infer("ping"));
        assertEquals(1, tagsCalls.get());
        assertEquals(0, chatCalls.get());
    }

    @Test
    void semaphoreIsReleasedAfterDeadlineFailure() {
        tagsAdvance = Duration.ofSeconds(20);
        chatAdvance = Duration.ofSeconds(15);
        LocalModelAdapter adapter = LocalModelAdapter.create(properties(), clock);
        assertThrows(LocalModelAdapter.AnalysisDeadlineExceededException.class,
                () -> adapter.infer("ping"));
        tagsAdvance = Duration.ZERO;
        chatAdvance = Duration.ZERO;
        assertEquals("hello", adapter.infer("ping"));
        assertEquals(2, tagsCalls.get());
        assertEquals(2, chatCalls.get());
    }

    @Test
    void preflightConsumesThirtySecondsAndInferenceReceivesAtMostRemainingFiveSeconds() {
        tagsAdvance = Duration.ofSeconds(30);
        chatAdvance = Duration.ofSeconds(5);
        LocalModelAdapter adapter = LocalModelAdapter.create(properties(), clock);
        assertThrows(LocalModelAdapter.AnalysisDeadlineExceededException.class, () -> adapter.infer("ping"));
        assertEquals(1, tagsCalls.get());
        assertEquals(1, chatCalls.get());
        assertEquals(Duration.ofSeconds(35), Duration.ofNanos(clock.nanoTime()));
    }

    @Test
    void preflightConsumesThirtySecondsAndInferenceReceivesLessThanRemainingFiveSeconds() {
        tagsAdvance = Duration.ofSeconds(30);
        chatAdvance = Duration.ofSeconds(4);
        LocalModelAdapter adapter = LocalModelAdapter.create(properties(), clock);
        assertEquals("hello", adapter.infer("ping"));
        assertEquals(1, chatCalls.get());
        assertEquals(Duration.ofSeconds(34), Duration.ofNanos(clock.nanoTime()));
    }

    @Test
    void preflightConsumesThirtyFourSecondsAndInferenceReceivesAtMostRemainingOneSecond() {
        tagsAdvance = Duration.ofSeconds(34);
        chatAdvance = Duration.ofSeconds(1);
        LocalModelAdapter adapter = LocalModelAdapter.create(properties(), clock);
        assertThrows(LocalModelAdapter.AnalysisDeadlineExceededException.class, () -> adapter.infer("ping"));
        assertEquals(1, chatCalls.get());
        assertEquals(Duration.ofSeconds(35), Duration.ofNanos(clock.nanoTime()));
    }

    @Test
    void preflightConsumesEntireThirtyFiveSecondBudgetAndInferenceIsNeverSent() {
        tagsAdvance = Duration.ofSeconds(35);
        LocalModelAdapter adapter = LocalModelAdapter.create(properties(), clock);
        assertThrows(LocalModelAdapter.AnalysisDeadlineExceededException.class, () -> adapter.infer("ping"));
        assertEquals(1, tagsCalls.get());
        assertEquals(0, chatCalls.get());
    }

    @Test
    void chatServerStallsBeforeResponseHeadersTerminatesAtDeadline() throws Exception {
        chatStalls = true;
        LocalModelAdapter adapter = LocalModelAdapter.create(properties(), Duration.ofSeconds(2), clock);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                adapter.infer("ping");
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        worker.start();
        assertTrue(chatArrived.await(5, TimeUnit.SECONDS));
        worker.join(5000);
        assertFalse(worker.isAlive());
        assertNotNull(failure.get());
        chatGate.countDown();
    }

    @Test
    void chatServerSendsHeadersThenStallsBeforeFirstBodyByteTerminatesAtDeadline() throws Exception {
        chatStallsBeforeBody = true;
        LocalModelAdapter adapter = LocalModelAdapter.create(properties(), Duration.ofSeconds(2), clock);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                adapter.infer("ping");
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        worker.start();
        assertTrue(chatArrived.await(5, TimeUnit.SECONDS));
        worker.join(5000);
        assertFalse(worker.isAlive());
        assertNotNull(failure.get());
    }

    @Test
    void bodyBeginsBeforeDeadlineButStallsDuringSubsequentReadTerminatesAtDeadline() throws Exception {
        chatWritePartialThenStall = true;
        LocalModelAdapter adapter = LocalModelAdapter.create(properties(), Duration.ofSeconds(2), clock);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                adapter.infer("ping");
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        worker.start();
        assertTrue(chatArrived.await(5, TimeUnit.SECONDS));
        worker.join(5000);
        assertFalse(worker.isAlive());
        assertNotNull(failure.get());
    }

    @Test
    void semaphoreIsNotReleasedWhileUnderlyingRequestIsExecuting() throws Exception {
        chatStalls = true;
        LocalModelAdapter adapter = LocalModelAdapter.create(properties(), clock);
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        Thread first = new Thread(() -> {
            try {
                adapter.infer("ping");
            } catch (Throwable t) {
                firstFailure.set(t);
            }
        });
        first.start();
        assertTrue(chatArrived.await(5, TimeUnit.SECONDS));
        LocalModelAdapter second = LocalModelAdapter.create(properties(), clock);
        assertThrows(LocalModelAdapter.AnalysisBusyException.class, () -> second.infer("second"));
        chatGate.countDown();
        first.join(5000);
        assertFalse(first.isAlive());
    }

    @Test
    void freshInferenceCanAcquireSemaphoreAfterPreviousExchangeTerminated() throws Exception {
        chatStalls = true;
        LocalModelAdapter adapter = LocalModelAdapter.create(properties(), Duration.ofSeconds(2), clock);
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        Thread first = new Thread(() -> {
            try {
                adapter.infer("ping");
            } catch (Throwable t) {
                firstFailure.set(t);
            }
        });
        first.start();
        assertTrue(chatArrived.await(5, TimeUnit.SECONDS));
        first.join(5000);
        assertFalse(first.isAlive());
        assertNotNull(firstFailure.get());
        chatGate.countDown();
        chatStalls = false;
        tagsAdvance = Duration.ZERO;
        chatAdvance = Duration.ZERO;
        assertEquals("hello", adapter.infer("ping"));
    }

    private void awaitChatGate() {
        try {
            chatGate.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ex);
        }
    }

    private static final class FakeMonotonicClock implements MonotonicClock {

        private volatile long nanos;

        @Override
        public long nanoTime() {
            return nanos;
        }

        void advance(Duration duration) {
            nanos += duration.toNanos();
        }
    }
}
