package com.citypets.feeder.service;

import com.citypets.feeder.dto.FeederDto;
import com.citypets.feeder.entity.FeederDevice;
import com.citypets.feeder.entity.FeedingLog;
import com.citypets.feeder.repository.FeederDeviceRepository;
import com.citypets.feeder.repository.FeedingLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FeederService {

    private static final Logger log = LoggerFactory.getLogger(FeederService.class);

    private final FeederDeviceRepository deviceRepo;
    private final FeedingLogRepository logRepo;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String FEEDER_CACHE_PREFIX = "feeder:device:";

    public FeederService(FeederDeviceRepository deviceRepo,
                         FeedingLogRepository logRepo,
                         RedisTemplate<String, Object> redisTemplate) {
        this.deviceRepo = deviceRepo;
        this.logRepo = logRepo;
        this.redisTemplate = redisTemplate;
    }

    @Transactional(readOnly = true)
    public List<FeederDto> listAll() {
        return deviceRepo.findAll().stream().map(FeederDto::from).toList();
    }

    @Transactional(readOnly = true)
    public Optional<FeederDto> findByFeederId(String feederId) {
        Object cached = redisTemplate.opsForValue().get(FEEDER_CACHE_PREFIX + feederId);
        if (cached instanceof FeederDto dto) {
            return Optional.of(dto);
        }
        return deviceRepo.findByFeederId(feederId).map(d -> {
            FeederDto dto = FeederDto.from(d);
            try {
                redisTemplate.opsForValue().set(FEEDER_CACHE_PREFIX + feederId, dto,
                        Duration.ofMinutes(10));
            } catch (Exception ignored) {}
            return dto;
        });
    }

    @Transactional
    public FeederDto createOrUpdate(FeederDto dto) {
        FeederDevice dev = deviceRepo.findByFeederId(dto.getFeederId())
                .orElseGet(FeederDevice::new);
        if (dto.getFeederId() != null) dev.setFeederId(dto.getFeederId());
        if (dto.getName() != null) dev.setName(dto.getName());
        if (dto.getLocation() != null) dev.setLocation(dto.getLocation());
        if (dto.getFoodCapacityGrams() != null) {
            dev.setFoodCapacityGrams(dto.getFoodCapacityGrams());
            if (dev.getFoodRemainingGrams() == null) {
                dev.setFoodRemainingGrams(dto.getFoodCapacityGrams());
                dev.setFoodPercentage(100.0);
            }
        }
        if (dto.getFoodRemainingGrams() != null) {
            dev.setFoodRemainingGrams(dto.getFoodRemainingGrams());
            int cap = dev.getFoodCapacityGrams() != null ? dev.getFoodCapacityGrams() : 5000;
            dev.setFoodPercentage(Math.max(0.0, Math.min(100.0,
                    dev.getFoodRemainingGrams() * 100.0 / cap)));
        }
        if (dto.getStatus() != null) dev.setStatus(dto.getStatus());
        if (dto.getDoorLocked() != null) {
            dev.setDoorLocked(dto.getDoorLocked());
            if (!dto.getDoorLocked()) {
                dev.setLockReason(null);
                dev.setLockUntil(null);
            }
        }
        if (dto.getLockReason() != null) dev.setLockReason(dto.getLockReason());
        if (dto.getLockUntil() != null) dev.setLockUntil(dto.getLockUntil());

        FeederDevice saved = deviceRepo.save(dev);
        try {
            redisTemplate.delete(FEEDER_CACHE_PREFIX + saved.getFeederId());
        } catch (Exception ignored) {}
        return FeederDto.from(saved);
    }

    @Transactional
    public FeederDto refillFood(String feederId, int gramsToAdd) {
        FeederDevice dev = deviceRepo.findByFeederId(feederId)
                .orElseThrow(() -> new IllegalArgumentException("Feeder not found: " + feederId));
        int cap = dev.getFoodCapacityGrams() != null ? dev.getFoodCapacityGrams() : 5000;
        int current = dev.getFoodRemainingGrams() != null ? dev.getFoodRemainingGrams() : 0;
        int newAmount = Math.min(cap, current + gramsToAdd);
        dev.setFoodRemainingGrams(newAmount);
        dev.setFoodPercentage(Math.round(newAmount * 10000.0 / cap) / 100.0);
        dev = deviceRepo.save(dev);
        try {
            redisTemplate.delete(FEEDER_CACHE_PREFIX + feederId);
        } catch (Exception ignored) {}
        log.info("Refilled {} +{}g -> {}g ({}%)", feederId, gramsToAdd,
                newAmount, dev.getFoodPercentage());
        return FeederDto.from(dev);
    }

    @Transactional
    public FeederDto unlockDoor(String feederId, String reason) {
        FeederDevice dev = deviceRepo.findByFeederId(feederId)
                .orElseThrow(() -> new IllegalArgumentException("Feeder not found: " + feederId));
        dev.setDoorLocked(false);
        dev.setLockReason("MANUAL_UNLOCK: " + (reason == null ? "operator" : reason));
        dev.setLockUntil(null);
        dev = deviceRepo.save(dev);
        try {
            redisTemplate.delete(FEEDER_CACHE_PREFIX + feederId);
        } catch (Exception ignored) {}
        return FeederDto.from(dev);
    }

    @Transactional(readOnly = true)
    public Page<FeedingLog> listLogs(String feederId, int page, int size) {
        if (size > 200) size = 200;
        return logRepo.findByFeederIdOrderByCreateTimeDesc(
                feederId, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public long countRecentPest(String feederId, int windowSeconds) {
        LocalDateTime since = LocalDateTime.now().minusSeconds(windowSeconds);
        return logRepo.countRecentPestDetections(feederId, since);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getFatCatRanking(int hoursBack, int limit) {
        LocalDateTime since = LocalDateTime.now().minusHours(hoursBack);
        List<FeedingLog> rejects = logRepo.findDuplicateCatRejectsSince(since);
        Map<String, FatCatAcc> byCat = new LinkedHashMap<>();
        for (FeedingLog r : rejects) {
            String cid = r.getCatFaceId();
            if (cid == null || cid.isBlank()) continue;
            FatCatAcc acc = byCat.computeIfAbsent(cid, k -> new FatCatAcc());
            acc.catFaceId = cid;
            acc.rejectCount++;
            if (acc.firstSnapshot == null && r.getCatSnapshotB64() != null) {
                acc.firstSnapshot = r.getCatSnapshotB64();
            }
            if (acc.funnyTag == null && r.getFunnyTag() != null) {
                acc.funnyTag = r.getFunnyTag();
            } else if (r.getFunnyTag() != null) {
                acc.funnyTag = r.getFunnyTag();
            }
            if (acc.feederId == null) acc.feederId = r.getFeederId();
            acc.lastSeen = r.getCreateTime();
            if (r.getCatSimilarity() != null) {
                acc.maxSimilarity = Math.max(acc.maxSimilarity, r.getCatSimilarity());
            }
            if (r.getCatDailyFeedCount() != null) {
                acc.dailyCount = Math.max(acc.dailyCount, r.getCatDailyFeedCount());
            }
        }
        return byCat.values().stream()
                .sorted(Comparator.comparingInt((FatCatAcc a) -> a.rejectCount).reversed())
                .limit(Math.max(1, limit))
                .map(acc -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("catFaceId", acc.catFaceId);
                    m.put("feederId", acc.feederId);
                    m.put("rejectCount", acc.rejectCount);
                    m.put("funnyTag", acc.funnyTag);
                    m.put("snapshotB64", acc.firstSnapshot);
                    m.put("maxSimilarity", Math.round(acc.maxSimilarity * 10000.0) / 10000.0);
                    m.put("dailyFeedCount", acc.dailyCount);
                    m.put("lastSeen", acc.lastSeen != null ? acc.lastSeen.toString() : null);
                    return m;
                })
                .collect(Collectors.toList());
    }

    private static class FatCatAcc {
        String catFaceId;
        String feederId;
        int rejectCount;
        String funnyTag;
        String firstSnapshot;
        LocalDateTime lastSeen;
        double maxSimilarity;
        int dailyCount;
    }
}
