package com.citypets.feeder.controller;

import com.citypets.feeder.common.ApiResponse;
import com.citypets.feeder.dto.AiSignalProcessResult;
import com.citypets.feeder.dto.AiSignalRequest;
import com.citypets.feeder.resilience.SlidingWindowCircuitBreaker;
import com.citypets.feeder.resilience.TokenBucketRateLimiter;
import com.citypets.feeder.service.FeedingService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/feeding")
public class FeedingController {

    private final FeedingService feedingService;
    private final TokenBucketRateLimiter rateLimiter;
    private final SlidingWindowCircuitBreaker circuitBreaker;

    public FeedingController(FeedingService feedingService,
                             TokenBucketRateLimiter rateLimiter,
                             SlidingWindowCircuitBreaker circuitBreaker) {
        this.feedingService = feedingService;
        this.rateLimiter = rateLimiter;
        this.circuitBreaker = circuitBreaker;
    }

    @PostMapping("/ai-signal")
    public ApiResponse<AiSignalProcessResult> receiveAiSignal(@RequestBody AiSignalRequest request) {
        if (request.getFeederId() == null || request.getFeederId().isBlank()) {
            return ApiResponse.error(400, "feederId is required");
        }
        AiSignalProcessResult result = feedingService.processAiSignalSync(request);
        if ("RATE_LIMITED".equals(result.getProcessingMode())) {
            return ApiResponse.rateLimited();
        }
        if ("CIRCUIT_OPEN".equals(result.getProcessingMode())) {
            return ApiResponse.circuitOpen(result.getStatusMessage());
        }
        return ApiResponse.ok(result);
    }

    @PostMapping("/ai-signal/async")
    public ApiResponse<Map<String, Object>> receiveAiSignalAsync(@RequestBody AiSignalRequest request) {
        if (request.getFeederId() == null || request.getFeederId().isBlank()) {
            return ApiResponse.error(400, "feederId is required");
        }
        Map<String, Object> submission = feedingService.submitAsyncSignal(request);
        if (Boolean.FALSE.equals(submission.get("accepted"))) {
            String reason = (String) submission.getOrDefault("reason", "UNKNOWN");
            if ("RATE_LIMITED".equals(reason)) {
                return ApiResponse.rateLimited();
            }
            if ("CIRCUIT_OPEN".equals(reason)) {
                return ApiResponse.circuitOpen((String) submission.get("message"));
            }
            return ApiResponse.error(503,
                    "Signal rejected: " + submission.getOrDefault("message", reason));
        }
        return ApiResponse.accepted(submission);
    }

    @GetMapping("/resilience/status")
    public ApiResponse<Map<String, Object>> getResilienceStatus(
            @RequestParam(required = false, defaultValue = "FEEDER-001") String feederId) {
        Map<String, Object> s = new HashMap<>();
        s.put("rateLimiter", rateLimiter.getStatus(feederId));
        s.put("circuitBreaker", circuitBreaker.getMetrics());
        return ApiResponse.ok(s);
    }
}
