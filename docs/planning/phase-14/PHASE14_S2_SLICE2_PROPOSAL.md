# Phase 14 — S2 Slice 2: Local Model Provider Integration (Spring AI + Local Ollama)

**Author:** Principal Architect & Senior Java Engineer, Systivex (technical repair pass)
**Status:** DRAFT — Proposal D41 for independent review — **NOT ADOPTED**
**Target:** `docs/planning/phase-14/PHASE14_S2_SLICE2_PROPOSAL.md`
**Branch:** main (HEAD: `95f9e2e45975386d19fd068293aa60f6604917ac`)
**Intent:** Design-only repair. This pass corrects blockers G-1, G-2, G-3 and G-5 from the independent NOT ADOPTABLE review. D41 remains DRAFT and NOT ADOPTED. It grants no implementation authorization, does not modify the adopted D40 authority, and must not be added to DECISIONS.md by this task.

**Repair pass verification basis:**

- Branch/HEAD re-checked: `main` @ `95f9e2e45975386d19fd068293aa60f6604917ac` (also recorded in §11).
- Spring AI 2.0.2 claims re-checked against (a) the v2.0.2 source tags, and (b) the **resolved 2.0.2 artifacts** in an isolated Maven scratch project **outside** this repository (Java 21, Spring Boot 4.1.1, Spring AI 2.0.2, direct `spring-ai-ollama`) — see §9 and §11.
- Corrected G-1 examples **compiled and executed** in that external scratch project (`mvn compile`, `mvn exec:java`, all boundary cases PASS). No test or implementation files were created inside this repository.

---

## 1. GOVERNANCE AND SOURCE OF TRUTH

This proposal is authored against the following canonical references:

- **DECISIONS.md** — D8, D28–D31, D38, D39, D40 (D40 remains the adopted authority)
- **ARCHITECTURE.md** — Phase 14 evidence and S2 design
- **DEVELOPMENT.md** — Phase 14 implementation guidance
- **PROJECT_STATUS.md**
- **THREAT_MODEL.md** — §3b
- **PHASE14_S1_PROPOSAL.md** — S1 evidence packet structures (SHA-256 checked, see §11)
- **PHASE14_S2_PROPOSAL.md** — S2 Slice 1 authorization (D40)
- Actual S1 EvidencePacket, EvidenceClaim and ClaimValidator implementation
- Existing backend dependency management and Spring Boot configuration
- Existing LoopbackGuard and local-service security guards
- Spring AI v2.0.2 source (`github.com/spring-projects/spring-ai` at tag `v2.0.2`) and resolved 2.0.2 artifacts
- Spring Framework 7 resolved at `7.0.9` under Spring Boot 4.1.1 BOM (see §9 — not the `7.0.10` the AI POM prefers)
- Jackson 3.1.5 (`tools.jackson.core:jackson-databind:3.1.5`, resolved)

**Verify:** current branch is main, HEAD is `95f9e2e45975386d19fd068293aa60f6604917ac`.
**Preserve:** both `PHASE14_S1_PROPOSAL.md` and `PHASE14_S2_PROPOSAL.md` unchanged. **Modify only this D41 proposal.**

---

## 2. D41 AUTHORIZATION SCOPE

**D41** proposes authorization for **S2 Slice 2 ONLY** — a local model provider integration using Spring AI with local Ollama. Slice 2 does **not** implement evidence-grounded claim generation.

### Proposed Scope

- Provider-neutral internal inference boundary
- Spring AI integration with local Ollama (direct `spring-ai-ollama:2.0.2`, §9)
- Minimal Ollama-specific adapter
- Strict local endpoint configuration and validation
- Server-bind and cloud-disabled deployment requirements
- Bounded synchronous inference request/response contract (65,536-byte cap, §4)
- Connection, read and overall timeout enforcement (§7, JDK `HttpClient` semantics)
- Response byte limits and output constraints (§4)
- Serialized process-wide concurrency
- Model/version verification and local model availability (§8)
- Provider failure handling with exactly one inference attempt and no retry (§5)
- Transport identity: direct-only proxy, redirects disabled (§6)
- Unit, adapter and integration tests (§11.3)
- Operational configuration documentation

### Explicit Exclusions (Slice 2)

- Prompt templates or evidence-specific prompt construction
- EvidencePacket-to-prompt assembly
- Generated EvidenceClaim creation
- Semantic entailment evaluation
- Human-review UI
- Public API or endpoint
- Frontend
- Persistence or conversation history
- Chat memory, RAG, embeddings or vector stores, tools/MCP
- Cloud providers or remote fallback
- Streaming inference (`stream()`, `OllamaApi.streamingChat()`, `OllamaApi.pullModel()`) — see §6 for the WebClient accounting
- **Spring AI remains the framework** — do not replace it with a hand-written direct Ollama HTTP client

### Slice 3

- Remains separately gated and requires a future decision
- D8 remains unchanged
- D40 remains unchanged
- Do not treat D8 as implementation authorization

---

## 3. SPRING AI 2.0.2 SOURCE VERIFICATION

### Baseline Compatibility

| Component | Official 2.0.2 Baseline | Systivex Baseline | Status |
|-----------|------------------------|-------------------|--------|
| **Spring Boot** | 4.0 / 4.1 | **4.1.1** (from `backend/pom.xml:8`) | ✅ Compatible — no upgrade needed |
| **Spring Framework** | 7.0 | **7.0.9 resolved** (Boot 4.1.1 BOM; AI 2.0.2 POM prefers 7.0.10) | ✅ Compatible — no upgrade needed |
| **Java** | 17+ | **21** | ✅ Compatible |
| **Jackson** | 3.1.5 | **3.1.5** (`tools.jackson.core:jackson-databind`) | ✅ No conflict |

### OllamaApi.Builder Verified Extension Points

Verified against both v2.0.2 source and `javap` on the resolved `spring-ai-ollama-2.0.2.jar`:

| Method | Signature | Description |
|--------|-----------|-------------|
| `baseUrl(String)` | `OllamaApi.Builder baseUrl(String baseUrl)` | Sets the base URL for Ollama API |
| `restClientBuilder(RestClient.Builder)` | `OllamaApi.Builder restClientBuilder(RestClient.Builder restClientBuilder)` | Sets custom RestClient builder — **the Slice 2 bounded-transport injection point (§4, §6)** |
| `webClientBuilder(WebClient.Builder)` | `OllamaApi.Builder webClientBuilder(WebClient.Builder webClientBuilder)` | Sets custom WebClient builder — must be explicitly configured (§6) |
| `responseErrorHandler(ResponseErrorHandler)` | `OllamaApi.Builder responseErrorHandler(ResponseErrorHandler responseErrorHandler)` | Sets response error handler |

