package com.citypets.feeder.service;

import com.citypets.feeder.config.AsyncConfig;
import com.citypets.feeder.config.FeederProperties;
import com.citypets.feeder.dto.AiSignalProcessResult;
import com.citypets.feeder.dto.AiSignalRequest;
import com.citypets.feeder.entity.FeederDevice;
import com.citypets.feeder.entity.FeedingLog;
import com.citypets.feeder.repository.FeederDeviceRepository;
import com.citypets.feeder.repository.FeedingLogRepository;
import com.citypets.feeder.resilience.SlidingWindowCircuitBreaker;
import com.citypets.feeder.resilience.TokenBucketRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
public class FeedingService {

    private static final Logger log = LoggerFactory.getLogger(FeedingService.class);

    private final FeederDeviceRepository feederDeviceRepo;
    private final FeedingLogRepository feedingLogRepo;
    private final FeederProperties props;
    private final RedisTemplate<String, Object> redisTemplate;
    private final TokenBucketRateLimiter rateLimiter;
    private final SlidingWindowCircuitBreaker circuitBreaker;
    private final ThreadPoolExecutor aiSignalExecutor;
    private final ThreadPoolExecutor hardwareExecutor;

    private static final String FEEDER_CACHE_PREFIX = "feeder:device:";
    private static final String PEST_WINDOW_PREFIX = "feeder:pest:window:";

    public FeedingService(FeederDeviceRepository feederDeviceRepo,
                          FeedingLogRepository feedingLogRepo,
                          FeederProperties props,
                          RedisTemplate<String, Object> redisTemplate,
                          TokenBucketRateLimiter rateLimiter,
                          SlidingWindowCircuitBreaker circuitBreaker,
                          @Qualifier(AsyncConfig.AI_SIGNAL_QUEUE_BEAN) ThreadPoolExecutor aiSignalExecutor,
                          @Qualifier(AsyncConfig.HARDWARE_CMD_QUEUE_BEAN) ThreadPoolExecutor hardwareExecutor) {
        this.feederDeviceRepo = feederDeviceRepo;
        this.feedingLogRepo = feedingLogRepo;
        this.props = props;
        this.redisTemplate = redisTemplate;
        this.rateLimiter = rateLimiter;
        this.circuitBreaker = circuitBreaker;
        this.aiSignalExecutor = aiSignalExecutor;
        this.hardwareExecutor = hardwareExecutor;
    }

    public AiSignalProcessResult processAiSignalSync(AiSignalRequest req) {
        long startNanos = System.nanoTime();
        String feederId = req.getFeederId();

        if (!rateLimiter.tryAcquire(feederId)) {
            return buildRejectedResult(req, "RATE_LIMITED",
                    "Rate limit exceeded for feeder " + feederId, false);
        }

        if (!circuitBreaker.canProceed()) {
            return buildRejectedResult(req, "CIRCUIT_OPEN",
                    "Circuit breaker is OPEN - rejecting to prevent cascade failure", true);
        }

        try {
            AiSignalProcessResult result = doProcess(req);
            circuitBreaker.recordResult(true, System.nanoTime() - startNanos);
            return result;
        } catch (Exception e) {
            log.error("processAiSignal failed for {}: {}", feederId, e.getMessage(), e);
            circuitBreaker.recordResult(false, System.nanoTime() - startNanos);
            return buildRejectedResult(req, "INTERNAL_ERROR", e.getMessage(), false);
        }
    }

    @Async(AsyncConfig.AI_SIGNAL_QUEUE_BEAN)
    public CompletableFuture<AiSignalProcessResult> processAiSignalAsync(AiSignalRequest req) {
        return CompletableFuture.completedFuture(processAiSignalSync(req));
    }

