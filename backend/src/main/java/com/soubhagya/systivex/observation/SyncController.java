package com.soubhagya.systivex.observation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Explicit sync trigger for the Phase 4 repository connector. No watchers,
 * no schedulers, no remote fetching: an operator invokes this endpoint and
 * the connector reads the configured target repository once.
 *
 * <p>Localhost-only (see {@link LoopbackGuard} plus the loopback bind in
 * {@code application.example.properties}): this phase adds no
 * authentication system, so the mutating trigger refuses non-loopback
 * peers instead of serving the network.
 */
@RestController
@RequestMapping("/api/v1/twin")
public class SyncController {

    private final TwinSyncService syncService;
    private final LoopbackGuard guard;
    private final String configuredRoot;

    public SyncController(
            TwinSyncService syncService,
            LoopbackGuard guard,
            @Value("${systivex.observation.repository-root:}") String configuredRoot) {
        this.syncService = syncService;
        this.guard = guard;
        this.configuredRoot = configuredRoot == null ? "" : configuredRoot.trim();
    }

    /** Optional per-call override; validated exactly like the configured root. */
    public record SyncRequest(@Size(max = 1024) String repositoryRoot) {}

    @PostMapping("/sync")
    public SyncResult synchronize(
            @Valid @RequestBody(required = false) SyncRequest request, HttpServletRequest http) {
        guard.check(http);
        String override =
                request == null || request.repositoryRoot() == null
                        ? ""
                        : request.repositoryRoot().trim();
        return syncService.synchronize(resolveRoot(override.isEmpty() ? null : override));
    }

    /**
     * Repository root resolution: explicit request value wins, then the
     * configured property (or {@code SYSTIVEX_OBSERVATION_REPOSITORY_ROOT}
     * via relaxed binding), then local-development defaults relative to the
     * process working directory. Only the existence/readability checks live
     * here; all content validation lives in the scanner, before any write.
     */
    Path resolveRoot(String override) {
        if (override != null && !override.isBlank()) {
            return Paths.get(override);
        }
        if (!configuredRoot.isBlank()) {
            return Paths.get(configuredRoot);
        }
        Path workingDir = Paths.get("").toAbsolutePath();
        List<Path> candidates =
                List.of(workingDir.resolve("target-services"), workingDir.resolve("backend/target-services"));
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException(
                "Repository root is not configured and no target-services directory was found "
                        + "under the working directory; set systivex.observation.repository-root");
    }
}