**Does NOT expose:**
- `connectTimeout(Duration)` — **nonexistent builder method**
- `readTimeout(Duration)` — **nonexistent builder method**
- `duration(Duration)` — **nonexistent builder method**

**Important sizing fact (v2.0.2 source):** the `OllamaApi` constructor `clone()`s both builders, sets `baseUrl` and default JSON headers, and builds **both** a `RestClient` (`restClient`) and a `WebClient` (`webClient`). Synchronous methods (`chat()`, `embed()`, `listModels()`, `showModel()`, `copyModel()`, `deleteModel()`) use the `RestClient`; `streamingChat()` and `pullModel()` use the `WebClient`. Therefore supplying only `restClientBuilder` leaves a **second, unconfigured transport** unless `webClientBuilder` is also explicitly set (§6).

### OllamaChatModel.Builder Verified Configuration

Verified against v2.0.2 source and `javap` on the resolved jar:

| Method | Signature | Description |
|--------|-----------|-------------|
| `ollamaApi(OllamaApi)` | `OllamaChatModel.Builder ollamaApi(OllamaApi ollamaApi)` | Sets the OllamaApi instance |
| `options(OllamaChatOptions)` | `OllamaChatModel.Builder options(OllamaChatOptions options)` | Sets chat options |
| `observationRegistry(ObservationRegistry)` | `OllamaChatModel.Builder observationRegistry(ObservationRegistry observationRegistry)` | Sets observation registry |
| `modelManagementOptions(ModelManagementOptions)` | `OllamaChatModel.Builder modelManagementOptions(ModelManagementOptions modelManagementOptions)` | Sets model-management options — **does not control inference retries (§5)** |
| `retryTemplate(RetryTemplate)` | `OllamaChatModel.Builder retryTemplate(org.springframework.core.retry.RetryTemplate retryTemplate)` | **Sets the inference retry template — this method exists (see §5 correction)** |

**Does NOT expose timeout methods:**
- `connectTimeout(Duration)` — **does not exist**
- `readTimeout(Duration)` — **does not exist**
- `duration(Duration)` — **does not exist**

### Design Consequence

Timeouts must be configured through the **JDK `HttpClient` + `JdkClientHttpRequestFactory`** under the `RestClient`, not through `OllamaApi`/`OllamaChatModel` builder methods (§6, §7).

---

## 4. 64 KiB RESPONSE LIMIT

### Design: Bounded Response-Body Stream in a Wrapping Request Factory

Use `OllamaApi.Builder.restClientBuilder(RestClient.Builder)` to inject a `ClientHttpRequestFactory` produced by the explicit wrapper below. The wrapper receives the original request factory as an **explicit parameter** (`originalFactory`) and decorates it. There is no implicit `originalFactory`, and response buffering is never enabled.

```java
public static ClientHttpRequestFactory boundedCountingRequestFactory(
        ClientHttpRequestFactory originalFactory) {
    Objects.requireNonNull(originalFactory, "originalFactory");
    return (uri, method) -> {
        ClientHttpRequest delegateRequest = originalFactory.createRequest(uri, method);
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
                delegateRequest.getHeaders().putAll(headers);
                if (requestBody.size() > 0) {
                    try (OutputStream out = delegateRequest.getBody()) {
                        out.write(requestBody.toByteArray());
                    }
                }
                return new BoundedCountingClientHttpResponse(delegateRequest.execute());
            }
        };
    };
}
```

