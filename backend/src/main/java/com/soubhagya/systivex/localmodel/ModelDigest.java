package com.soubhagya.systivex.localmodel;

import java.util.regex.Pattern;

public final class ModelDigest {

    private static final Pattern OLLAMA_SHA256_DIGEST = Pattern.compile("sha256:[0-9a-f]{64}");

    private ModelDigest() {
    }

    public static void validate(String expectedDigest) {
        if (expectedDigest == null || expectedDigest.isBlank()) {
            throw new IllegalArgumentException("expectedDigest must not be blank");
        }
        if (!OLLAMA_SHA256_DIGEST.matcher(expectedDigest).matches()) {
            throw new IllegalArgumentException(
                    "expectedDigest must be an Ollama SHA-256 digest of the form "
                            + "sha256:<64 lowercase hex characters>");
        }
    }
}
