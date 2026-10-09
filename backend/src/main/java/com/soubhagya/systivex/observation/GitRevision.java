package com.soubhagya.systivex.observation;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Best-effort reader for the scanned repository revision. Returns the short
 * commit hash when the root lives inside a Git checkout that answers;
 * otherwise empty (the sync result simply omits the revision). Never
 * throws, never runs anything from inside the scanned tree — only the
 * system {@code git} binary against the enclosing checkout.
 */
final class GitRevision {

    private static final Pattern SHORT_HEAD = Pattern.compile("^[0-9a-f]{4,40}$");

    private GitRevision() {}

    static Optional<String> readShortHead(Path root) {
        try {
            Path checkout = enclosingCheckout(root.toAbsolutePath().normalize());
            if (checkout == null) {
                return Optional.empty();
            }
            Process process =
                    new ProcessBuilder("git", "rev-parse", "--short", "HEAD")
                            .directory(checkout.toFile())
                            .redirectErrorStream(true)
                            .start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return Optional.empty();
            }
            String out = new String(process.getInputStream().readAllBytes()).strip();
            if (process.exitValue() == 0 && SHORT_HEAD.matcher(out).matches()) {
                return Optional.of(out);
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static Path enclosingCheckout(Path dir) {
        Path current = dir;
        for (int level = 0; level < 4 && current != null; level++) {
            if (java.nio.file.Files.isDirectory(current.resolve(".git"))) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }
}