    public Map<String, Object> submitAsyncSignal(AiSignalRequest req) {
        Map<String, Object> resp = new HashMap<>();
        String feederId = req.getFeederId();

        if (!rateLimiter.tryAcquire(feederId)) {
            resp.put("accepted", false);
            resp.put("reason", "RATE_LIMITED");
            resp.put("message", "Rate limit exceeded for feeder " + feederId);
            return resp;
        }

        if (!circuitBreaker.canProceed()) {
            resp.put("accepted", false);
            resp.put("reason", "CIRCUIT_OPEN");
            resp.put("message", "Circuit breaker OPEN");
            return resp;
        }

        try {
            CompletableFuture<AiSignalProcessResult> future =
                    CompletableFuture.supplyAsync(() -> processAiSignalSync(req), aiSignalExecutor);
            resp.put("accepted", true);
            resp.put("submitted", true);
            resp.put("queueSize", aiSignalExecutor.getQueue().size());
            resp.put("activeWorkers", aiSignalExecutor.getActiveCount());
            resp.put("message", "Signal submitted to async processing pool");
            return resp;
        } catch (Exception e) {
            log.error("submitAsyncSignal rejected: {}", e.getMessage());
            resp.put("accepted", false);
            resp.put("reason", "QUEUE_FULL");
            resp.put("message", "Async pool queue is full, dropping signal");
            return resp;
        }
    }

    @Transactional
    public AiSignalProcessResult doProcess(AiSignalRequest req) {
        String feederId = req.getFeederId();
        String rawAnimalType = req.getAnimalType() == null ? "unknown" : req.getAnimalType();
        FeedingLog.AnimalType animalType = mapAnimalType(rawAnimalType);

        FeederDevice feeder = feederDeviceRepo.findByFeederId(feederId)
                .orElseGet(() -> createDefaultFeeder(feederId));

        FeedingLog logEntry = new FeedingLog();
        logEntry.setFeederId(feederId);
        logEntry.setAnimalType(animalType);
        logEntry.setConfidence(req.getConfidence());
        logEntry.setDetectionTimestamp(req.getTimestamp() != null
                ? parseTimestamp(req.getTimestamp()) : LocalDateTime.now());

        AiSignalProcessResult.Builder result = AiSignalProcessResult.builder()
                .feederId(feederId)
                .animalType(rawAnimalType)
                .confidence(req.getConfidence() != null ? req.getConfidence() : 0.0)
                .processingMode("sync");

        if (Boolean.FALSE.equals(req.getAboveThreshold())) {
            return completeWithNoFeed(feeder, logEntry, result,
                    FeedingLog.DoorAction.NONE,
                    "BELOW_CONFIDENCE_THRESHOLD", false, null);
        }

        if (feeder.getStatus() != FeederDevice.FeederStatus.ONLINE) {
            return completeWithNoFeed(feeder, logEntry, result,
                    FeedingLog.DoorAction.NONE,
                    "FEEDER_OFFLINE: " + feeder.getStatus(), false, null);
        }

        boolean isLocked = feeder.getDoorLocked() != null && feeder.getDoorLocked();
        if (isLocked) {
            if (feeder.getLockUntil() != null && feeder.getLockUntil().isBefore(LocalDateTime.now())) {
                feeder.setDoorLocked(false);
                feeder.setLockReason(null);
                feeder.setLockUntil(null);
                isLocked = false;
            }
        }

        boolean isPest = (animalType == FeedingLog.AnimalType.PEST_ANIMAL);

        if (isPest) {
            return handlePestAnimal(feeder, logEntry, result, isLocked);
        }

        if (animalType == FeedingLog.AnimalType.UNKNOWN) {
            return completeWithNoFeed(feeder, logEntry, result,
                    FeedingLog.DoorAction.NONE,
                    "UNKNOWN_ANIMAL_TYPE: " + rawAnimalType, false, null);
        }

        if (isLocked) {
            long remain = feeder.getLockUntil() != null
                    ? Duration.between(LocalDateTime.now(), feeder.getLockUntil()).getSeconds() : 0;
            return completeWithNoFeed(feeder, logEntry, result,
                    FeedingLog.DoorAction.NONE,
                    "FEEDER_LOCKED: " + feeder.getLockReason(), true, Math.max(0, remain));
        }

        return performFeeding(feeder, logEntry, result, animalType);
    }

