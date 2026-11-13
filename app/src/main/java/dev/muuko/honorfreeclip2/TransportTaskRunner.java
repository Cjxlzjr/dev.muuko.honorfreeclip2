package dev.muuko.honorfreeclip2;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Bounded serial queue. A timed-out task cannot later run or complete a second time. */
final class TransportTaskRunner {
    private final ThreadPoolExecutor worker;
    private final ScheduledExecutorService timer;
    private final long timeoutMs;
    TransportTaskRunner(long timeoutMs, int capacity) {
        this.timeoutMs = timeoutMs;
        worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(capacity), r -> daemon(r, "HonorFreeClip2-rfcomm"));
        timer = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "HonorFreeClip2-deadline"));
    }
    private static Thread daemon(Runnable job, String name) {
        Thread thread = new Thread(job, name);
        thread.setDaemon(true);
        return thread;
    }
    void execute(Runnable task, Runnable failed, Runnable abort) {
        AtomicBoolean finished = new AtomicBoolean();
        Runnable failure = () -> {
            try { failed.run(); } catch (Throwable ignored) { }
        };
        AtomicReference<Future<?>> deadline = new AtomicReference<>();
        Runnable queued = () -> {
            if (finished.get()) return;
            try { task.run(); }
            catch (Throwable ignored) {
                if (finished.compareAndSet(false, true)) failure.run();
            } finally {
                finished.set(true);
                Future<?> scheduled = deadline.get();
                if (scheduled != null) scheduled.cancel(false);
            }
        };
        deadline.set(timer.schedule(() -> {
            if (finished.compareAndSet(false, true)) {
                worker.remove(queued);
                // Deliver the failure even if a vendor close() unexpectedly blocks.
                failure.run();
                try { abort.run(); } catch (Throwable ignored) { }
            }
        }, timeoutMs, TimeUnit.MILLISECONDS));
        try {
            if (!finished.get()) worker.execute(queued);
        } catch (java.util.concurrent.RejectedExecutionException full) {
            deadline.get().cancel(false);
            if (finished.compareAndSet(false, true)) failure.run();
        }
    }
    void shutdown() { worker.shutdownNow(); timer.shutdownNow(); }
}
