package com.citypets.feeder.controller;

import com.citypets.feeder.common.ApiResponse;
import com.citypets.feeder.config.AsyncConfig;
import com.citypets.feeder.dto.FeederDto;
import com.citypets.feeder.entity.FeedingLog;
import com.citypets.feeder.service.FeederService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

@RestController
@RequestMapping("/api/feeder")
public class FeederController {

    private final FeederService feederService;
    private final ThreadPoolExecutor aiSignalExecutor;
    private final ThreadPoolExecutor hardwareExecutor;

    public FeederController(FeederService feederService,
                            @Qualifier(AsyncConfig.AI_SIGNAL_QUEUE_BEAN) ThreadPoolExecutor aiSignalExecutor,
                            @Qualifier(AsyncConfig.HARDWARE_CMD_QUEUE_BEAN) ThreadPoolExecutor hardwareExecutor) {
        this.feederService = feederService;
        this.aiSignalExecutor = aiSignalExecutor;
        this.hardwareExecutor = hardwareExecutor;
    }

    @GetMapping
    public ApiResponse<List<FeederDto>> listAll() {
        return ApiResponse.ok(feederService.listAll());
    }

    @GetMapping("/{feederId}")
    public ApiResponse<FeederDto> getByFeederId(@PathVariable String feederId) {
        return feederService.findByFeederId(feederId)
                .map(ApiResponse::ok)
                .orElse(ApiResponse.error(404, "Feeder not found: " + feederId));
    }

    @PostMapping
    public ApiResponse<FeederDto> createOrUpdate(@RequestBody FeederDto dto) {
        if (dto.getFeederId() == null || dto.getFeederId().isBlank()) {
            return ApiResponse.error(400, "feederId is required");
        }
        return ApiResponse.ok(feederService.createOrUpdate(dto));
    }

    @PostMapping("/{feederId}/refill")
    public ApiResponse<FeederDto> refillFood(@PathVariable String feederId,
                                             @RequestParam(defaultValue = "5000") int grams) {
        try {
            return ApiResponse.ok(feederService.refillFood(feederId, grams));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @PostMapping("/{feederId}/unlock")
    public ApiResponse<FeederDto> unlockDoor(@PathVariable String feederId,
                                             @RequestParam(required = false) String reason) {
        try {
            return ApiResponse.ok(feederService.unlockDoor(feederId, reason));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @GetMapping("/{feederId}/logs")
    public ApiResponse<Map<String, Object>> listLogs(@PathVariable String feederId,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "50") int size) {
        Page<FeedingLog> p = feederService.listLogs(feederId, page, size);
        Map<String, Object> res = new HashMap<>();
        res.put("content", p.getContent());
        res.put("totalElements", p.getTotalElements());
        res.put("totalPages", p.getTotalPages());
        res.put("page", p.getNumber());
        res.put("size", p.getSize());
        return ApiResponse.ok(res);
    }

    @GetMapping("/{feederId}/pest-count")
    public ApiResponse<Map<String, Object>> pestCount(@PathVariable String feederId,
                                                      @RequestParam(defaultValue = "60") int windowSeconds) {
        long count = feederService.countRecentPest(feederId, windowSeconds);
        Map<String, Object> res = new HashMap<>();
        res.put("feederId", feederId);
        res.put("windowSeconds", windowSeconds);
        res.put("pestDetectionCount", count);
        return ApiResponse.ok(res);
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> h = new HashMap<>();
        h.put("status", "UP");
        h.put("timestamp", System.currentTimeMillis());
        h.put("aiSignalPool", poolMetrics(aiSignalExecutor));
        h.put("hardwarePool", poolMetrics(hardwareExecutor));
        return ApiResponse.ok(h);
    }

    @GetMapping("/fat-cat-ranking")
    public ApiResponse<List<Map<String, Object>>> fatCatRanking(
            @RequestParam(defaultValue = "24") int hoursBack,
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(feederService.getFatCatRanking(hoursBack, limit));
    }

    private Object poolMetrics(ThreadPoolExecutor pool) {
        if (pool instanceof AsyncConfig.InstrumentedThreadPool ip) {
            return ip.getMetrics();
        }
        Map<String, Object> m = new HashMap<>();
        m.put("activeCount", pool.getActiveCount());
        m.put("poolSize", pool.getPoolSize());
        m.put("queueSize", pool.getQueue().size());
        m.put("completedTasks", pool.getCompletedTaskCount());
        return m;
    }
}