    private AiSignalProcessResult handlePestAnimal(FeederDevice feeder, FeedingLog logEntry,
                                                    AiSignalProcessResult.Builder result,
                                                    boolean currentlyLocked) {
        String feederId = feeder.getFeederId();
        LocalDateTime windowStart = LocalDateTime.now()
                .minusSeconds(props.getPestDetectionWindowSeconds());

        long pestCountInWindow = feedingLogRepo.countRecentPestDetections(feederId, windowStart) + 1;
        boolean shouldLock = pestCountInWindow >= props.getPestDetectionThreshold();

        String doorActionStr;
        boolean lockedResult;
        String reason;
        Long lockRemain = null;

        if (shouldLock || currentlyLocked) {
            LocalDateTime lockUntil = LocalDateTime.now()
                    .plusSeconds(props.getPestLockDurationSeconds());
            feeder.setDoorLocked(true);
            feeder.setLockReason(String.format(
                    "PEST_LOCK: detected %d pest animals within %ds window (threshold=%d). Lock for %ds",
                    pestCountInWindow, props.getPestDetectionWindowSeconds(),
                    props.getPestDetectionThreshold(), props.getPestLockDurationSeconds()));
            feeder.setLockUntil(lockUntil);
            doorActionStr = FeedingLog.DoorAction.LOCK.name();
            lockedResult = true;
            reason = String.format("PEST_ANIMAL detected (%dx in window) -> DOOR LOCKED", pestCountInWindow);
            lockRemain = (long) props.getPestLockDurationSeconds();
            issueHardwareCommand(feederId, "DOOR_LOCK");
        } else {
            doorActionStr = FeedingLog.DoorAction.NONE.name();
            lockedResult = currentlyLocked;
            reason = String.format("PEST_ANIMAL detected (%dx/%d threshold) -> REJECT FEEDING, not locking yet",
                    pestCountInWindow, props.getPestDetectionThreshold());
        }

        logEntry.setFeedingSuccess(false);
        logEntry.setDoorAction(FeedingLog.DoorAction.valueOf(doorActionStr));
        logEntry.setFailureReason(reason);
        logEntry.setFoodDispensedGrams(0);
        logEntry.setFoodBeforeGrams(feeder.getFoodRemainingGrams());
        logEntry.setFoodAfterGrams(feeder.getFoodRemainingGrams());
        feedingLogRepo.save(logEntry);
        evictFeederCache(feederId);

        result.feedingTriggered(false)
                .foodDispensedGrams(0)
                .foodRemainingPercentage(feeder.getFoodPercentage())
                .doorAction(doorActionStr)
                .doorLocked(lockedResult)
                .lockReason(feeder.getLockReason())
                .lockExpireSecondsRemaining(lockRemain)
                .statusMessage(reason)
                .pestAlarm(true);

        return result.build();
    }

    private AiSignalProcessResult performFeeding(FeederDevice feeder, FeedingLog logEntry,
                                                  AiSignalProcessResult.Builder result,
                                                  FeedingLog.AnimalType animalType) {
        int before = feeder.getFoodRemainingGrams() != null ? feeder.getFoodRemainingGrams() : 0;
        int capacity = feeder.getFoodCapacityGrams() != null ? feeder.getFoodCapacityGrams() : 5000;
        int amount = props.getDefaultFoodAmountPerDispense();

        if (before <= 0) {
            String reason = "FOOD_EMPTY: no food remaining in hopper";
            return completeWithNoFeed(feeder, logEntry, result,
                    FeedingLog.DoorAction.NONE, reason, false, null);
        }

        double pctBefore = before * 100.0 / capacity;
        if (pctBefore < props.getMinFoodPercentageThreshold()) {
            String reason = String.format("FOOD_LOW: %.2f%% remaining (threshold %.2f%%)",
                    pctBefore, props.getMinFoodPercentageThreshold());
            return completeWithNoFeed(feeder, logEntry, result,
                    FeedingLog.DoorAction.NONE, reason, false, null);
        }

        int actualDispensed = Math.min(amount, before);
        int after = before - actualDispensed;
        double newPct = after * 100.0 / capacity;

        feeder.setFoodRemainingGrams(after);
        feeder.setFoodPercentage(Math.round(newPct * 100.0) / 100.0);

        logEntry.setFoodBeforeGrams(before);
        logEntry.setFoodAfterGrams(after);
        logEntry.setFoodDispensedGrams(actualDispensed);
        logEntry.setFeedingSuccess(true);
        logEntry.setDoorAction(FeedingLog.DoorAction.OPEN_FOR_FEEDING);
        logEntry.setFailureReason(null);
        feedingLogRepo.save(logEntry);
        evictFeederCache(feeder.getFeederId());

        issueHardwareCommand(feeder.getFeederId(), "DISPENSE_FOOD:" + actualDispensed);

        result.feedingTriggered(true)
                .foodDispensedGrams(actualDispensed)
                .foodRemainingPercentage(feeder.getFoodPercentage())
                .doorAction(FeedingLog.DoorAction.OPEN_FOR_FEEDING.name())
                .doorLocked(false)
                .lockReason(null)
                .lockExpireSecondsRemaining(null)
                .statusMessage(String.format("FEEDING OK: dispensed %dg to %s (%.2f%% -> %.2f%%)",
                        actualDispensed, animalType,
                        before * 100.0 / capacity, newPct))
                .pestAlarm(false);

        return result.build();
    }

