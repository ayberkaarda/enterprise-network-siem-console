package com.example.demo.common;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The point of the fan-out is that a batch of blocking probes costs about as
 * much as one probe rather than as many as there are probes, so that is what is
 * measured here: a sequential loop over these tasks could not possibly finish in
 * the time the assertion allows.
 */
class VirtualThreadFanOutTest {

    private static final int TASK_COUNT = 64;
    private static final Duration TASK_DURATION = Duration.ofMillis(200);

    /** Sequential cost of the batch, which the run must come nowhere near. */
    private static final long SEQUENTIAL_MS = TASK_COUNT * TASK_DURATION.toMillis();

    @Test
    void blockingTasksRunConcurrentlyRatherThanOneAfterAnother() {
        List<Integer> items = IntStream.range(0, TASK_COUNT).boxed().toList();
        AtomicInteger processed = new AtomicInteger();

        long startedAt = System.nanoTime();
        int succeeded = VirtualThreadFanOut.forEach(items, item -> {
            sleep(TASK_DURATION);
            processed.incrementAndGet();
        });
        long elapsedMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

        assertThat(succeeded).isEqualTo(TASK_COUNT);
        assertThat(processed.get()).isEqualTo(TASK_COUNT);
        assertThat(elapsedMs)
                .as("64 blocking tasks of 200 ms each; sequentially this would take %d ms", SEQUENTIAL_MS)
                .isLessThan(SEQUENTIAL_MS / 4);
    }

    @Test
    void everyItemIsVisitedExactlyOnce() {
        List<Integer> items = IntStream.range(0, 500).boxed().toList();
        Set<Integer> visited = ConcurrentHashMap.newKeySet();
        AtomicInteger visits = new AtomicInteger();

        VirtualThreadFanOut.forEach(items, item -> {
            visited.add(item);
            visits.incrementAndGet();
        });

        assertThat(visited).hasSize(500);
        assertThat(visits.get()).isEqualTo(500);
    }

    @Test
    void oneFailingTaskDoesNotCostTheOthers() {
        List<Integer> items = IntStream.range(0, 10).boxed().toList();
        AtomicInteger processed = new AtomicInteger();

        int succeeded = VirtualThreadFanOut.forEach(items, item -> {
            if (item == 3) {
                throw new IllegalStateException("this host cannot be probed");
            }
            processed.incrementAndGet();
        });

        assertThat(processed.get()).isEqualTo(9);
        assertThat(succeeded).isEqualTo(9);
    }

    @Test
    void anEmptyOrMissingBatchIsNotAnError() {
        assertThat(VirtualThreadFanOut.forEach(List.of(), item -> {
        })).isZero();
        assertThat(VirtualThreadFanOut.forEach(null, item -> {
        })).isZero();
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
