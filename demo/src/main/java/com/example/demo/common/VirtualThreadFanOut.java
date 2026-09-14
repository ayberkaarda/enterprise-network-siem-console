package com.example.demo.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Runs one short-lived task per item, each on its own virtual thread, and
 * returns only once every task has finished.
 *
 * <p>This exists for work that spends its time blocked on I/O rather than on
 * the CPU — a reachability probe waits for a packet that may never come. A
 * sequential loop over such work costs the sum of the individual waits, so its
 * duration grows in step with the number of items; a fixed-size platform thread
 * pool bounds that growth but caps concurrency at the pool size and ties up a
 * real operating system thread for every item in flight. A virtual thread parked
 * on a blocking call holds no operating system thread, so the whole batch costs
 * roughly as long as its slowest single item.
 *
 * <p>One task failing does not cancel the others: in a scan, one unreachable or
 * misconfigured host must not cost the results for every other host.
 */
public final class VirtualThreadFanOut {

    private static final Logger log = LoggerFactory.getLogger(VirtualThreadFanOut.class);

    private VirtualThreadFanOut() {
    }

    /**
     * Applies {@code action} to every item concurrently and waits for all of
     * them.
     *
     * @return how many items were processed without throwing
     */
    public static <T> int forEach(Collection<T> items, Consumer<T> action) {
        if (items == null || items.isEmpty()) {
            return 0;
        }

        int succeeded = 0;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>(items.size());
            for (T item : items) {
                futures.add(executor.submit(() -> action.accept(item)));
            }

            for (Future<?> future : futures) {
                try {
                    future.get();
                    succeeded++;
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (ExecutionException ex) {
                    log.warn("A parallel task failed and was skipped", ex.getCause());
                }
            }
        }
        return succeeded;
    }
}
