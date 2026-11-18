package com.soubhagya.systivex.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soubhagya.systivex.order.api.InvalidIdempotencyKeyException;
import com.soubhagya.systivex.order.api.OrderRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Unit level guarantees of the idempotency primitives: the digests are
 * stable and collision resistant in the ways that matter, the canonical
 * payload does not depend on JSON spelling, and the key policy rejects what
 * cannot identify a checkout.
 */
class IdempotencyFingerprintTest {

    private OrderRequest request() {
        return new OrderRequest("SKU-1001", 1, "CUST-1001", new BigDecimal("1499.00"));
    }

    @Test
    void keyDigestIsStableAcrossCalls() {
        assertThat(IdempotencyFingerprint.keyHash("abc"))
                .isEqualTo(IdempotencyFingerprint.keyHash("abc"));
    }

    @Test
    void keyDigestIsHexEncodedSha256() {
        assertThat(IdempotencyFingerprint.keyHash("abc")).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void differentKeysProduceDifferentDigests() {
        assertThat(IdempotencyFingerprint.keyHash("abc"))
                .isNotEqualTo(IdempotencyFingerprint.keyHash("abd"));
    }

    @Test
    void keyDigestAndRequestDigestUseDifferentDomains() {
        assertThat(IdempotencyFingerprint.keyHash("abc"))
                .isNotEqualTo(IdempotencyFingerprint.requestHash(request()));
    }

    @Test
    void requestDigestIsStableAcrossCalls() {
        assertThat(IdempotencyFingerprint.requestHash(request()))
                .isEqualTo(IdempotencyFingerprint.requestHash(request()));
    }

    @Test
    void equivalentAmountSpellingsShareOneFingerprint() {
        OrderRequest scaled = new OrderRequest("SKU-1001", 1, "CUST-1001", new BigDecimal("1499"));
        OrderRequest plain = new OrderRequest("SKU-1001", 1, "CUST-1001", new BigDecimal("1499.00"));

        assertThat(IdempotencyFingerprint.requestHash(scaled))
                .isEqualTo(IdempotencyFingerprint.requestHash(plain));
    }

    @Test
    void everyFieldChangeChangesTheFingerprint() {
        String baseline = IdempotencyFingerprint.requestHash(request());

        assertThat(
                        IdempotencyFingerprint.requestHash(
                                new OrderRequest(
                                        "SKU-1002", 1, "CUST-1001", new BigDecimal("1499.00"))))
                .isNotEqualTo(baseline);
        assertThat(
                        IdempotencyFingerprint.requestHash(
                                new OrderRequest(
                                        "SKU-1001", 2, "CUST-1001", new BigDecimal("1499.00"))))
                .isNotEqualTo(baseline);
        assertThat(
                        IdempotencyFingerprint.requestHash(
                                new OrderRequest(
                                        "SKU-1001", 1, "CUST-1002", new BigDecimal("1499.00"))))
                .isNotEqualTo(baseline);
        assertThat(
                        IdempotencyFingerprint.requestHash(
                                new OrderRequest(
                                        "SKU-1001", 1, "CUST-1001", new BigDecimal("1499.01"))))
                .isNotEqualTo(baseline);
    }

    @Test
    void canonicalPayloadSeparatesFieldsByLength() {
        // Without the length prefix these two payloads would canonicalize to
        // the same text.
        OrderRequest first = new OrderRequest("AB", 1, "C", new BigDecimal("1.00"));
        OrderRequest second = new OrderRequest("A", 1, "BC", new BigDecimal("1.00"));

        assertThat(IdempotencyFingerprint.canonicalRequest(first))
                .isNotEqualTo(IdempotencyFingerprint.canonicalRequest(second));
    }

    @Test
    void canonicalPayloadKeepsSeparatorsInsideValuesHarmless() {
        OrderRequest first = new OrderRequest("A\nB", 1, "C", new BigDecimal("1.00"));
        OrderRequest second = new OrderRequest("A", 1, "B\nC", new BigDecimal("1.00"));

        assertThat(IdempotencyFingerprint.canonicalRequest(first))
                .isNotEqualTo(IdempotencyFingerprint.canonicalRequest(second));
    }

    @Test
    void ordinarySurroundingSpacesAreAcceptedAndNormalized() {
        assertThat(IdempotencyKeyPolicy.requireValid("  abc  ")).isEqualTo("abc");
        assertThat(IdempotencyKeyPolicy.requireValid("   abc   ")).isEqualTo("abc");
    }

    @Test
    void blankKeyIsRejected() {
        assertThatThrownBy(() -> IdempotencyKeyPolicy.requireValid(""))
                .isInstanceOf(InvalidIdempotencyKeyException.class);
        assertThatThrownBy(() -> IdempotencyKeyPolicy.requireValid("   "))
                .isInstanceOf(InvalidIdempotencyKeyException.class);
        assertThatThrownBy(() -> IdempotencyKeyPolicy.requireValid(null))
                .isInstanceOf(InvalidIdempotencyKeyException.class);
    }

    @Test
    void oversizedKeyIsRejectedAfterTrimming() {
        assertThatThrownBy(() -> IdempotencyKeyPolicy.requireValid("k".repeat(256)))
                .isInstanceOf(InvalidIdempotencyKeyException.class);
        assertThat(IdempotencyKeyPolicy.requireValid("k".repeat(255))).hasSize(255);
        assertThat(IdempotencyKeyPolicy.requireValid(" " + "k".repeat(255) + " ")).hasSize(255);
        assertThatThrownBy(() -> IdempotencyKeyPolicy.requireValid(" " + "k".repeat(256) + " "))
                .isInstanceOf(InvalidIdempotencyKeyException.class);
    }

    @Test
    void leadingAndTrailingTabsAreRejected() {
        assertThatThrownBy(() -> IdempotencyKeyPolicy.requireValid("\tabc"))
                .isInstanceOf(InvalidIdempotencyKeyException.class)
                .hasMessage("Idempotency-Key header must not contain control characters");
        assertThatThrownBy(() -> IdempotencyKeyPolicy.requireValid("abc\t"))
                .isInstanceOf(InvalidIdempotencyKeyException.class)
                .hasMessage("Idempotency-Key header must not contain control characters");
    }

    @Test
    void leadingAndTrailingCrLfAreRejected() {
        assertThatThrownBy(() -> IdempotencyKeyPolicy.requireValid("\r\nabc"))
                .isInstanceOf(InvalidIdempotencyKeyException.class)
                .hasMessage("Idempotency-Key header must not contain control characters");
        assertThatThrownBy(() -> IdempotencyKeyPolicy.requireValid("abc\r\n"))
                .isInstanceOf(InvalidIdempotencyKeyException.class)
                .hasMessage("Idempotency-Key header must not contain control characters");
    }

    @Test
    void nulAndDelAreRejected() {
        assertThatThrownBy(() -> IdempotencyKeyPolicy.requireValid("abc" + (char) 0))
                .isInstanceOf(InvalidIdempotencyKeyException.class)
                .hasMessage("Idempotency-Key header must not contain control characters");
        assertThatThrownBy(() -> IdempotencyKeyPolicy.requireValid((char) 0 + "abc"))
                .isInstanceOf(InvalidIdempotencyKeyException.class)
                .hasMessage("Idempotency-Key header must not contain control characters");
        assertThatThrownBy(() -> IdempotencyKeyPolicy.requireValid("abc" + (char) 0x7F))
                .isInstanceOf(InvalidIdempotencyKeyException.class)
                .hasMessage("Idempotency-Key header must not contain control characters");
        assertThatThrownBy(() -> IdempotencyKeyPolicy.requireValid((char) 0x7F + "abc"))
                .isInstanceOf(InvalidIdempotencyKeyException.class)
                .hasMessage("Idempotency-Key header must not contain control characters");
    }

    @Test
    void c1ControlsAreRejectedAtBeginningMiddleAndEnd() {
        char nextLine = (char) 0x85;
        char applicationProgramCommand = (char) 0x9F;

        assertThatThrownBy(() -> IdempotencyKeyPolicy.requireValid(nextLine + "abc"))
                .isInstanceOf(InvalidIdempotencyKeyException.class)
                .hasMessage("Idempotency-Key header must not contain control characters");
        assertThatThrownBy(() -> IdempotencyKeyPolicy.requireValid("ab" + nextLine + "c"))
                .isInstanceOf(InvalidIdempotencyKeyException.class)
                .hasMessage("Idempotency-Key header must not contain control characters");
        assertThatThrownBy(() -> IdempotencyKeyPolicy.requireValid("abc" + nextLine))
                .isInstanceOf(InvalidIdempotencyKeyException.class)
                .hasMessage("Idempotency-Key header must not contain control characters");
        assertThatThrownBy(() -> IdempotencyKeyPolicy.requireValid(applicationProgramCommand + "abc"))
                .isInstanceOf(InvalidIdempotencyKeyException.class)
                .hasMessage("Idempotency-Key header must not contain control characters");
        assertThatThrownBy(
                        () ->
                                IdempotencyKeyPolicy.requireValid(
                                        "ab" + applicationProgramCommand + "c"))
                .isInstanceOf(InvalidIdempotencyKeyException.class)
                .hasMessage("Idempotency-Key header must not contain control characters");
        assertThatThrownBy(
                        () ->
                                IdempotencyKeyPolicy.requireValid(
                                        "abc" + applicationProgramCommand))
                .isInstanceOf(InvalidIdempotencyKeyException.class)
                .hasMessage("Idempotency-Key header must not contain control characters");
    }

    @Test
    void controlCharactersInTheMiddleAreRejected() {
        assertThatThrownBy(() -> IdempotencyKeyPolicy.requireValid("abc\r\nX-Injected: 1"))
                .isInstanceOf(InvalidIdempotencyKeyException.class);
    }

    @Test
    void rejectionMessagesNeverEchoTheKey() {
        String secret = "super-secret-key-value";
        assertThatThrownBy(() -> IdempotencyKeyPolicy.requireValid(secret + "\r\nX-Injected: 1"))
                .isInstanceOf(InvalidIdempotencyKeyException.class)
                .hasMessageNotContaining(secret);
        assertThatThrownBy(() -> IdempotencyKeyPolicy.requireValid("\t" + secret))
                .isInstanceOf(InvalidIdempotencyKeyException.class)
                .hasMessageNotContaining(secret);
        assertThatThrownBy(() -> IdempotencyKeyPolicy.requireValid(secret + (char) 0))
                .isInstanceOf(InvalidIdempotencyKeyException.class)
                .hasMessageNotContaining(secret);
        assertThatThrownBy(() -> IdempotencyKeyPolicy.requireValid(secret + (char) 0x7F))
                .isInstanceOf(InvalidIdempotencyKeyException.class)
                .hasMessageNotContaining(secret);
    }
}
