package org.micahcode.teamTP;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class BatchQueueTest {
    @Test
    void processesThreeHundredSlowTeleportsWithinBothLimits() {
        Map<Integer, CompletableFuture<Boolean>> active = new HashMap<>();
        List<Integer> started = new ArrayList<>();
        BatchQueue<Integer> queue = new BatchQueue<>(IntStream.range(0, 300).boxed().toList(), 4, 8, 1_000_000,
                player -> {
                    assertFalse(started.contains(player));
                    started.add(player);
                    CompletableFuture<Boolean> future = new CompletableFuture<>();
                    active.put(player, future);
                    return future;
                }, (player, error) -> fail("Unexpected failure"));
        for (int tick = 0; tick < 2000 && !queue.done(); tick++) {
            if (tick % 3 == 0) {
                active.values().forEach(future -> future.complete(true));
                active.clear();
            }
            int before = started.size();
            queue.tick(tick);
            assertTrue(started.size() - before <= 4);
            assertTrue(queue.inFlight() <= 8);
        }
        assertTrue(queue.done());
        assertEquals(300, queue.succeeded());
        assertEquals(300, started.size());
    }

    @Test
    void immediateCompletionsCannotExceedPerTickBudget() {
        AtomicInteger starts = new AtomicInteger();
        BatchQueue<Integer> queue = new BatchQueue<>(IntStream.range(0, 300).boxed().toList(), 4, 64, 1000,
                item -> { starts.incrementAndGet(); return CompletableFuture.completedFuture(true); }, (item, error) -> fail());
        queue.tick(0);
        assertEquals(4, starts.get());
        queue.tick(1);
        assertEquals(8, starts.get());
    }

    @Test
    void cancellationRetainsActivePermitsAndAccountsPendingWork() {
        CompletableFuture<Boolean> active = new CompletableFuture<>();
        BatchQueue<Integer> queue = new BatchQueue<>(List.of(1, 2, 3), 1, 1, 1000, item -> active, (item, error) -> fail());
        queue.tick(0);
        queue.cancel();
        assertEquals(2, queue.cancelled());
        assertFalse(queue.done());
        active.complete(true);
        queue.tick(1);
        assertTrue(queue.done());
        assertEquals(1, queue.succeeded());
    }

    @Test
    void timeoutNeverReleasesPermitsForUnfinishedTeleport() {
        CompletableFuture<Boolean> active = new CompletableFuture<>();
        BatchQueue<Integer> queue = new BatchQueue<>(List.of(1, 2, 3), 1, 1, 10, item -> active, (item, error) -> fail());
        queue.tick(0);
        queue.tick(11);
        assertTrue(queue.stalled());
        assertEquals(2, queue.cancelled());
        assertEquals(1, queue.inFlight());
        assertFalse(queue.done());
        queue.tick(1000);
        assertFalse(queue.done());
        active.complete(true);
        queue.tick(1001);
        assertTrue(queue.done());
    }

    @Test
    void handlesFalseResultsExceptionsAndSynchronousFailures() {
        AtomicInteger failures = new AtomicInteger();
        BatchQueue<Integer> queue = new BatchQueue<>(List.of(1, 2, 3, 4), 4, 4, 1000, item -> switch (item) {
            case 1 -> CompletableFuture.completedFuture(false);
            case 2 -> CompletableFuture.failedFuture(new IllegalStateException("Chunk failure"));
            case 3 -> throw new IllegalStateException("Player disconnected");
            default -> CompletableFuture.completedFuture(true);
        }, (item, error) -> failures.incrementAndGet());
        queue.tick(0);
        queue.tick(1);
        assertTrue(queue.done());
        assertEquals(3, queue.failed());
        assertEquals(1, queue.succeeded());
        assertEquals(3, failures.get());
    }

    @Test
    void backgroundCompletionIsAccountedOnNextTick() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        BatchQueue<Integer> queue = new BatchQueue<>(List.of(1), 1, 1, 1000, item -> future, (item, error) -> fail());
        queue.tick(0);
        CompletableFuture.runAsync(() -> future.complete(true)).join();
        assertEquals(0, queue.succeeded());
        queue.tick(1);
        assertEquals(1, queue.succeeded());
    }
}
