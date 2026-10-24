package org.micahcode.teamTP;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.*;

public final class BatchQueue<T> {
    private final Deque<T> pending;
    private final Map<T, Long> active = new HashMap<>();
    private final Queue<Runnable> completions = new ConcurrentLinkedQueue<>();
    private final Function<T, CompletableFuture<Boolean>> start;
    private final BiConsumer<T, Throwable> failure;
    private final int perTick;
    private final int concurrency;
    private final long timeoutNanos;
    private int succeeded;
    private int failed;
    private int cancelled;
    private boolean stalled;

    public BatchQueue(Collection<T> items, int perTick, int concurrency, long timeoutNanos,
                      Function<T, CompletableFuture<Boolean>> start, BiConsumer<T, Throwable> failure) {
        if (perTick < 1 || concurrency < 1 || timeoutNanos < 1) throw new IllegalArgumentException("Invalid queue limits.");
        if (new HashSet<>(items).size() != items.size()) throw new IllegalArgumentException("Duplicate work item.");
        pending = new ArrayDeque<>(items);
        this.perTick = perTick;
        this.concurrency = concurrency;
        this.timeoutNanos = timeoutNanos;
        this.start = start;
        this.failure = failure;
    }

    public void tick(long now) {
        Runnable completion;
        while ((completion = completions.poll()) != null) completion.run();
        if (active.values().stream().anyMatch(started -> now - started >= timeoutNanos)) {
            stalled = true;
            cancel();
        }
        for (int count = 0; count < perTick && active.size() < concurrency && !pending.isEmpty(); count++) {
            T item = pending.removeFirst();
            active.put(item, now);
            try {
                start.apply(item).whenComplete((success, error) -> completions.add(() -> finish(item, success, error)));
            } catch (Exception error) {
                completions.add(() -> finish(item, false, error));
            }
        }
    }

    private void finish(T item, Boolean success, Throwable error) {
        if (active.remove(item) == null) return;
        if (error == null && Boolean.TRUE.equals(success)) succeeded++;
        else {
            failed++;
            failure.accept(item, error);
        }
    }

    public void cancel() {
        cancelled += pending.size();
        pending.clear();
    }

    public boolean done() { return pending.isEmpty() && active.isEmpty(); }

    public int succeeded() { return succeeded; }

    public int failed() { return failed; }

    public int cancelled() { return cancelled; }

    public int queued() { return pending.size(); }

    public int inFlight() { return active.size(); }

    public boolean stalled() { return stalled; }
}
