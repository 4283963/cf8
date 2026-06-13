package com.citypets.feeder.resilience;

import com.citypets.feeder.config.FeederProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class TokenBucketRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(TokenBucketRateLimiter.class);

    private final Map<String, Bucket> perFeederBuckets = new ConcurrentHashMap<>();
    private final Bucket globalBucket;
    private final double perFeederRate;
    private final double globalRate;
    private final int burstFactor;

    public TokenBucketRateLimiter(FeederProperties props) {
        FeederProperties.RateLimit rl = props.getRateLimit();
        this.perFeederRate = rl.getAiSignalPerFeederPerSecond();
        this.globalRate = rl.getAiSignalGlobalPerSecond();
        this.burstFactor = rl.getBurstFactor();
        this.globalBucket = new Bucket(globalRate, (long) (globalRate * burstFactor));
        log.info("RateLimiter initialized: perFeeder={}/s, global={}/s, burstFactor={}",
                perFeederRate, globalRate, burstFactor);
    }

    public boolean tryAcquire(String feederId) {
        if (!globalBucket.tryConsume()) {
            return false;
        }
        Bucket bucket = perFeederBuckets.computeIfAbsent(feederId,
                id -> new Bucket(perFeederRate, (long) (perFeederRate * burstFactor)));
        boolean ok = bucket.tryConsume();
        if (!ok) {
            globalBucket.refund();
        }
        return ok;
    }

    public RateLimitStatus getStatus(String feederId) {
        Bucket fb = perFeederBuckets.get(feederId);
        return new RateLimitStatus(
                globalBucket.getCurrentTokens(),
                globalBucket.getCapacity(),
                fb != null ? fb.getCurrentTokens() : 0,
                fb != null ? fb.getCapacity() : 0,
                perFeederBuckets.size()
        );
    }

    public record RateLimitStatus(
            long globalTokens,
            long globalCapacity,
            long feederTokens,
            long feederCapacity,
            int activeFeeders
    ) {}

    private static class Bucket {
        private final double rate;
        private final long capacity;
        private final AtomicLong tokens;
        private volatile long lastRefillNanos;

        Bucket(double ratePerSecond, long capacity) {
            this.rate = ratePerSecond;
            this.capacity = capacity;
            this.tokens = new AtomicLong(capacity);
            this.lastRefillNanos = System.nanoTime();
        }

        boolean tryConsume() {
            refill();
            long current;
            do {
                current = tokens.get();
                if (current <= 0) return false;
            } while (!tokens.compareAndSet(current, current - 1));
            return true;
        }

        void refund() {
            long current;
            do {
                current = tokens.get();
                if (current >= capacity) return;
            } while (!tokens.compareAndSet(current, current + 1));
        }

        private void refill() {
            long now = System.nanoTime();
            long elapsed = now - lastRefillNanos;
            if (elapsed <= 0) return;
            double newTokens = (elapsed / 1_000_000_000.0) * rate;
            if (newTokens < 1.0 && tokens.get() > 0) return;
            long add = (long) newTokens;
            if (add > 0) {
                tokens.updateAndGet(cur -> Math.min(capacity, cur + add));
                lastRefillNanos = now;
            }
        }

        long getCurrentTokens() {
            refill();
            return tokens.get();
        }

        long getCapacity() {
            return capacity;
        }
    }
}
