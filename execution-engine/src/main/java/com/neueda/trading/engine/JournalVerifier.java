package com.neueda.trading.engine;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.annotation.PreDestroy;

import org.springframework.stereotype.Component;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs a replay of a day for the admin "Verify this day" button, inside the live engine but kept out of
 * trading's way: one replay at a time, on one low-priority thread, with its own fresh engine.
 */
@Component
public class JournalVerifier {

    private static final long TIMEOUT_SECONDS = 120;

    /** A second verify while one runs. */
    static final class BusyException extends RuntimeException {
        BusyException() {
            super("A replay is already running; try again in a moment");
        }
    }

    record DifferenceView(Long seq, String orderId, String recorded, String replayed) {
    }

    record VerifyResult(String day, int engineRuns, int orders, int quotes, int decisions, boolean matches,
            List<DifferenceView> differences) {
    }

    private final Semaphore oneAtATime = new Semaphore(1);
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "journal-verify");
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.setDaemon(true);
        return thread;
    });

    VerifyResult verify(String day, Path journal) throws Exception {
        if (!oneAtATime.tryAcquire()) {
            throw new BusyException();
        }
        try {
            Future<Replay.Result> run = worker.submit(() -> new Replay(
                JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build()).run(journal));
            Replay.Result result = waitFor(run);
            List<DifferenceView> differences = result.differences().stream()
                .map(d -> new DifferenceView(
                    d.recorded() != null ? d.seq() : null,
                    d.recorded() != null ? d.recorded().orderId() : d.replayed().orderId(),
                    d.recorded() != null ? d.recorded().toString() : null,
                    d.replayed() != null ? d.replayed().toString() : null))
                .toList();
            return new VerifyResult(day, result.runs(), result.orders(), result.quotes(),
                result.recorded().size(), result.matches(), differences);
        } finally {
            oneAtATime.release();
        }
    }

    private static Replay.Result waitFor(Future<Replay.Result> run) throws Exception {
        try {
            return run.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            run.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        }
    }

    @PreDestroy
    void stop() {
        worker.shutdownNow();
    }
}
