package com.soubhagya.systivex.intelligence;

/**
 * Shared execution limits for change-simulation requests.
 *
 * <p>The 100-operation maximum is defined once here so the public simulation
 * and policy endpoints and the governed evidence-packet path cannot drift
 * apart: a request that is too large to simulate is too large everywhere.
 */
public final class SimulationLimits {

    /** Maximum number of operations a single simulation request may carry. */
    public static final int MAX_OPERATIONS = 100;

    private SimulationLimits() {}
}
