package com.soubhagya.systivex.inventory.api;

import com.soubhagya.systivex.inventory.InventoryReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Persistent reservation endpoint for Phase 2B: requests are checked against
 * the stored stock in inventory_db (seeded with SKU-1001 for local demos).
 * Short stock and unknown products are rejected with 422; every attempt is
 * recorded.
 */
@RestController
@RequestMapping("/internal/v1/inventory")
public class InventoryController {

    private final InventoryReservationService reservations;

    public InventoryController(InventoryReservationService reservations) {
        this.reservations = reservations;
    }

    @PostMapping("/reserve")
    @ResponseStatus(HttpStatus.CREATED)
    public ReserveResponse reserve(@Valid @RequestBody ReserveRequest request) {
        return reservations.reserve(request);
    }
}