    private AiSignalProcessResult completeWithNoFeed(
            FeederDevice feeder, FeedingLog logEntry,
            AiSignalProcessResult.Builder result,
            FeedingLog.DoorAction doorAction, String reason,
            boolean lockedState, Long lockRemainSec) {

        int remaining = feeder.getFoodRemainingGrams() != null ? feeder.getFoodRemainingGrams() : 0;
        logEntry.setFoodBeforeGrams(remaining);
        logEntry.setFoodAfterGrams(remaining);
        logEntry.setFoodDispensedGrams(0);
        logEntry.setFeedingSuccess(false);
        logEntry.setDoorAction(doorAction);
        logEntry.setFailureReason(reason);
        feedingLogRepo.save(logEntry);

        result.feedingTriggered(false)
                .foodDispensedGrams(0)
                .foodRemainingPercentage(feeder.getFoodPercentage())
                .doorAction(doorAction.name())
                .doorLocked(lockedState)
                .lockReason(feeder.getLockReason())
                .lockExpireSecondsRemaining(lockRemainSec)
                .statusMessage("REJECTED: " + reason)
                .pestAlarm(false);
        return result.build();
    }

    private FeederDevice createDefaultFeeder(String feederId) {
        FeederDevice f = new FeederDevice();
        f.setFeederId(feederId);
        f.setName("Auto-registered feeder " + feederId);
        f.setLocation("Unknown");
        f.setFoodCapacityGrams(5000);
        f.setFoodRemainingGrams(5000);
        f.setFoodPercentage(100.0);
        f.setStatus(FeederDevice.FeederStatus.ONLINE);
        f.setDoorLocked(false);
        return feederDeviceRepo.save(f);
    }

    private FeedingLog.AnimalType mapAnimalType(String raw) {
        if (raw == null) return FeedingLog.AnimalType.UNKNOWN;
        return switch (raw.toLowerCase().trim()) {
            case "stray_cat" -> FeedingLog.AnimalType.STRAY_CAT;
            case "stray_dog" -> FeedingLog.AnimalType.STRAY_DOG;
            case "pest_animal", "rat", "mouse", "weasel", "黄鼠狼", "老鼠"
                    -> FeedingLog.AnimalType.PEST_ANIMAL;
            default -> FeedingLog.AnimalType.UNKNOWN;
        };
    }

    private LocalDateTime parseTimestamp(String ts) {
        try {
            if (ts == null) return LocalDateTime.now();
            return LocalDateTime.parse(ts.replace("Z", ""));
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private void issueHardwareCommand(String feederId, String command) {
        try {
            hardwareExecutor.submit(() -> {
                try {
                    TimeUnit.MILLISECONDS.sleep(50);
                    log.debug("[HW] Feeder={} executed command: {}", feederId, command);
                    Map<String, Object> cmd = new HashMap<>();
                    cmd.put("cmd", command);
                    cmd.put("ts", System.currentTimeMillis());
                    redisTemplate.opsForList().rightPush(
                            "feeder:hw:cmd:" + feederId, cmd);
                } catch (Exception e) {
                    log.warn("[HW] command failed {} for {}: {}", command, feederId, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("[HW] queue rejected command {} for {}: {}", command, feederId, e.getMessage());
        }
    }

    private void evictFeederCache(String feederId) {
        try {
            redisTemplate.delete(FEEDER_CACHE_PREFIX + feederId);
        } catch (Exception ignored) {}
    }

    private AiSignalProcessResult buildRejectedResult(AiSignalRequest req, String code,
                                                       String message, boolean circuitRelated) {
        return AiSignalProcessResult.builder()
                .feederId(req.getFeederId())
                .animalType(req.getAnimalType())
                .confidence(req.getConfidence() != null ? req.getConfidence() : 0.0)
                .feedingTriggered(false)
                .foodDispensedGrams(0)
                .doorAction("REJECTED:" + code)
                .doorLocked(circuitRelated)
                .statusMessage("REJECTED[" + code + "]: " + message)
                .pestAlarm(false)
                .processingMode(code)
                .build();
    }
}
