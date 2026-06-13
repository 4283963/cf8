package com.citypets.feeder.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "feeder")
public class FeederProperties {

    private int defaultFoodAmountPerDispense = 10;
    private double minFoodPercentageThreshold = 5;
    private int pestLockDurationSeconds = 300;
    private int pestDetectionThreshold = 3;
    private int pestDetectionWindowSeconds = 60;
    private int asyncSignalQueueCapacity = 10000;
    private RateLimit rateLimit = new RateLimit();
    private CircuitBreakerProps circuitBreaker = new CircuitBreakerProps();

    public int getDefaultFoodAmountPerDispense() { return defaultFoodAmountPerDispense; }
    public void setDefaultFoodAmountPerDispense(int v) { this.defaultFoodAmountPerDispense = v; }
    public double getMinFoodPercentageThreshold() { return minFoodPercentageThreshold; }
    public void setMinFoodPercentageThreshold(double v) { this.minFoodPercentageThreshold = v; }
    public int getPestLockDurationSeconds() { return pestLockDurationSeconds; }
    public void setPestLockDurationSeconds(int v) { this.pestLockDurationSeconds = v; }
    public int getPestDetectionThreshold() { return pestDetectionThreshold; }
    public void setPestDetectionThreshold(int v) { this.pestDetectionThreshold = v; }
    public int getPestDetectionWindowSeconds() { return pestDetectionWindowSeconds; }
    public void setPestDetectionWindowSeconds(int v) { this.pestDetectionWindowSeconds = v; }
    public int getAsyncSignalQueueCapacity() { return asyncSignalQueueCapacity; }
    public void setAsyncSignalQueueCapacity(int v) { this.asyncSignalQueueCapacity = v; }
    public RateLimit getRateLimit() { return rateLimit; }
    public void setRateLimit(RateLimit rateLimit) { this.rateLimit = rateLimit; }
    public CircuitBreakerProps getCircuitBreaker() { return circuitBreaker; }
    public void setCircuitBreaker(CircuitBreakerProps circuitBreaker) { this.circuitBreaker = circuitBreaker; }

    public static class RateLimit {
        private double aiSignalPerFeederPerSecond = 2.0;
        private double aiSignalGlobalPerSecond = 50.0;
        private int burstFactor = 5;

        public double getAiSignalPerFeederPerSecond() { return aiSignalPerFeederPerSecond; }
        public void setAiSignalPerFeederPerSecond(double v) { this.aiSignalPerFeederPerSecond = v; }
        public double getAiSignalGlobalPerSecond() { return aiSignalGlobalPerSecond; }
        public void setAiSignalGlobalPerSecond(double v) { this.aiSignalGlobalPerSecond = v; }
        public int getBurstFactor() { return burstFactor; }
        public void setBurstFactor(int v) { this.burstFactor = v; }
    }

    public static class CircuitBreakerProps {
        private int failureThresholdPercentage = 50;
        private int slowCallThresholdPercentage = 80;
        private int slowCallDurationSeconds = 3;
        private int waitDurationInOpenStateSeconds = 15;
        private int permittedNumberOfCallsInHalfOpenState = 3;
        private int slidingWindowSize = 50;
        private String slidingWindowType = "COUNT_BASED";
        private int minimumNumberOfCalls = 10;

        public int getFailureThresholdPercentage() { return failureThresholdPercentage; }
        public void setFailureThresholdPercentage(int v) { this.failureThresholdPercentage = v; }
        public int getSlowCallThresholdPercentage() { return slowCallThresholdPercentage; }
        public void setSlowCallThresholdPercentage(int v) { this.slowCallThresholdPercentage = v; }
        public int getSlowCallDurationSeconds() { return slowCallDurationSeconds; }
        public void setSlowCallDurationSeconds(int v) { this.slowCallDurationSeconds = v; }
        public int getWaitDurationInOpenStateSeconds() { return waitDurationInOpenStateSeconds; }
        public void setWaitDurationInOpenStateSeconds(int v) { this.waitDurationInOpenStateSeconds = v; }
        public int getPermittedNumberOfCallsInHalfOpenState() { return permittedNumberOfCallsInHalfOpenState; }
        public void setPermittedNumberOfCallsInHalfOpenState(int v) { this.permittedNumberOfCallsInHalfOpenState = v; }
        public int getSlidingWindowSize() { return slidingWindowSize; }
        public void setSlidingWindowSize(int v) { this.slidingWindowSize = v; }
        public String getSlidingWindowType() { return slidingWindowType; }
        public void setSlidingWindowType(String v) { this.slidingWindowType = v; }
        public int getMinimumNumberOfCalls() { return minimumNumberOfCalls; }
        public void setMinimumNumberOfCalls(int v) { this.minimumNumberOfCalls = v; }
    }
}
