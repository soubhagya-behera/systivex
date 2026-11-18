package com.soubhagya.systivex.order;

import com.soubhagya.systivex.order.api.OrderRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Deterministic digests for the idempotency contract.
 *
 * <p>Two digests are needed and both are one-way:
 *
 * <ul>
 *   <li>the <em>key digest</em> identifies the checkout attempt. Only the
 *       digest is persisted, so the client supplied key never reaches the
 *       database, a log line, or a metric label;
 *   <li>the <em>request digest</em> fingerprints the payload that was
 *       accepted under that key, so "same key, different payload" can be
 *       detected without storing the payload.
 * </ul>
 *
 * <p>The request fingerprint is canonical: field order is fixed, every
 * variable-length field is length-prefixed so no value can imitate a
 * separator, and the amount is normalized so {@code 1499.00} and {@code 1499}
 * are the same business payload. A client that retries with a different JSON
 * spelling of the same numbers therefore still hits its original checkout.
 */
public final class IdempotencyFingerprint {

    /** Domain separation, so a key digest can never collide with a request digest. */
    private static final String KEY_DOMAIN = "systivex.checkout.idempotency-key.v1";

    private static final String REQUEST_DOMAIN = "systivex.checkout.request.v1";

    private IdempotencyFingerprint() {}

    /** SHA-256 hex digest of the client supplied key. */
    public static String keyHash(String key) {
        return sha256Hex(KEY_DOMAIN + "\n" + key);
    }

    /** SHA-256 hex digest of the canonical request payload. */
    public static String requestHash(OrderRequest request) {
        return sha256Hex(REQUEST_DOMAIN + "\n" + canonicalRequest(request));
    }

    /**
     * Canonical text form of a checkout payload. Exposed for tests: the
     * fingerprint must be stable across processes and restarts, so it is
     * asserted directly rather than only through its digest.
     */
    public static String canonicalRequest(OrderRequest request) {
        StringBuilder canonical = new StringBuilder(128);
        appendField(canonical, request.productId());
        canonical.append('\n').append(request.quantity()).append('\n');
        appendField(canonical, request.customerId());
        canonical.append('\n').append(canonicalAmount(request.amount()));
        return canonical.toString();
    }

    private static void appendField(StringBuilder out, String value) {
        if (value == null) {
            out.append("-1:");
            return;
        }
        // Length prefix keeps the field boundary unambiguous, whatever the
        // value contains.
        out.append(value.length()).append(':').append(value);
    }

    private static String canonicalAmount(BigDecimal amount) {
        if (amount == null) {
            return "-1:";
        }
        return amount.stripTrailingZeros().toPlainString();
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required by the Java platform; this cannot happen.
            throw new IllegalStateException("SHA-256 digest unavailable", e);
        }
    }
}
