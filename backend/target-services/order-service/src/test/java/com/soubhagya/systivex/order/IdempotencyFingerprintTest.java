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
    void validKeyIsAcceptedAndTrimmed() {
        assertThat(IdempotencyKeyPolicy.requireValid("  abc  ")).isEqualTo("abc");
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
    void oversizedKeyIsRejected() {
        assertThatThrownBy(() -> IdempotencyKeyPolicy.requireValid("k".repeat(256)))
                .isInstanceOf(InvalidIdempotencyKeyException.class);
        assertThat(IdempotencyKeyPolicy.requireValid("k".repeat(255))).hasSize(255);
    }

    @Test
    void controlCharactersAreRejected() {
        assertThatThrownBy(() -> IdempotencyKeyPolicy.requireValid("abc\r\nX-Injected: 1"))
                .isInstanceOf(InvalidIdempotencyKeyException.class);
    }

    @Test
    void rejectionMessagesNeverEchoTheKey() {
        String secret = "super-secret-key-value";
        assertThatThrownBy(() -> IdempotencyKeyPolicy.requireValid(secret + "\r\nX-Injected: 1"))
                .isInstanceOf(InvalidIdempotencyKeyException.class)
                .hasMessageNotContaining(secret);
    }
}
