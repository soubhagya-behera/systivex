package com.soubhagya.systivex.order.api;

import com.soubhagya.systivex.order.CheckoutService;
import com.soubhagya.systivex.order.IdempotentCheckoutService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final CheckoutService checkoutService;
    private final IdempotentCheckoutService idempotentCheckoutService;

    public OrderController(
            CheckoutService checkoutService, IdempotentCheckoutService idempotentCheckoutService) {
        this.checkoutService = checkoutService;
        this.idempotentCheckoutService = idempotentCheckoutService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse checkout(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody OrderRequest request) {
        if (idempotencyKey == null) {
            // No key: unchanged behaviour, one attempt per request.
            // Generated up front so even failed attempts carry a traceable id.
            return checkoutService.checkout(UUID.randomUUID(), request);
        }
        // With a key the attempt is owned by that key: a repeat replays the
        // recorded outcome instead of creating a second order.
        return idempotentCheckoutService.checkout(idempotencyKey, request);
    }
}
