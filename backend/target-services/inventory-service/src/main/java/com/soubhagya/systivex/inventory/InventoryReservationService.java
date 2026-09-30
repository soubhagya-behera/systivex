package com.soubhagya.systivex.inventory;

import com.soubhagya.systivex.inventory.api.ReservationRejectedException;
import com.soubhagya.systivex.inventory.api.ReserveRequest;
import com.soubhagya.systivex.inventory.api.ReserveResponse;
import com.soubhagya.systivex.inventory.model.InventoryItem;
import com.soubhagya.systivex.inventory.model.InventoryReservation;
import com.soubhagya.systivex.inventory.model.ReservationStatus;
import com.soubhagya.systivex.inventory.repository.InventoryItemRepository;
import com.soubhagya.systivex.inventory.repository.InventoryReservationRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistent reservation behind one local inventory-database transaction:
 * lock the stock row, check availability, decrement only on success, and
 * record the attempt either way. Unknown products and short stock both end
 * as REJECTED rows (422) — stock never goes negative (CHECK constraint backs
 * the application check) and concurrent reserves for the same product
 * serialize on the row lock instead of overwriting each other. The REJECTED
 * row must commit even though the method then throws, so the business
 * rejection is excluded from rollback.
 */
@Service
public class InventoryReservationService {

    private final InventoryItemRepository items;
    private final InventoryReservationRepository reservations;

    public InventoryReservationService(
            InventoryItemRepository items, InventoryReservationRepository reservations) {
        this.items = items;
        this.reservations = reservations;
    }

    @Transactional(noRollbackFor = ReservationRejectedException.class)
    public ReserveResponse reserve(ReserveRequest request) {
        UUID id = UUID.randomUUID();
        Optional<InventoryItem> locked = items.findLockedByProductId(request.productId());

        if (locked.isEmpty()) {
            reservations.save(
                    new InventoryReservation(
                            id, request.productId(), request.quantity(), ReservationStatus.REJECTED));
            throw new ReservationRejectedException(
                    "Unknown product " + request.productId() + ": no stock record exists");
        }

        InventoryItem item = locked.get();
        if (item.getAvailableQuantity() < request.quantity()) {
            reservations.save(
                    new InventoryReservation(
                            id, request.productId(), request.quantity(), ReservationStatus.REJECTED));
            throw new ReservationRejectedException(
                    "Insufficient stock for product "
                            + request.productId()
                            + ": requested "
                            + request.quantity()
                            + ", only "
                            + item.getAvailableQuantity()
                            + " available");
        }

        item.decrease(request.quantity());
        items.save(item);
        reservations.save(
                new InventoryReservation(
                        id, request.productId(), request.quantity(), ReservationStatus.RESERVED));
        return new ReserveResponse(id, request.productId(), request.quantity(), "RESERVED");
    }
}
