package com.soubhagya.systivex.inventory.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** Explicit contract for stock reservation requests. */
public record ReserveRequest(
        @NotBlank String productId,
        @Min(1) int quantity) {}
