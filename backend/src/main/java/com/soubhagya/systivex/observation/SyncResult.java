package com.soubhagya.systivex.observation;

import java.time.Instant;

/**
 * Outcome of one twin synchronization run. Counts are exact: every
 * discovered entity ends exactly one of created / updated / unchanged, and
 * every discovered edge ends created or unchanged (edges carry no mutable
 * state). {@code revision} is present only when the connector could
 * establish the scanned repository revision reliably.
 */
public record SyncResult(
        int entitiesCreated,
        int entitiesUpdated,
        int entitiesUnchanged,
        int relationshipsCreated,
        int relationshipsUnchanged,
        String repositoryRoot,
        String revision,
        Instant completedAt) {}
