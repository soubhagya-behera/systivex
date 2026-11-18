package com.soubhagya.systivex.order;

import com.soubhagya.systivex.order.api.InvalidIdempotencyKeyException;

/**
 * The accepted shape of a client supplied Idempotency-Key.
 *
 * <p>The key is an opaque client-chosen token, so the only rules are the ones
 * that keep it safe to store, hash and compare: not blank, bounded length,
 * and no control characters (a newline in a key would be a header-smuggling
 * hazard, not a key). The value is trimmed, so surrounding whitespace from
 * an intermediary does not fork the key into two different keys.
 *
 * <p>Rejection is loud on purpose. A client that sends an unusable key gets
 * a 400 instead of silently falling back to a non-idempotent checkout, which
 * is exactly the duplicate-charge situation this feature exists to prevent.
 * The supplied value never appears in the error message.
 */
public final class IdempotencyKeyPolicy {

    /** Long enough for any UUID, ULID or prefixed token. */
    public static final int MAX_LENGTH = 255;

    private IdempotencyKeyPolicy() {}

    /**
     * Validates the raw header value and returns the normalized key.
     *
     * @throws InvalidIdempotencyKeyException when the value cannot identify a
     *     checkout attempt
     */
    public static String requireValid(String rawKey) {
        if (rawKey == null) {
            throw new InvalidIdempotencyKeyException("Idempotency-Key header must not be blank");
        }
        for (int index = 0; index < rawKey.length(); index++) {
            char character = rawKey.charAt(index);
            if (Character.isISOControl(character)) {
                throw new InvalidIdempotencyKeyException(
                        "Idempotency-Key header must not contain control characters");
            }
        }
        String key = rawKey.trim();
        if (key.isEmpty()) {
            throw new InvalidIdempotencyKeyException("Idempotency-Key header must not be blank");
        }
        if (key.length() > MAX_LENGTH) {
            throw new InvalidIdempotencyKeyException(
                    "Idempotency-Key header must not exceed " + MAX_LENGTH + " characters");
        }
        return key;
    }
}