**Why this shape:** `ClientHttpRequestFactory.createRequest(URI, HttpMethod)` is the only creation method (the previous draft's `create(...)` / `execute(URI, HttpMethod)` / `getHeaders()`-returns-null signatures were fabricated interface methods and would not compile). The `AbstractClientHttpRequest` subclass forwards the caller's headers and buffered request body to the delegate request and wraps the delegate response before returning it. This example was compiled against `spring-web 7.0.9` in the external scratch project (see §11) — including the Spring 7 `ClientHttpResponse.getStatusCode(): HttpStatusCode` and `getStatusText()` signatures.

### BoundedCountingClientHttpResponse

```java
public static final class BoundedCountingClientHttpResponse implements ClientHttpResponse {
    private final ClientHttpResponse delegate;
    private BoundedInputStream body;
    private boolean closed;

    public BoundedCountingClientHttpResponse(ClientHttpResponse delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
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
            long declared = delegate.getHeaders().getContentLength(); // -1 if unknown
            if (declared > MAX_BYTES) {
                close();
                throw new ResponseTooLargeException(
                        "Ollama response Content-Length exceeds 65536 bytes: " + declared);
            }
            body = new BoundedInputStream(delegate.getBody(), MAX_BYTES);
        }
        return body; // the single instance is returned every time: the counter persists
    }

    @Override
    public void close() {
        closed = true;
        IOException failure = null;
        if (body != null) {
            try {
                body.close();
            } catch (IOException e) {
                failure = e;
            }
            body = null;
        }
        try {
            delegate.close(); // delegate is always closed, even if the body stream failed to close
        } catch (RuntimeException e) {
            if (failure != null) {
                failure.addSuppressed(e);
            } else {
                throw e;
            }
        }
        if (failure != null) {
            throw new RuntimeException(failure);
        }
    }
}
```

### BoundedInputStream

```java
public static final class BoundedInputStream extends FilterInputStream {
    private final int maxBytes;
    private int bytesReturned;          // exactly ONE authoritative counter of bytes handed to the caller
    private int lookahead = -2;         // -2 = empty, -1 = EOF observed, 0..255 = peeked byte (not returned, not counted)
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
                int v = in.read();
                if (v == -1) {
                    underlyingEof = true;
                    lookahead = -1;
                } else {
                    lookahead = v;
                }
            }
        }
        return lookahead;
    }

    private int consume() throws IOException {
        if (lookahead != -2) {
            int v = lookahead;
            lookahead = -2;
            return v;
        }
        if (underlyingEof) {
            return -1;
        }
        int v = in.read();
        if (v == -1) {
            underlyingEof = true;
        }
        return v;
    }

    @Override
    public int read() throws IOException {
        if (failed) {
            throw new ResponseTooLargeException("Ollama response body exceeds 65536 bytes");
        }
        if (bytesReturned >= maxBytes) {
            int p = peek(); // bounded lookahead: distinguish exact-limit EOF from oversized body
            if (p == -1) {
                return -1; // exactly 65536 bytes followed by EOF: accepted
            }
            failed = true; // byte 65537 is never returned to the caller or JSON parser
            throw new ResponseTooLargeException("Ollama response body exceeds 65536 bytes");
        }
        int v = consume();
        if (v == -1) {
            return -1;
        }
        bytesReturned++; // `v != -1` counts byte value 0 correctly (the old `v > 0` check did not)
        return v;
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
            int v = read(); // bulk reads funnel through read(): the single counter is shared
            if (v == -1) {
                break;
            }
            b[off + total] = (byte) v;
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
            int v = read(); // skipping consumes through the counted path; overflow throws instead of silently skipping byte 65537
            if (v == -1) {
                break;
            }
            skipped++;
        }
        return skipped;
    }

    @Override
    public boolean markSupported() {
        return false; // prevents replay from bypassing the counter
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
        in.close(); // delegates; underlying I/O failures propagate unchanged
    }
}
```

### How G-1 is satisfied

- **Defined `originalFactory`:** the wrapper takes `originalFactory` as an explicit non-null parameter and calls `originalFactory.createRequest(uri, method)`. No undefined identifier remains.
- **Exactly one authoritative counter:** `bytesReturned` in `BoundedInputStream` is the sole counter, incremented only in `read()` after a byte is handed to the caller. The removed lambda (`bytesRead -> { bytesRead += bytesRead; ... }`) with its shadowing local and dead `ByteArrayOutputStream` field is deleted.
- **No off-by-one exposure:** the limit check happens **before** handing a byte to the caller, never after incrementing past the limit and returning the oversized byte. `65536` is the maximum; byte `65537` is never placed in the caller's buffer and never returned.
- **Exact-boundary case:** when `bytesReturned == 65536` and the caller asks for one more byte, the stream peeks one byte of lookahead. EOF (`-1`) returns `-1` (accepted); any real byte throws `ResponseTooLargeException` and is discarded. This is the bounded-lookahead rule: the lookahead byte is held in a single slot, is not counted as returned, and is never returned to the caller or the JSON parser.
- **Unknown-length/chunked:** the same peek distinguishes exact EOF from an oversized chunked body without trusting `Content-Length`. Oversized streams are rejected mid-read, not after buffering.
- **`Content-Length`:** validated when present (`> 65536` is rejected in `getBody()` before the stream is created and the response is closed), but **never** relied on as the only enforcement — misleading or missing values are still policed by the streaming counter.
- **Bulk vs single-byte:** `read(byte[], off, len)` fills the caller's array by calling `read()`, so both paths share the counter and boundary. Empty-stream bulk reads return `-1` (not `0`).
- **`skip` / `readAllBytes` / `transferTo`:** `skip` is overridden to consume byte-by-byte through `read()` (the `FilterInputStream` default would delegate to the underlying skip and bypass counting). `readAllBytes`/`transferTo` consume through `read(byte[], …)`, which funnels into `read()`. All consumption paths are bounded.
- **Close delegation:** `close()` closes the bounded body and then closes the delegate response even on body-close failure, preserving the body failure with suppression of a secondary delegate failure.
- **No buffering:** no `BufferingClientHttpRequestFactory`; enforcement happens while reading.
- **Underlying I/O failures:** `in.read()` exceptions propagate unchanged; byte value `0` (`NUL`) is counted correctly (`v != -1`), which the old draft's `b > 0` check missed.

### Synchronous Path Only

- **Use:** `OllamaChatModel.call()` (synchronous only)
- **Do NOT invoke:** `stream()` or `OllamaApi.streamingChat()` in Slice 2
- **Rationale:** Slice 2 authorized scope is bounded synchronous inference only

### Required Verification Cases (all exercised in the external scratch project)

| Case | Scratch result |
|------|---------------|
| 0 bytes | PASS |
| 65,535 bytes | PASS |
| Exactly 65,536 bytes followed by EOF | PASS (accepted) |
| 65,537 bytes | PASS (rejected, byte 65,537 never returned) |
| Bulk reads of varying sizes | PASS (`read(byte[],…)` shares the counter) |
| Single-byte reads | PASS |
| `skip` (including overflow skip) | PASS (overflow throws) |
| `readAllBytes` (exact and overflow) | PASS |
| Early close | PASS (delegates, no exception) |
| Underlying stream failure (`IOException("boom")`) | PASS (propagates unchanged) |
| Factory forwards POST body + wraps the response | PASS (delegate saw `{"hello":1}`, response body bounded) |
| Declared `Content-Length` > 65,536 | Rejected pre-stream (code path reviewed; deterministic unit test still required) |
| Missing / misleading `Content-Length`, chunked oversized | Streaming counter enforces (deterministic unit test still required) |

---

## 5. RETRY BEHAVIOR

### Critical Source Finding

**OllamaChatModel.Builder defaults to `RetryUtils.DEFAULT_RETRY_TEMPLATE`.**

**OllamaChatModel.internalCall() invokes `RetryUtils.execute(this.retryTemplate, () -> this.chatApi.chat(request))`.**

**`ModelManagementOptions.maxRetries` does NOT disable inference retries.** It is the Reactor `Retry.backoff` count for **model pulls** (`OllamaModelManager.pullModel`, defaults `0`, no-op under `PullModelStrategy.NEVER`) — a different mechanism from the inference retry template. Setting `maxRetries=0` there changes nothing about `/api/chat` retries.

### Two APIs That Must Not Be Conflated

**A. `OllamaChatModel.Builder.retryTemplate(RetryTemplate)` — the Spring AI builder setter (this is what D41 configures).**

```java
OllamaChatModel model = OllamaChatModel.builder()
        .ollamaApi(ollamaApi)
        .options(options)
        .modelManagementOptions(ModelManagementOptions.builder()
                .pullModelStrategy(PullModelStrategy.NEVER) // explicit operator provisioning
                .maxRetries(0)
                .build())
        .retryTemplate(noRetryTemplate()) // <- exactly one inference attempt
        .build();
```

- Exact type (jar-verified): `OllamaChatModel.Builder.retryTemplate(org.springframework.core.retry.RetryTemplate)` — the setter **exists** in Spring AI 2.0.2. (The previous draft's §5 statement that the builder "does not expose a retryTemplate() method" was false and is withdrawn.)
- Exact type of the parameter: **`org.springframework.core.retry.RetryTemplate`** — Spring Framework 7's resilience `RetryTemplate` (package `org.springframework.core.retry`), already on the classpath via Spring Framework 7. The default `RetryUtils.DEFAULT_RETRY_TEMPLATE` is built with `RetryPolicy.builder().maxRetries(10)…` plus exponential backoff (`delay 2000ms`, `multiplier 5`, `maxDelay 180s`) and is therefore **not** used when a custom template is supplied.

**B. `RetryTemplate.builder().maxAttempts(int).build()` — a static factory of the *old* Spring Retry library (`org.springframework.retry.support.RetryTemplate`, artifact `org.springframework.retry:spring-retry`).**

- This is a **different type from a different artifact**. `spring-retry` is **not** in the resolved Spring AI 2.0.2 graph (§9: absent from `dependency:tree` and `dependency:list`, never downloaded). `maxAttempts(1)` in that library means "one total attempt" (its Javadoc: "A value of 3 would result in an initial attempt and two retries"), but **that API is unavailable to the Slice 2 adapter and must not be used**. Any code written against `org.springframework.retry.support.RetryTemplate`/`SimpleRetryPolicy`/`RetryCallback`/`RetryContext` will fail to compile against the Spring AI 2.0.2 classpath.

### Correct No-Retry Construction for the Spring Framework 7 Type

`org.springframework.core.retry.RetryTemplate` has **no `builder()` static factory and no `maxAttempts(int)` method** (jar-verified). It is constructed from a `RetryPolicy`:

```java
@Bean
public RetryTemplate noRetryTemplate() {
    RetryPolicy policy = RetryPolicy.builder().maxRetries(0).build(); // total attempts = 1 + maxRetries = 1
    return new RetryTemplate(policy);
}
```

with the import `org.springframework.core.retry.RetryTemplate`. The equivalent shorthand `RetryPolicy.withMaxRetries(0)` (Spring Framework 7.0.10 API: "total attempts = 1 initial attempt + maxRetries attempts") may be used but the builder form above is the design's pinned choice because it matches the jar-verified surface on resolved `spring-core 7.0.9`.

**`maxRetries(0)` semantics:** zero retries beyond the initial invocation, so **one total attempt** — not one retry. **`maxRetries(1)` would mean two total attempts** and is explicitly forbidden for inference. No backoff is applied with zero retries; the adapter adds no queue, loop, or remote fallback of its own.

### Acceptance

Inference performs **exactly one attempt**. A provider failure is logged and produces `AnalysisProviderUnavailable`. No retry, backoff, queue, or remote fallback is attempted. (The bounded transport cannot silently introduce a second request: a failed `createRequest`/`execute` fails the call.)

---

## 6. PROXY AND REDIRECTS

### Deployment Rule

Ollama must share the application's **loopback/network namespace**. A normal Docker bridge or `host.docker.internal` is **not** equivalent to shared loopback and is not compliant. The deployment binds Ollama to `127.0.0.1` only.

### Direct-Only ProxySelector

```java
ProxySelector proxySelector = new ProxySelector() {
    @Override
    public List<Proxy> select(URI uri) {
        return List.of(Proxy.NO_PROXY); // direct-only: explicit NO_PROXY, never a proxy route
    }

    @Override
    public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
        // observed only; connection failure then fails the call (no alternate route, no fallback)
    }
};
```

### Single Transport Construction (complete chain)

```java
ProxySelector proxySelector = new ProxySelector() {
    @Override
    public List<Proxy> select(URI uri) {
        return List.of(Proxy.NO_PROXY);
    }

    @Override
    public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
    }
};

HttpClient httpClient = HttpClient.newBuilder()
        .proxy(proxySelector)                          // the exact instance above — never getDefault()
        .connectTimeout(Duration.ofSeconds(5))         // connect timeout lives on HttpClient (§7)
        .followRedirects(HttpClient.Redirect.NEVER)    // NEVER — the JDK API name is followRedirects
        .build();

JdkClientHttpRequestFactory jdkFactory = new JdkClientHttpRequestFactory(httpClient);
jdkFactory.setReadTimeout(Duration.ofSeconds(30));     // read timeout on the factory (10ms int vs Duration overloads both exist)

ClientHttpRequestFactory boundedFactory = boundedCountingRequestFactory(jdkFactory); // §4 — same factory

OllamaApi ollamaApi = OllamaApi.builder()
        .baseUrl("http://127.0.0.1:11434")
        .restClientBuilder(RestClient.builder().requestFactory(boundedFactory))
        .webClientBuilder(WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(65_536)))
                // WebClient NEVER carries Slice 2 traffic (see below); still explicitly configured so no default unconfigured transport exists.
        .build();
```

The JDK `HttpClient.Builder` method is `followRedirects(HttpClient.Redirect.NEVER)` (there is no `redirect(...)` on `HttpClient.Builder`) — verified by the scratch compile. `HttpClient.Redirect.NEVER` refuses every redirect response, including loopback redirects.

### Properties That Must Hold

- **The same configured `HttpClient`** is supplied to `JdkClientHttpRequestFactory(HttpClient)`.
- That factory is wrapped by the §4 bounded wrapper, and the **wrapped** factory is the one put into the `RestClient.Builder` passed to `OllamaApi.Builder.restClientBuilder(...)`.
- The **synchronous inference path** (`OllamaChatModel.call()` → `RetryUtils.execute(...)` → `OllamaApi.chat()` → `this.restClient` → the bounded factory) uses this transport. The inference request is never built with a default/unconfigured client.
- **Other Slice 2 paths accounted for (complete list):** `OllamaApi.chat()` (`POST /api/chat`), `listModels()` (`GET /api/tags`), `showModel()` (`POST /api/show`) — all via the configured `RestClient`. Slice 2 **forbids** `streamingChat()` and `pullModel()` (WebClient paths); `ModelManagementOptions.pullModelStrategy(NEVER)` makes `OllamaModelManager` a no-op (returns before any pull). `webClientBuilder` is still explicitly set so the `OllamaApi` constructor's `WebClient.builder()` default never silently forms an unconfigured transport, and navigation tests (§11.3) assert the WebClient path is unreachable.
- **No fallback HTTP client:** if `OllamaApi` construction would fall back to any default request factory, provisioning is invalid. `ProxySelector.getDefault()` must not appear anywhere in the transport construction — the explicit `proxySelector` instance is the only selector supplied.
- **JVM proxy properties cannot replace it:** the explicit `.proxy(proxySelector)` overrides `java.net.useSystemProxies`/`http.proxyHost` defaults for this `HttpClient`. `HTTP_PROXY`/`HTTPS_PROXY`/`NO_PROXY` environment variables are only secondary defence; deployments still set `NO_PROXY=127.0.0.1`/`no_proxy=127.0.0.1`, but the security boundary is the selector, not the environment.
- **Redirect rejection:** every `3xx` (including `301`/`307`/`308` to an external host) fails the call without following. There is no redirect-capable fallback.

### Tests Specified

- **Proxy bypass:** run with `HTTP_PROXY`/`http.proxyHost` pointing at a mock proxy; assert the mock records **zero** requests containing the inference endpoint and that the direct loopback call succeeds.
- **Redirect rejection:** mock Ollama returns `302` (and separately `307`) `Location: http://169.254.169.254/…`; assert failure, no outbound follow-up request.
- **Transport identity:** assert via reflection/wiring that `OllamaApi`'s `restClient` factory **is** (transitively) the single `JdkClientHttpRequestFactory` built from the one configured `HttpClient` instance; assert `ProxySelector.getDefault()` was never invoked (e.g. a test selector records `select` calls).
- **Outbound paths:** parameterised test enumerating `chat`, `listModels`, `showModel` proves each goes through the bounded, proxy-free transport; a test proves `streamingChat`/`pullModel` are unreachable in Slice 2.

---

## 7. TIMEOUTS AND CANCELLATION

### Actual Factory APIs (jar-verified on resolved 7.0.9)

`JdkClientHttpRequestFactory` exposes `setReadTimeout(int)` and `setReadTimeout(Duration)` — **and no connect-timeout setter**. The connect timeout is `HttpClient.Builder.connectTimeout(Duration)`. There is no `setConnectTimeout(...)` on the JDK factory; designs that call it will not compile.

### Documented Timeout Semantics

| Timeout | Semantics | Configuration |
|---------|-----------|---------------|
| **5-second connection timeout** | Time to establish TCP connection; fail fast on network issues | `HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))` |
| **30-second HTTP exchange/read timeout** | Time to read response body after connection established; prevents hung requests | `JdkClientHttpRequestFactory.setReadTimeout(Duration.ofSeconds(30))` |
| **35-second overall operation deadline** | Total time from request initiation to response completion | `connectTimeout + readTimeout <= 35000`; verify composition does not exceed 35s |

### Composition Constraint

**The 35-second overall deadline is the total from request initiation to response completion.** It comprises:

1. **Connection phase** (up to 5s): TCP handshake, TLS negotiation (if applicable), server connection
2. **Read phase** (up to 30s): Response body read via `ClientHttpResponse.getBody()`
3. **Composition constraint:** `connect_timeout + read_timeout <= 35000ms` ensures the total cannot exceed 35s

`JdkClientHttpRequestFactory` implements the read timeout as the JDK `HttpClient` response timeout; combined with the 5s connect timeout the total cannot exceed 35s.

### Cancellation and Permit Release

**Critical:** Cancellation must not release the process-wide semaphore while an underlying inference operation is still running.

- **Permit release** must occur **only when the actual operation terminates** (success, exception, or timeout)
- **Semaphore** in the adapter layer tracks in-flight requests
- **Cancellation** of the `HttpClient` or `RestClient` request must be followed by permit release in a `finally` block
- **Do not** release the permit on cancellation callback alone if the operation may still be in progress

```java
private final Semaphore inferencePermit = new Semaphore(1, true);

public String infer(String prompt) throws InterruptedException {
    if (!inferencePermit.tryAcquire(0, TimeUnit.MILLISECONDS)) {
        throw new AnalysisBusyException("Another inference in progress");
    }
    try {
        return ollamaChatModel.call(prompt); // single attempt, no retry, bounded stream
    } finally {
        inferencePermit.release(); // ONLY after the actual operation terminates
    }
}
```

### Critical: Do Not Release Permit on Cancellation Alone

If the underlying `HttpClient.cancel()` interrupts the request but the operation may still be running in background, **do not release the permit** until the adapter can confirm termination.

---

## 8. MODEL IDENTITY

### Keep Installed Digest Checks (jar/source-verified)

| Endpoint | Purpose | Action |
|----------|---------|--------|
| `GET /api/tags` (`OllamaApi.listModels()`) | Verify installed model digest | Check expected digest exists; fail closed on mismatch |
| `GET /api/show` (`OllamaApi.showModel()`) | Model details fall-back | No digest field is relied on — fall back to `/api/tags` verification |

**Correction carried forward:** the earlier draft required `GET /api/ps` (`/api/ps` running-model digest). **Spring AI 2.0.2's `OllamaApi` has no `/api/ps` client method** (verified against v2.0.2 source and `spring-ai-ollama-2.0.2.jar` — there is `listModels` `/api/tags`, `showModel` `/api/show`, `chat` `/api/chat`, `embed` `/api/embed`, `copy`/`delete`/`pull`, and `streamingChat`). Any `/api/ps` check would require a custom `RestClient` call outside `OllamaApi`; if introduced it must use the **same** §4/§6 bounded, direct-only transport. Slice 2 does not require it.

### No Per-Request Cryptographic Proof (accepted residual limitation, preserved)

**Preserve the no-per-request-cryptographic-proof limitation.**

- `/api/tags` and `/api/show` support installed-model identity checks.
- They do **not** cryptographically prove the model used by each individual `/api/chat` request.
- No per-request cryptographic identity guarantee is claimed.
- **Document this limitation explicitly:** "No per-request cryptographic model identity proof is provided by Ollama or Spring AI. Pre-flight digest verification confirms installed model only. The system assumes the installed model remains unchanged during the session."

### Fail-Closed Behavior

- Missing expected digest in `/api/tags` → fail closed, refuse inference
- No digest field in `/api/show` → do not rely on it; fall back to `/api/tags` verification
- Running-model digest confirmation is out of scope for the 2.0.2 `OllamaApi` surface (see correction above)

---

## 9. DEPENDENCY MANAGEMENT (G-5)

### Minimal Dependency (resolved, not assumed)

**Use `org.springframework.ai:spring-ai-ollama:2.0.2` directly**, with manual in-process construction of `OllamaApi` and `OllamaChatModel`. The broad `spring-ai-starter-model-ollama` is **not** used — no technical justification for it exists; §11 reports the actual resolved graph of the direct artifact.

### Actual Verification (performed during this repair pass)

An isolated project was built **outside** this repository (`C:\Users\Asus\AppData\Local\Temp\opencode\scratch-s2s2`, Java 21, Spring Boot 4.1.1 BOM + Spring AI BOM 2.0.2, direct `org.springframework.ai:spring-ai-ollama:2.0.2`). Repository `pom.xml` was **not** modified.

**Commands run (PowerShell, paths quoted):**

```powershell
mvn dependency:tree -Dverbose
mvn "org.apache.maven.plugins:maven-help-plugin:3.5.1:effective-pom" "-Doutput=effective-pom.xml"
mvn dependency:list
```

**`mvn dependency:tree -Dverbose` → BUILD SUCCESS, `mvn dependency:list` → BUILD SUCCESS, effective-POM → written to `effective-pom.xml` (BUILD SUCCESS).** Representative resolved graph (exact, from the scratch `full-tree.txt` / `dep-list.txt`):

```text
com.scratch:s2s2-verify:jar:1.0-SNAPSHOT
├─ org.springframework.ai:spring-ai-ollama:2.0.2
│  ├─ org.springframework.ai:spring-ai-model:2.0.2
│  │  ├─ org.springframework.ai:spring-ai-commons:2.0.2
│  │  ├─ org.springframework.ai:spring-ai-template-st:2.0.2
│  │  ├─ org.springframework.ai:spring-ai-retry:2.0.2
│  │  ├─ org.springframework:spring-webflux:7.0.9 (→ reactor-core 3.8.7)
│  │  ├─ com.github.victools:jsonschema-generator:5.0.0 (+ jsonschema-module-jackson, jsonschema-module-swagger-2)
│  │  └─ io.swagger.core.v3:swagger-annotations-jakarta:2.2.38
│  └─ tools.jackson.core:jackson-databind:3.1.5 (+ jackson-core 3.1.5, jackson-annotations 2.21)
├─ org.springframework:spring-web:7.0.9
└─ org.springframework:spring-core:7.0.9
```

**Exact resolved versions (from `mvn dependency:list`):** `spring-ai-ollama`/`spring-ai-model`/`spring-ai-commons`/`spring-ai-template-st`/`spring-ai-retry` **2.0.2**; `spring-web`/`spring-core`/`spring-webflux`/`spring-context` **7.0.9**; `tools.jackson.core:jackson-databind`/`jackson-core` **3.1.5**; `io.projectreactor:reactor-core` **3.8.7**; `micrometer-core` **1.17.1**. (The earlier draft's claim of Spring Framework 7.0.10 was the AI POM's preferred version; the Boot 4.1.1 BOM resolves the framework to **7.0.9** — recorded here as the true value.)

### Unwanted Dependencies: review of the actual graph

**Absent from the resolved graph (the proposal's feared modules are not pulled by the direct artifact):**

- `spring-ai-client-chat` (ChatClient) — absent
- `spring-ai-autoconfigure-model-chat-client` — absent
- `spring-ai-autoconfigure-model-chat-memory` (and every other `spring-ai-autoconfigure-*`) — absent
- `spring-boot-starter-webclient` / `spring-boot-starter-restclient` — absent
- `org.springframework.retry:spring-retry` — absent (never downloaded; confirms §5 API-B does not apply)
- embeddings / vector-store / RAG / MCP artifacts — absent

**Present and accepted:**

- `spring-ai-model` transitives: `jackson` 3.1.5 (data binding for request/response records), `reactor-core` (WebClient type signatures — required by `OllamaApi` even though Slice 2 never streams), `spring-messaging`, `micrometer` (observation), `jtokkit`, `jsonschema` + `swagger-annotations` (tool-definition schemas — required by `spring-ai-model`'s `ToolCallingManager` types even though Slice 2 declares no tools), `ST4/antlr` + `spring-ai-template-st` (prompt-template support — present as a library only; Slice 2 declares no templates).
- No auto-configuration is pulled at all (there is no `spring-ai-autoconfigure-*` in the graph), so the earlier draft's mitigation list (`@SpringBootApplication(exclude=…)`, `@ExcludeFilters`, `spring.autoconfigure.exclude`) is **unnecessary** — there are no unwanted auto-configurations to exclude. Manual bean construction with `@ConditionalOnProperty(name = "ai.local.enabled", havingValue = "true", matchIfMissing = false)` remains the design.

This corrects the previous draft's assumed starter-POM graph (`spring-ai-starter-model-ollama`'s direct `spring-ai-client-chat` etc.): attachment of that assumption to the **direct** `spring-ai-ollama` artifact was wrong, and is now replaced by the actual resolution above. Do not reintroduce the starter.

---

## 10. FINAL PROPOSAL CONSISTENCY

### Re-read of the Complete Proposal (all 11 sections)

The following were corrected in this repair pass (see §11 for line-level detail):

- **D8 preserved** — D8 direction unchanged
- **D40 unchanged** — D40 endpoint and loopback rules not weakened
- **Slice 2 only** — Design scope limited to Slice 2 authorization
- **Slice 3 separately gated** — Slice 3 requires its own D42+ authorization
- **No ChatClient/memory/RAG/MCP/tools in the actual runtime graph** — now **verified** by resolution (§9), not merely asserted
- **No evidence packet or EvidenceClaim** passed to the model in Slice 2
- **No public endpoint, persistence or execution**
- **No remote fallback**
- **No provider enabled by default** — `@ConditionalOnProperty(name = "ai.local.enabled", havingValue = "true", matchIfMissing = false)`
- **Retry:** the draft's self-contradiction (§3 table said `.retryTemplate(RetryTemplate)` exists; old §5/§11.1 claimed the builder does not expose it) is resolved — the setter **exists**, typed as `org.springframework.core.retry.RetryTemplate`, and the no-retry construction is the `RetryPolicy.maxRetries(0)` form in §5, with the Spring Retry `RetryTemplate.builder().maxAttempts(1)` explicitly out-of-scope.
- **64 KiB enforcement:** interceptor/`originalFactory`-undefined example replaced by the compiling `AbstractClientHttpRequest` wrapper pair in §4; prose and examples now match.
- **Proxy:** the old draft's `.proxy(ProxySelector.getDefault())` example (which would silently use the JVM default selector) is replaced by the exact-instance `.proxy(proxySelector)` chain in §6; prose and example now agree.
- **Digest:** old §8/§11.1 implied `/api/ps` existed on the 2.0.2 client — corrected in §8 (no such method; limitation preserved).
- **G-numbering:** the old draft mislabelled the dependency-tree blocker as "G-4" in §11.2 (G-4 is the resolved model-identity limitation). Fixed: G-5 = dependency verification (§9); G-4 = §8 residual limitation.
- **Unbuilt claims removed:** previous language implying verification from the BOM alone or an expected tree is replaced by the actual commands, outputs, and versions in §9.

### Additional Consistency Checks

- **Spring AI 2.0.2 source-level APIs** verified from `github.com/spring-projects/spring-ai` at `v2.0.2` **and** `javap` on resolved `2.0.2` jars
- **No nonexistent builder methods** (`connectTimeout()`, `readTimeout()`, `duration()` on `OllamaApi`/`OllamaChatModel`; `setConnectTimeout` on `JdkClientHttpRequestFactory`; `HttpClient.Builder.redirect(...)` — all replaced by jar-verified forms)
- **JDK HttpClient redirect API:** `followRedirects(HttpClient.Redirect.NEVER)` (§6)
- **64 KiB enforcement** via the bounded response stream (§4), not post-buffer check; verified by scratch execution
- **Proxy isolation** via the exact `ProxySelector` instance; `NO_PROXY`/environment as secondary, not primary boundary
- **Timeout/cancellation semantics**: 5s connect via `HttpClient.Builder.connectTimeout` / 30s read via `JdkClientHttpRequestFactory.setReadTimeout` / 35s overall; permit release on operation termination only
- **Retry behavior**: exactly one inference attempt; no retry, backoff, or remote fallback; `ModelManagementOptions.maxRetries` kept separate
- **Digest verification**: installed-model (`/api/tags`) only on the 2.0.2 client surface; no per-request cryptographic proof
- **Dependency-tree verification**: actual `mvn dependency:tree`/`dependency:list`/effective-POM output (§9) — not BOM-only reasoning

---

## 11. FINAL REPORT

### G-1 through G-5 status matrix

| Blocker | Status after repair | Evidence |
|---------|---------------------|----------|
| **G-1 bounded stream** | ✅ Corrected and **scratch-verified** (compiles + executes) | §4 rewritten; external scratch `mvn compile` BUILD SUCCESS + 16 runtime checks ALL PASS (boundary table in §4); no files inside the repo |
| **G-2 retry API** | ✅ Corrected and **artifact-verified** | §5: A/B distinction made explicit; setter existence jar-verified (`javap` on `spring-ai-ollama-2.0.2.jar`); `RetryTemplate` core construction jar-verified; Spring Retry confirmed absent from graph (§9) |
| **G-3 proxy/redirect** | ✅ Corrected at design level; implementation tests specified, not yet executed (await adoption + implementation) | §6: exact-instance chain, `Proxy.NO_PROXY`, no `getDefault()`, `followRedirects(NEVER)` compile-verified, WebClient accounted, namespace rule; 4 test families specified (§6/§11.3) |
| **G-4 model identity** | ✅ Resolved at design level (accepted residual limitation) — **not** reopened | §8 preserved; `/api/ps` correction made without overstating any guarantee |
| **G-5 dependencies** | ✅ **Verified** by actual Maven resolution in an isolated project | §9: `dependency:tree`, `dependency:list`, effective-POM all BUILD SUCCESS; exact versions and absence findings recorded |

### Exact corrected sections

- §3 — retry-setter row + `OllamaApi` dual-transport fact; timeout-API caveats.
- §4 — complete replacement of the `BoundedCountingClientHttpResponse` + `BoundedInputStream` examples, the `originalFactory`-undefined factory, and all Key Design Requirements; verification-case table updated with actual scratch results.
- §5 — full rewrite (A/B distinction, correct no-retry construction, `maxRetries(0)` semantics, `ModelManagementOptions` separation, §3 line-92 contradiction resolved).
- §6 — full rewrite (exact-instance `HttpClient` chain, `followRedirects(NEVER)`, JDK API name corrected, WebClient accounting, namespace rule, specified tests). The `.proxy(ProxySelector.getDefault())` example is removed.
- §7 — timeout table corrected to jar-verified APIs (`connectTimeout` on `HttpClient`, `setReadTimeout` on the factory).
- §8 — `/api/ps` correction; residual limitation wording preserved verbatim in substance.
- §9 — new section replacing the assumed starter graph with actual resolution results.
- §10 — all 11 sections re-checked; contradictions and mislabellings catalogued.
- §11 — this report (G-5 / G-4 labels fixed; old §11.1/§11.2 duplicated heading and the second "G-4" dependency label removed).

### Scratch-project compilation/test results (G-1)

External scratch `C:\Users\Asus\AppData\Local\Temp\opencode\scratch-s2s2` (`mvn compile` **BUILD SUCCESS** against `spring-web 7.0.9` + `spring-core 7.0.9`; `mvn exec:java "-Dexec.mainClass=scratch.Main"`):

```
0 bytes: PASS
65535 bytes: PASS
65536 exact EOF: PASS
65537 rejected: PASS
single-byte: PASS
skip: PASS
skip overflow rejected: PASS
readAllBytes overflow rejected: PASS
readAllBytes exact: PASS
early close ok: PASS
io failure preserved: PASS
no-retry template constructed: PASS
proxy NO_PROXY: PASS
httpClient built: PASS
delegate saw body={"hello":1} method=POST uri=http://127.0.0.1:11434/api/chat
factory forwards body + wraps response: PASS
ALL PASS
```

No files were created inside the Systivex repository for this verification. The corrected classes were compiled only in the external scratch project.

### Maven dependency result (G-5)

| Check | Result |
|-------|--------|
| `mvn dependency:tree -Dverbose` | **BUILD SUCCESS** (`full-tree.txt`); graph recorded in §9 |
| `mvn dependency:list` | **BUILD SUCCESS** (`dep-list.txt`); exact versions in §9 |
| `mvn org.apache.maven.plugins:maven-help-plugin:3.5.1:effective-pom "-Doutput=effective-pom.xml"` | **BUILD SUCCESS**; `effective-pom.xml` written |
| Unwanted modules | **None of the feared modules are present** (see §9 absence list) |
| `spring-retry` | **Absent** — confirms §5 |
| Repository `pom.xml` | **Unmodified** |

### Remaining adoption blockers

At design level, none of G-1…G-5 remain open. The process steps that remain are structural, not technical repairs:

1. **Independent re-review** of this repaired D41 (this pass cannot self-adopt).
2. **Authorized adoption** through the DECISIONS.md process (this task grants no adoption, no implementation authorization, no commit, no PR).
3. **Implementation-time test execution** for §6/§7 (§11.3): proxy-bypass, redirect-rejection, transport-identity, outbound-path, deterministic Content-Length/chunked mock tests — these require the (still unauthorized) implementation and cannot be executed in a design-only proposal.
4. **`/api/ps` question** (§8): Slice 2 proceeds on `/api/tags` + `/api/show` only; any future `/api/ps` addition requires the same bounded transport and its own verification.

### Final verdict

> **ADOPTABLE at design level.** The four genuine blockers from the independent NOT ADOPTABLE verdict (G-1, G-2, G-3, G-5) are technically repaired and scratch-verified; G-4 is preserved as an accepted residual limitation. **D41 remains DRAFT and is NOT ADOPTED by this task**; adoption requires independent re-review and an authorized DECISIONS.md process. No implementation is authorized by this repair.

### §11.1 Exact Spring AI Source APIs (from v2.0.2)

**OllamaApi.Builder:**
- `baseUrl(String)` ✅
- `restClientBuilder(RestClient.Builder)` ✅ — Slice 2 injection point (§4/§6)
- `webClientBuilder(WebClient.Builder)` ✅ — explicitly configured (§6); the `OllamaApi` constructor builds **both** a `RestClient` and a `WebClient`
- `responseErrorHandler(ResponseErrorHandler)` ✅
- `connectTimeout(Duration)` ❌ nonexistent
- `readTimeout(Duration)` ❌ nonexistent
- `duration(Duration)` ❌ nonexistent

**OllamaChatModel.Builder:**
- `ollamaApi(OllamaApi)` ✅
- `options(OllamaChatOptions)` ✅
- `observationRegistry(ObservationRegistry)` ✅
- `modelManagementOptions(ModelManagementOptions)` ✅ — pull-strategy/retries for pulls only; **does not** replace inference-retry configuration
- `retryTemplate(org.springframework.core.retry.RetryTemplate)` ✅ — **the method exists** (jar-verified); it accepts the Spring Framework 7 core `RetryTemplate`
- `connectTimeout(Duration)` ❌ nonexistent
- `readTimeout(Duration)` ❌ nonexistent
- `duration(Duration)` ❌ nonexistent

### §11.2 Retry behavior and no-retry configuration (corrected)

- **OllamaChatModel.Builder defaults** to `RetryUtils.DEFAULT_RETRY_TEMPLATE` (exponential backoff, `maxRetries(10)`)
- **OllamaChatModel.internalCall()** invokes `RetryUtils.execute(this.retryTemplate, …)`
- **`ModelManagementOptions.maxRetries` does NOT disable inference retries** (Reactor backoff for pulls only; `PullModelStrategy.NEVER` + `maxRetries(0)` defaults)
- **D41 explicitly configures a no-retry `RetryTemplate`:** `new RetryTemplate(RetryPolicy.builder().maxRetries(0).build())`, i.e. **1 total attempt** (`total = 1 + maxRetries`)
- **`RetryTemplate.builder().maxAttempts(1).build()` is the old Spring Retry API and does NOT apply** — different type, artifact `spring-retry` absent from the graph (§9)
- **Acceptance:** A provider failure produces exactly one inference attempt. No retry, backoff, queue, or remote fallback.

### §11.3 Final dependency and security recommendation

- **Dependency:** `org.springframework.ai:spring-ai-ollama:2.0.2` (direct; **not** the starter), manual `OllamaApi`/`OllamaChatModel` construction, `@ConditionalOnProperty(name = "ai.local.enabled", havingValue = "true", matchIfMissing = false)`. No auto-configuration exclusions are needed (none are pulled). **Justification:** the direct artifact is the verified minimal provider scope (§9).
- **64 KiB enforcement:** `OllamaApi.Builder.restClientBuilder(RestClient.Builder)` → §4 bounded wrapper factory; enforcement during `read()`; oversized `Content-Length` pre-rejected; no buffering; synchronous path only.
- **Timeouts:** 5s `HttpClient.Builder.connectTimeout` / 30s `JdkClientHttpRequestFactory.setReadTimeout` / 35s composed; `followRedirects(NEVER)`.
- **Proxy isolation:** exact-instance `ProxySelector` returning `Proxy.NO_PROXY`; never `getDefault()`; `NO_PROXY=127.0.0.1` as secondary; shared loopback/network namespace deployment rule.
- **Specified tests (implementation-time):** 13 G-1 cases (§4 table, scratch-proven) + proxy-bypass, redirect-rejection, transport-identity, outbound-path families (§6) + deterministic Content-Length/chunked mock responses (§4) + curl-free JDK-server-free unit/mocked tests for one-attempt inference (§5).

### Integrity report

| Check | Status | Detail |
|-------|--------|--------|
| **main remains at `95f9e2e45975386d19fd068293aa60f6604917ac`** | ✅ Verified | `git rev-parse HEAD` confirmed (see final tool check) |
| **Only one proposal modified** | ✅ Verified | this file only; no canonical/governance docs touched |
| **All three proposals preserved** | ✅ Verified | `PHASE14_S1_PROPOSAL.md`, `PHASE14_S2_PROPOSAL.md`, `PHASE14_S2_SLICE2_PROPOSAL.md` |
| **S1 proposal SHA-256 unchanged** | ✅ Verified | `c45f9e648a00ab9c8433d4049f711e3f624b703e1b9b02f7d74e46fcb1e2df7d` (pre- and post-edit check) |
| **Nothing staged/committed/pushed** | ✅ Verified | no `git add`, commit, push, merge or PR performed |
| **No repository POM/config/code/tests changed** | ✅ Verified | scratch project lives outside the repo |
| **D41 not adopted** | ✅ Verified | D41 remains draft-only; DECISIONS.md untouched |

---

*End of Final Technical Refinement and Report — DRAFT D41 NOT ADOPTED*
