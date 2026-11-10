package com.soubhagya.systivex.localmodel;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.AbstractClientHttpRequest;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

public final class LocalModelTransport {

    public static final int MAX_RESPONSE_BYTES = 65_536;

    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    public static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private static final ProxySelector DIRECT_PROXY_SELECTOR = new ProxySelector() {
        @Override
        public List<Proxy> select(URI uri) {
            return List.of(Proxy.NO_PROXY);
        }

        @Override
        public void connectFailed(URI uri, java.net.SocketAddress sa, IOException ioe) {
        }
    };

    private final MonotonicClock clock;

    private final HttpClient httpClient;

    /**
     * Preconfigured 30-second request factory retained for direct inspection and compatibility with
     * callers that do not require the dynamic deadline. The production inference path uses
     * {@link #boundedFactory()}, which creates a per-request factory with
     * {@code min(30s, remaining deadline)}.
     */
    private final JdkClientHttpRequestFactory jdkFactory;

    private final ThreadLocal<Deadline> currentDeadline = new ThreadLocal<>();

    public LocalModelTransport() {
        this(MonotonicClock.system());
    }

    public LocalModelTransport(MonotonicClock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.httpClient = HttpClient.newBuilder()
                .proxy(DIRECT_PROXY_SELECTOR)
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.jdkFactory = new JdkClientHttpRequestFactory(httpClient);
        this.jdkFactory.setReadTimeout(READ_TIMEOUT);
    }

    public static ProxySelector directProxySelector() {
        return DIRECT_PROXY_SELECTOR;
    }

    public MonotonicClock getClock() {
        return clock;
    }

    public HttpClient httpClient() {
        return httpClient;
    }

    public JdkClientHttpRequestFactory jdkFactory() {
        return jdkFactory;
    }

    public ClientHttpRequestFactory boundedFactory() {
        return new BoundedCountingRequestFactory(httpClient, this::currentDeadline, clock, READ_TIMEOUT);
    }

    public RestClient.Builder restClientBuilder() {
        return RestClient.builder().requestFactory(boundedFactory());
    }

    public void setDeadline(Deadline deadline) {
        currentDeadline.set(Objects.requireNonNull(deadline, "deadline"));
    }

    public void clearDeadline() {
        currentDeadline.remove();
    }

    private Deadline currentDeadline() {
        return currentDeadline.get();
    }

    public static ClientHttpRequestFactory boundedCountingRequestFactory(
            ClientHttpRequestFactory originalFactory) {
        Objects.requireNonNull(originalFactory, "originalFactory");
        return new BoundedCountingRequestFactory(originalFactory, () -> null, MonotonicClock.system(), READ_TIMEOUT);
    }

    private static final class BoundedCountingRequestFactory implements ClientHttpRequestFactory {

        private final ClientHttpRequestFactory delegate;
        private final HttpClient httpClient;
        private final Supplier<Deadline> deadlineSupplier;
        private final MonotonicClock clock;
        private final Duration fallbackReadTimeout;

        private BoundedCountingRequestFactory(ClientHttpRequestFactory delegate,
                                              Supplier<Deadline> deadlineSupplier,
                                              MonotonicClock clock,
                                              Duration fallbackReadTimeout) {
            this.delegate = delegate;
            this.httpClient = null;
            this.deadlineSupplier = Objects.requireNonNull(deadlineSupplier, "deadlineSupplier");
            this.clock = Objects.requireNonNull(clock, "clock");
            this.fallbackReadTimeout = Objects.requireNonNull(fallbackReadTimeout, "fallbackReadTimeout");
        }

        private BoundedCountingRequestFactory(HttpClient httpClient,
                                              Supplier<Deadline> deadlineSupplier,
                                              MonotonicClock clock,
                                              Duration fallbackReadTimeout) {
            this.delegate = null;
            this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
            this.deadlineSupplier = Objects.requireNonNull(deadlineSupplier, "deadlineSupplier");
            this.clock = Objects.requireNonNull(clock, "clock");
            this.fallbackReadTimeout = Objects.requireNonNull(fallbackReadTimeout, "fallbackReadTimeout");
        }

        private Duration resolveReadTimeout(Deadline deadline) {
            Duration readTimeout = fallbackReadTimeout;
            if (deadline != null) {
                Duration remaining = deadline.remaining();
                if (remaining.isZero()) {
                    return Duration.ZERO;
                }
                if (remaining.compareTo(fallbackReadTimeout) < 0) {
                    readTimeout = remaining;
                }
            }
            return readTimeout;
        }

        @Override
        public ClientHttpRequest createRequest(URI uri, HttpMethod method) throws IOException {
            ClientHttpRequestFactory effectiveDelegate = delegate;
            if (effectiveDelegate == null) {
                JdkClientHttpRequestFactory jdkFactory = new JdkClientHttpRequestFactory(httpClient);
                jdkFactory.setReadTimeout(resolveReadTimeout(deadlineSupplier.get()));
                effectiveDelegate = jdkFactory;
            }
            ClientHttpRequest delegateRequest = effectiveDelegate.createRequest(uri, method);
            return new AbstractClientHttpRequest() {
                private final ByteArrayOutputStream requestBody = new ByteArrayOutputStream();

                @Override
                public HttpMethod getMethod() {
                    return method;
                }

                @Override
                public URI getURI() {
                    return uri;
                }

                @Override
                protected OutputStream getBodyInternal(HttpHeaders headers) throws IOException {
                    return requestBody;
                }

                @Override
                protected ClientHttpResponse executeInternal(HttpHeaders headers) throws IOException {
                    Deadline deadline = deadlineSupplier.get();
                    if (deadline != null && deadline.isExpired()) {
                        throw new OperationDeadlineExceededException(
                                "Local model operation exceeded the overall deadline before the request was sent");
                    }
                    delegateRequest.getHeaders().putAll(headers);
                    if (requestBody.size() > 0) {
                        try (OutputStream out = delegateRequest.getBody()) {
                            out.write(requestBody.toByteArray());
                        }
                    }
                    return new BoundedCountingClientHttpResponse(delegateRequest.execute(), deadline, clock);
                }
            };
        }
    }

