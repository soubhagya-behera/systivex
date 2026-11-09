package com.soubhagya.systivex.evidence;

import java.util.List;

/**
 * Deterministic source adapter contract. Adapters map already-loaded,
 * allowlisted source data into evidence items. They do not load data,
 * perform I/O, or mutate their inputs.
 */
public interface EvidenceSource<T> {
    List<EvidenceItem<?>> adapt(T source);
}
