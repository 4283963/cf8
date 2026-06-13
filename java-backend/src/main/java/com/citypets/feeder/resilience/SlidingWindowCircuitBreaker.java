package com.citypets.feeder.resilience;

import com.citypets.feeder.config.FeederProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class SlidingWindowCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(SlidingWindowCircuitBreaker.class);

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicLong openedAt = new AtomicLong(0);
    private final AtomicInteger halfOpenSuccess = new AtomicInteger(0);

    private final int failureThresholdPct;
    private final int slowCallThresholdPct;
    private final long slowCallNanos;
    private final long waitOpenNanos;
    private final int halfOpenPermits;
    private final int windowSize;
    private final int minCalls;

    private final Object windowLock = new Object();
    private final Deque<CallResult> window = new ArrayDeque<>();

    public SlidingWindowCircuitBreaker(FeederProperties props) {
        FeederProperties.CircuitBreakerProps cb = props.getCircuitBreaker();
        this.failureThresholdPct = cb.getFailureThresholdPercentage();
        this.slowCallThresholdPct = cb.getSlowCallThresholdPercentage();
        this.slowCallNanos = cb.getSlowCallDurationSeconds() * 1_000_000_000L;
        this.waitOpenNanos = cb.getWaitDurationInOpenStateSeconds() * 1_000_000_000L;
        this.halfOpenPermits = cb.getPermittedNumberOfCallsInHalfOpenState();
        this.windowSize = cb.getSlidingWindowSize();
        this.minCalls = cb.getMinimumNumberOfCalls();
        log.info("CircuitBreaker initialized: failurePct={}%, slowPct={}%, slowMs={}, waitOpenMs={}, window={}, minCalls={}",
                failureThresholdPct, slowCallThresholdPct, slowCallNanos / 1_000_000,
                waitOpenNanos / 1_000_000, windowSize, minCalls);
    }

    public boolean canProceed() {
        State s = state.get();
        if (s == State.CLOSED) return true;
        if (s == State.OPEN) {
            if (System.nanoTime() - openedAt.get() >= waitOpenNanos) {
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    halfOpenSuccess.set(0);
                    log.warn("[CircuitBreaker] OPEN -> HALF_OPEN");
                }
                return true;
            }
            return false;
        }
        return halfOpenSuccess.get() < halfOpenPermits;
    }

    public void recordResult(boolean success, long durationNanos) {
        boolean isSlow = durationNanos > slowCallNanos;
        boolean failed = !success || isSlow;

        State s = state.get();

        if (s == State.HALF_OPEN) {
            if (failed) {
                trip("HALF_OPEN failure");
            } else {
                if (halfOpenSuccess.incrementAndGet() >= halfOpenPermits) {
                    if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                        synchronized (windowLock) {
                            window.clear();
                        }
                        log.info("[CircuitBreaker] HALF_OPEN -> CLOSED (recovery successful)");
                    }
                }
            }
            return;
        }

        if (s == State.CLOSED) {
            int failures;
            int slowCalls;
            int total;
            synchronized (windowLock) {
                window.addLast(new CallResult(failed, isSlow));
                while (window.size() > windowSize) window.pollFirst();
                total = window.size();
                failures = 0;
                slowCalls = 0;
                for (CallResult r : window) {
                    if (r.failed) failures++;
                    if (r.slow) slowCalls++;
                }
            }
            if (total < minCalls) return;

            int failPct = failures * 100 / total;
            int slowPct = slowCalls * 100 / total;

            if (failPct >= failureThresholdPct) {
                trip(String.format("failure rate %d%%/%d (>%d%%) over %d calls",
                        failPct, total, failureThresholdPct, total));
            } else if (slowPct >= slowCallThresholdPct) {
                trip(String.format("slow-call rate %d%%/%d (>%d%%) over %d calls",
                        slowPct, total, slowCallThresholdPct, total));
            }
        }
    }

    private void trip(String reason) {
        openedAt.set(System.nanoTime());
        if (state.getAndSet(State.OPEN) != State.OPEN) {
            log.error("[CircuitBreaker] TRIPPED -> OPEN | reason: {}", reason);
        }
    }

    public State getState() {
        return state.get();
    }

    public CircuitBreakerStatus getMetrics() {
        int failures = 0, slow = 0, total;
        synchronized (windowLock) {
            total = window.size();
            for (CallResult r : window) {
                if (r.failed) failures++;
                if (r.slow) slow++;
            }
        }
        long msLeft = state.get() == State.OPEN
                ? Math.max(0, waitOpenNanos - (System.nanoTime() - openedAt.get())) / 1_000_000
                : 0;
        return new CircuitBreakerStatus(
                state.get().name(),
                total, failures, slow,
                total > 0 ? failures * 100.0 / total : 0.0,
                total > 0 ? slow * 100.0 / total : 0.0,
                msLeft,
                halfOpenSuccess.get()
        );
    }

    public record CircuitBreakerStatus(
            String state,
            int windowTotal,
            int windowFailures,
            int windowSlow,
            double failureRatePct,
            double slowRatePct,
            long millisUntilHalfOpen,
            int halfOpenSuccessCount
    ) {}

    private record CallResult(boolean failed, boolean slow) {}
}