    public static final class ResponseTooLargeException extends IOException {
        public ResponseTooLargeException(String message) {
            super(message);
        }
    }

    public static final class BoundedInputStream extends FilterInputStream {
        private final int maxBytes;
        private int bytesReturned;
        private int lookahead = -2;
        private boolean underlyingEof;
        private boolean failed;

        public BoundedInputStream(InputStream in, int maxBytes) {
            super(Objects.requireNonNull(in, "in"));
            if (maxBytes <= 0) {
                throw new IllegalArgumentException("maxBytes must be > 0");
            }
            this.maxBytes = maxBytes;
        }

        private int peek() throws IOException {
            if (lookahead == -2) {
                if (underlyingEof) {
                    lookahead = -1;
                } else {
                    int value = in.read();
                    if (value == -1) {
                        underlyingEof = true;
                        lookahead = -1;
                    } else {
                        lookahead = value;
                    }
                }
            }
            return lookahead;
        }

        private int consume() throws IOException {
            if (lookahead != -2) {
                int value = lookahead;
                lookahead = -2;
                return value;
            }
            if (underlyingEof) {
                return -1;
            }
            int value = in.read();
            if (value == -1) {
                underlyingEof = true;
            }
            return value;
        }

        @Override
        public int read() throws IOException {
            if (failed) {
                throw new ResponseTooLargeException("Ollama response body exceeds 65536 bytes");
            }
            if (bytesReturned >= maxBytes) {
                int peeked = peek();
                if (peeked == -1) {
                    return -1;
                }
                failed = true;
                throw new ResponseTooLargeException("Ollama response body exceeds 65536 bytes");
            }
            int value = consume();
            if (value == -1) {
                return -1;
            }
            bytesReturned++;
            return value;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            Objects.requireNonNull(b, "b");
            if (off < 0 || len < 0 || len > b.length - off) {
                throw new IndexOutOfBoundsException();
            }
            if (len == 0) {
                return 0;
            }
            int total = 0;
            while (total < len) {
                int value = read();
                if (value == -1) {
                    break;
                }
                b[off + total] = (byte) value;
                total++;
            }
            return total == 0 ? -1 : total;
        }

        @Override
        public long skip(long n) throws IOException {
            if (n <= 0) {
                return 0;
            }
            long skipped = 0;
            while (skipped < n) {
                int value = read();
                if (value == -1) {
                    break;
                }
                skipped++;
            }
            return skipped;
        }

        @Override
        public boolean markSupported() {
            return false;
        }

        @Override
        public void mark(int readlimit) {
        }

        @Override
        public void reset() throws IOException {
            throw new IOException("mark/reset not supported on bounded response body");
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }

    public static final class BoundedCountingClientHttpResponse implements ClientHttpResponse {
        private final ClientHttpResponse delegate;
        private final Deadline deadline;
        private final MonotonicClock clock;
        private InputStream body;
        private boolean closed;

        public BoundedCountingClientHttpResponse(ClientHttpResponse delegate) {
            this(delegate, null, MonotonicClock.system());
        }

        public BoundedCountingClientHttpResponse(ClientHttpResponse delegate, Deadline deadline,
                                                 MonotonicClock clock) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.deadline = deadline;
            this.clock = Objects.requireNonNull(clock, "clock");
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public InputStream getBody() throws IOException {
            if (closed) {
                throw new IOException("response closed");
            }
            if (body == null) {
                long declared = delegate.getHeaders().getContentLength();
                if (declared > MAX_RESPONSE_BYTES) {
                    close();
                    throw new ResponseTooLargeException(
                            "Ollama response Content-Length exceeds 65536 bytes: " + declared);
                }
                InputStream delegateBody = delegate.getBody();
                InputStream deadlineBody = deadline == null
                        ? delegateBody
                        : new DeadlineInputStream(delegateBody, deadline);
                body = new BoundedInputStream(deadlineBody, MAX_RESPONSE_BYTES);
            }
            return body;
        }

        @Override
        public void close() {
            closed = true;
            Throwable primary = null;
            if (body != null) {
                try {
                    body.close();
                } catch (Throwable ex) {
                    primary = ex;
                }
                body = null;
            }
            try {
                delegate.close();
            } catch (Throwable ex) {
                if (primary != null) {
                    primary.addSuppressed(ex);
                } else {
                    primary = ex;
                }
            }
            if (primary instanceof RuntimeException) {
                throw (RuntimeException) primary;
            }
            if (primary instanceof Error) {
                throw (Error) primary;
            }
            if (primary != null) {
                throw new RuntimeException(primary);
            }
        }
    }

    public static final class DeadlineInputStream extends FilterInputStream {

        private final Deadline deadline;

        public DeadlineInputStream(InputStream in, Deadline deadline) {
            super(Objects.requireNonNull(in, "in"));
            this.deadline = Objects.requireNonNull(deadline, "deadline");
        }

        private void checkDeadline() {
            if (deadline.isExpired()) {
                throw new OperationDeadlineExceededException(
                        "Local model operation exceeded the overall deadline while reading the response body");
            }
        }

        @Override
        public int read() throws IOException {
            checkDeadline();
            return super.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            checkDeadline();
            return super.read(b, off, len);
        }

        @Override
        public long skip(long n) throws IOException {
            checkDeadline();
            return super.skip(n);
        }
    }
}
