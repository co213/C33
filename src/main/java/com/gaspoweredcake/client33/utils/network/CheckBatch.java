package com.gaspoweredcake.client33.utils.network;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;

/** Bounded concurrent checks. Each worker owns an item's retries; an empty queue never blocks. */
public final class CheckBatch {
    private CheckBatch() {}

    public static <T> void run(List<T> items, int workers, int retries, Predicate<T> retry) throws InterruptedException {
        if (workers < 1 || retries < 0) throw new IllegalArgumentException("Invalid check limits");
        if (items.isEmpty()) return;
        var queue = new ConcurrentLinkedQueue<>(items);
        try (var executor = Executors.newFixedThreadPool(Math.min(workers, items.size()))) {
            List<Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < Math.min(workers, items.size()); i++) {
                tasks.add(executor.submit(() -> {
                    for (T item; (item = queue.poll()) != null;) {
                        for (int attempt = 0; !Thread.currentThread().isInterrupted(); attempt++) {
                            if (!retry.test(item) || attempt == retries) break;
                        }
                        if (Thread.currentThread().isInterrupted()) return;
                    }
                }));
            }
            try {
                for (Future<?> task : tasks) task.get();
            } catch (InterruptedException e) {
                executor.shutdownNow();
                throw e;
            } catch (ExecutionException e) {
                executor.shutdownNow();
                throw new IllegalStateException("A check failed", e.getCause());
            }
        }
    }
}
