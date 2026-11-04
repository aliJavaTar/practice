package com.edwardmagongo.ledgerapi.support;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Sends the same request from many threads, all released at the same moment. */
public final class ConcurrentRequests {

    private ConcurrentRequests() {
    }

    public static <T> List<T> send(int count, Supplier<T> request) throws InterruptedException {
        CyclicBarrier startTogether = new CyclicBarrier(count);
        Callable<T> task = () -> {
            startTogether.await();
            return request.get();
        };
        try (ExecutorService pool = Executors.newFixedThreadPool(count)) {
            return pool.invokeAll(Collections.nCopies(count, task), 60, TimeUnit.SECONDS).stream()
                    .map(Future::resultNow)
                    .toList();
        }
    }
}
