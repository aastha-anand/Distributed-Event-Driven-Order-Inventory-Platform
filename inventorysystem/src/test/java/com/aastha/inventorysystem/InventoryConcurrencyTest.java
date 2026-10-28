package com.aastha.inventorysystem;

import com.aastha.inventorysystem.inventory.model.Inventory;
import com.aastha.inventorysystem.inventory.repository.InventoryRepository;
import com.aastha.inventorysystem.inventory.service.InventoryService;
import com.aastha.inventorysystem.product.service.ProductService;
import com.aastha.inventorysystem.product.dto.ProductRequest;
import com.aastha.inventorysystem.product.dto.ProductResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class InventoryConcurrencyTest {
    @Autowired
    private ProductService productService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Test
    void stockMustNeverBecomeNegative() throws Exception {
        int initialStock = 5;
        int requests = 20;

        ProductResponse product = productService.create(
                new ProductRequest(
                        "TEST-" + UUID.randomUUID(),
                        "Concurrency Test Product",
                        new BigDecimal("100.00"),
                        initialStock
                )
        );

        ExecutorService executor =
                Executors.newFixedThreadPool(requests);

        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();

        try {
            Future<?>[] futures = new Future<?>[requests];

            for (int i = 0; i < requests; i++) {
                futures[i] = executor.submit(() -> {
                    ready.countDown();

                    try {
                        if (!start.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException(
                                    "Timed out waiting to start");
                        }

                        inventoryService.reserveStock(
                                product.id(), 1,
                                "TEST-ORDER-" + UUID.randomUUID()
                        );

                        successes.incrementAndGet();
                    } catch (ResponseStatusException expected) {
                        // Insufficient stock: expected for some requests.
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                });
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }

            Inventory finalInventory =
                    inventoryRepository.findById(product.id())
                            .orElseThrow();

            assertEquals(initialStock, successes.get());
            assertEquals(5, finalInventory.getOnHand());
            assertEquals(5, finalInventory.getReserved());
            assertEquals(0, finalInventory.getAvailable());
            assertTrue(finalInventory.getOnHand() >= 0);
            assertTrue(finalInventory.getReserved()
                    <= finalInventory.getOnHand());

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

}
