package com.soubhagya.systivex.localmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.AbstractClientHttpRequest;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;

class BoundedResponseTransportTest {

    private static InputStream stream(int size) {
        byte[] bytes = new byte[size];
        Arrays.fill(bytes, (byte) 'a');
        return new ByteArrayInputStream(bytes);
    }

    private static long drain(InputStream input) throws IOException {
        long count = 0;
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            count += read;
        }
        return count;
    }

    @Test
    void zeroBytesAccepted() throws IOException {
        assertEquals(0, drain(new LocalModelTransport.BoundedInputStream(stream(0), 65_536)));
    }

    @Test
    void sixtyFiveThousandFiveHundredThirtyFiveAccepted() throws IOException {
        assertEquals(65_535, drain(new LocalModelTransport.BoundedInputStream(stream(65_535), 65_536)));
    }

    @Test
    void exactLimitWithEofAccepted() throws IOException {
        assertEquals(65_536, drain(new LocalModelTransport.BoundedInputStream(stream(65_536), 65_536)));
    }

    @Test
    void overflowByteRejected() {
        var input = new LocalModelTransport.BoundedInputStream(stream(65_537), 65_536);
        assertThrows(LocalModelTransport.ResponseTooLargeException.class, () -> drain(input));
    }

    @Test
    void unknownLengthChunkedOverflowRejected() {
        InputStream trickle = new InputStream() {
            private int produced = 0;

            @Override
            public int read() {
                if (produced >= 70_000) {
                    return -1;
                }
                produced++;
                return 'x';
            }
        };
        var input = new LocalModelTransport.BoundedInputStream(trickle, 65_536);
        assertThrows(LocalModelTransport.ResponseTooLargeException.class, () -> {
            byte[] buffer = new byte[4096];
            while (true) {
                if (input.read(buffer) == -1) {
                    break;
                }
            }
        });
    }

    @Test
    void missingContentLengthStillEnforced() {
        ClientHttpResponse response = responseWithoutDeclaredLength(stream(65_537));
        var counted = new LocalModelTransport.BoundedCountingClientHttpResponse(response);
        assertThrows(LocalModelTransport.ResponseTooLargeException.class, () -> {
            counted.getBody().readAllBytes();
        });
    }

    @Test
    void misleadingContentLengthStillEnforced() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentLength(10);
        var counted = new LocalModelTransport.BoundedCountingClientHttpResponse(
                responseWithHeaders(headers, stream(65_537)));
        assertThrows(LocalModelTransport.ResponseTooLargeException.class, () -> {
            counted.getBody().readAllBytes();
        });
    }

    @Test
    void oversizedDeclaredContentLengthRejectedBeforeBody() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentLength(65_537);
        var counted = new LocalModelTransport.BoundedCountingClientHttpResponse(
                responseWithHeaders(headers, stream(10)));
        assertThrows(LocalModelTransport.ResponseTooLargeException.class, counted::getBody);
    }

    @Test
    void singleByteReadsShareCounter() throws IOException {
        var input = new LocalModelTransport.BoundedInputStream(stream(3), 65_536);
        assertEquals('a', input.read());
        assertEquals('a', input.read());
        assertEquals('a', input.read());
        assertEquals(-1, input.read());
        byte[] nul = new byte[] {0, 0};
        assertEquals(0, new LocalModelTransport.BoundedInputStream(new ByteArrayInputStream(nul), 65_536).read());
    }

    @Test
    void bulkReadsShareCounter() throws IOException {
        var input = new LocalModelTransport.BoundedInputStream(stream(70_000), 65_536);
        byte[] buffer = new byte[65_536];
        assertThrows(LocalModelTransport.ResponseTooLargeException.class, () -> {
            input.read(buffer, 0, buffer.length);
            input.read();
        });
    }

    @Test
    void skipCannotBypassLimit() throws IOException {
        var input = new LocalModelTransport.BoundedInputStream(stream(100), 65_536);
        assertEquals(10, input.skip(10));
        var overflow = new LocalModelTransport.BoundedInputStream(stream(65_537), 65_536);
        assertThrows(LocalModelTransport.ResponseTooLargeException.class, () -> overflow.skip(65_537));
    }

    @Test
    void readAllBytesBounded() throws IOException {
        var exact = new LocalModelTransport.BoundedInputStream(stream(65_536), 65_536);
        assertEquals(65_536, exact.readAllBytes().length);
        var overflow = new LocalModelTransport.BoundedInputStream(stream(65_537), 65_536);
        assertThrows(LocalModelTransport.ResponseTooLargeException.class, overflow::readAllBytes);
    }

    @Test
    void transferToBounded() throws IOException {
        var overflow = new LocalModelTransport.BoundedInputStream(stream(65_537), 65_536);
        assertThrows(LocalModelTransport.ResponseTooLargeException.class,
                () -> overflow.transferTo(new ByteArrayOutputStream()));
        var exact = new LocalModelTransport.BoundedInputStream(stream(65_536), 65_536);
        var sink = new ByteArrayOutputStream();
        assertEquals(65_536, exact.transferTo(sink));
    }

    @Test
    void earlyCloseDelegates() throws IOException {
        var input = new LocalModelTransport.BoundedInputStream(stream(100), 65_536);
        input.close();
        assertTrue(input.markSupported() == false);
    }

    @Test
    void underlyingIoFailurePreserved() {
        InputStream failing = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("boom");
            }
        };
        IOException thrown = assertThrows(IOException.class,
                () -> new LocalModelTransport.BoundedInputStream(failing, 65_536).read());
        assertEquals("boom", thrown.getMessage());
    }

    @Test
    void factoryForwardsRequestBodyAndWrapsResponse() throws IOException {
        StringBuilder seenBody = new StringBuilder();
        ClientHttpRequestFactory delegate = (uri, method) -> new AbstractClientHttpRequest() {
            private final java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();

            @Override
            public HttpMethod getMethod() {
                return method;
            }

            @Override
            public URI getURI() {
                return uri;
            }

            @Override
            protected OutputStream getBodyInternal(HttpHeaders headers) {
                return body;
            }

            @Override
            protected ClientHttpResponse executeInternal(HttpHeaders headers) {
                seenBody.append(body.toString());
                return responseWithHeaders(new HttpHeaders(), stream(10));
            }
        };
        ClientHttpRequestFactory bounded = LocalModelTransport.boundedCountingRequestFactory(delegate);
        ClientHttpRequest request = bounded.createRequest(URI.create("http://127.0.0.1:11434/api/chat"), HttpMethod.POST);
        request.getBody().write("{\"hello\":1}".getBytes());
        ClientHttpResponse response = request.execute();
        assertEquals(10, drain(response.getBody()));
        response.close();
        assertEquals("{\"hello\":1}", seenBody.toString());
    }

    private static ClientHttpResponse responseWithoutDeclaredLength(InputStream body) {
        HttpHeaders headers = new HttpHeaders();
        headers.remove(HttpHeaders.CONTENT_LENGTH);
        return responseWithHeaders(headers, body);
    }

    private static ClientHttpResponse responseWithHeaders(HttpHeaders headers, InputStream body) {
        return new ClientHttpResponse() {
            @Override
            public HttpHeaders getHeaders() {
                return headers;
            }

            @Override
            public InputStream getBody() {
                return body;
            }

            @Override
            public HttpStatusCode getStatusCode() {
                return HttpStatus.OK;
            }

            @Override
            public String getStatusText() {
                return HttpStatus.OK.getReasonPhrase();
            }

            @Override
            public void close() {
            }
        };
    }

    private static ClientHttpResponse responseWithClosingBody(InputStream body, Runnable onClose) {
        return new ClientHttpResponse() {
            @Override
            public HttpHeaders getHeaders() {
                return new HttpHeaders();
            }

            @Override
            public InputStream getBody() {
                return body;
            }

            @Override
            public HttpStatusCode getStatusCode() {
                return HttpStatus.OK;
            }

            @Override
            public String getStatusText() {
                return HttpStatus.OK.getReasonPhrase();
            }

            @Override
            public void close() {
                onClose.run();
            }
        };
    }

    @Test
    void closeFailurePropagates() throws IOException {
        ClientHttpResponse response = responseWithClosingBody(new ByteArrayInputStream(new byte[0]), () -> {
            throw new RuntimeException("delegate close boom");
        });
        var counted = new LocalModelTransport.BoundedCountingClientHttpResponse(response);
        counted.getBody();
        RuntimeException thrown = assertThrows(RuntimeException.class, counted::close);
        assertEquals("delegate close boom", thrown.getMessage());
    }

    @Test
    void readFailureNotMaskedByCloseFailure() {
        InputStream failingBody = new InputStream() {
            private int produced = 0;

            @Override
            public int read() {
                if (produced >= 65_537) {
                    return -1;
                }
                produced++;
                return 'a';
            }

            @Override
            public void close() throws IOException {
                throw new IOException("body close boom");
            }
        };
        ClientHttpResponse response = responseWithClosingBody(failingBody, () -> {
        });
        Throwable thrown = null;
        try (var counted = new LocalModelTransport.BoundedCountingClientHttpResponse(response)) {
            counted.getBody().readAllBytes();
        } catch (Throwable t) {
            thrown = t;
        }
        assertNotNull(thrown);
        assertTrue(thrown instanceof LocalModelTransport.ResponseTooLargeException);
        assertTrue(thrown.getSuppressed().length >= 1);
    }
}
