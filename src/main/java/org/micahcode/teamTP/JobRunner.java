package org.micahcode.teamTP;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;
import java.util.logging.Level;

public final class JobRunner implements AutoCloseable {
    private final JavaPlugin plugin;
    private final BukkitTask ticker;
    private BatchQueue<?> queue;
    private Runnable completion;
    private CommandSender sender;
    private Messages messages;
    private String name;
    private boolean cancellationRequested;
    private boolean stallReported;
    private int ticks;

    public JobRunner(JavaPlugin plugin) {
        this.plugin = plugin;
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    public <T> void start(String name, CommandSender sender, Messages messages, Settings settings,
                          Collection<T> items, int concurrency, Function<T, CompletableFuture<Boolean>> work,
                          Consumer<Boolean> finished) {
        if (busy()) throw new IllegalStateException("A job is already active.");
        this.name = name;
        this.sender = sender;
        this.messages = messages;
        cancellationRequested = false;
        stallReported = false;
        ticks = 0;
        BatchQueue<T> started = new BatchQueue<>(items, settings.startsPerTick(), concurrency,
                settings.timeoutSeconds() * 1_000_000_000L, work, (item, error) -> {
                    if (error == null) plugin.getLogger().warning(name + " rejected/skipped: " + item);
                    else plugin.getLogger().log(Level.WARNING, name + " failed: " + item, error);
                });
        queue = started;
        completion = () -> finished.accept(!cancellationRequested && !started.stalled()
                && started.failed() == 0 && started.cancelled() == 0);
        messages.send(sender, "started", "job", name, "count", items.size());
    }

    private void tick() {
        if (queue == null) return;
        queue.tick(System.nanoTime());
        if (queue.stalled() && !stallReported) {
            stallReported = true;
            messages.send(sender, "stalled");
            plugin.getLogger().warning("Job stalled: " + name + ". Queued work stopped; waiting for real in-flight completion.");
        }
        if (++ticks % 200 == 0 && !queue.done()) status(sender);
        if (!queue.done()) return;
        messages.send(sender, "finished", "job", name, "success", queue.succeeded(), "failed", queue.failed(), "cancelled", queue.cancelled());
        plugin.getLogger().info(name + ": succeeded=" + queue.succeeded() + ", failed=" + queue.failed() + ", cancelled=" + queue.cancelled());
        Runnable callback = completion;
        queue = null;
        completion = null;
        callback.run();
    }

    public boolean busy() { return queue != null; }

    public void status(CommandSender recipient) {
        if (queue != null) messages.send(recipient, "status", "job", name, "queued", queue.queued(),
                "active", queue.inFlight(), "success", queue.succeeded(), "failed", queue.failed(), "cancelled", queue.cancelled());
    }

    public void cancel() {
        if (queue != null) {
            cancellationRequested = true;
            queue.cancel();
        }
    }

    @Override
    public void close() {
        cancel();
        ticker.cancel();
        if (queue != null) plugin.getLogger().warning("Disabled with " + queue.inFlight() + " requests in flight; those may still complete.");
        queue = null;
        completion = null;
    }
}
