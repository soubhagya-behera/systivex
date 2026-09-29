package com.soubhagya.systivex.inventory.api;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deterministic, stateless reservation endpoint for Phase 2A. There is no
 * database yet: any request for more than {@value #DEMO_STOCK_LIMIT} units is
 * rejected so callers can exercise the failure path. Persistent stock
 * arrives in Phase 2B.
 */
@RestController
@RequestMapping("/internal/v1/inventory")
public class InventoryController {

    static final int DEMO_STOCK_LIMIT = 5;

    @PostMapping("/reserve")
    @ResponseStatus(HttpStatus.CREATED)
    public ReserveResponse reserve(@Valid @RequestBody ReserveRequest request) {
        if (request.quantity() > DEMO_STOCK_LIMIT) {
            throw new ReservationRejectedException(
                    "Insufficient stock for product "
                            + request.productId()
                            + ": requested "
                            + request.quantity()
                            + ", at most "
                            + DEMO_STOCK_LIMIT
                            + " available in the Phase 2A demo catalog");
        }
        return new ReserveResponse(
                UUID.randomUUID(), request.productId(), request.quantity(), "RESERVED");
    }
}
