package com.soubhagya.systivex.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.soubhagya.systivex.inventory.api.ReservationRejectedException;
import com.soubhagya.systivex.inventory.api.ReserveRequest;
import com.soubhagya.systivex.inventory.api.ReserveResponse;
import com.soubhagya.systivex.inventory.model.InventoryItem;
import com.soubhagya.systivex.inventory.repository.InventoryItemRepository;
import com.soubhagya.systivex.inventory.repository.InventoryReservationRepository;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Two concurrent reserves racing for the same stock: with 10 units and two
 * requests for 6, exactly one must win and the other must be rejected — stock
 * must end at 4, never negative, never double-spent. The row-level write lock
 * in the reserve path is what serializes them; no Redis, no external
 * coordinator.
 */
@SpringBootTest
class InventoryConcurrentReservationTest extends AbstractPostgresIntegrationTest {

    @Autowired private InventoryReservationService service;

    @Autowired private InventoryItemRepository items;

    @Autowired private InventoryReservationRepository reservations;

    @BeforeEach
    void resetStock() {
        reservations.deleteAll();
        items.deleteAll();
        items.save(new InventoryItem("SKU-1001", 10));
    }

    @Test
    void concurrentReservesDoNotOversell() throws Exception {
        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<ReserveResponse> successes = new CopyOnWriteArrayList<>();
        List<Exception> failures = new CopyOnWriteArrayList<>();
        try {
            Future<?> first = pool.submit(() -> attempt(ready, go, successes, failures));
            Future<?> second = pool.submit(() -> attempt(ready, go, successes, failures));
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(successes).hasSize(1);
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0)).isInstanceOf(ReservationRejectedException.class);
        assertThat(items.findById("SKU-1001").orElseThrow().getAvailableQuantity())
                .isEqualTo(4);
    }

    private void attempt(
            CountDownLatch ready,
            CountDownLatch go,
            List<ReserveResponse> successes,
            List<Exception> failures) {
        ready.countDown();
        try {
            if (!go.await(30, TimeUnit.SECONDS)) {
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        try {
            successes.add(service.reserve(new ReserveRequest("SKU-1001", 6)));
        } catch (Exception e) {
            failures.add(e);
        }
    }
}
