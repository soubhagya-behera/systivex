package com.soubhagya.systivex.evidence;

import java.util.List;
import java.util.Locale;

public final class ClaimWordingScreen {

    private static final List<String> FORBIDDEN_PHRASES = List.of(
            "caused by",
            "causes the",
            "caused the",
            "is caused",
            "will break",
            "will not break",
            "breaks the",
            "is broken",
            "confirmed breakage",
            "is safe",
            "safe to execute",
            "safe to deploy",
            "safe to apply",
            "no risk",
            "fully verified",
            "complete coverage",
            "guaranteed",
            "proves that",
            "proof of",
            "chain verified",
            "chain is verified",
            "approved to execute",
            "authorized to",
            "permission to",
            "you should",
            "must execute",
            "approved for execution",
            "will execute",
            "was executed",
            "executed the change");

    private ClaimWordingScreen() {
    }

    public static boolean isForbidden(String statement) {
        if (statement == null || statement.isBlank()) {
            return false;
        }
        String lowered = statement.toLowerCase(Locale.ROOT);
        for (String phrase : FORBIDDEN_PHRASES) {
            if (lowered.contains(phrase)) {
                return true;
            }
        }
        return false;
    }
}
