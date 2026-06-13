package com.citypets.feeder.dto;

public class AiSignalProcessResult {
    private String feederId;
    private String animalType;
    private double confidence;
    private boolean feedingTriggered;
    private Integer foodDispensedGrams;
    private Double foodRemainingPercentage;
    private String doorAction;
    private boolean doorLocked;
    private String lockReason;
    private Long lockExpireSecondsRemaining;
    private String statusMessage;
    private boolean pestAlarm;
    private String processingMode;

    private Boolean duplicateCat;
    private String catFaceId;
    private String catSnapshotB64;
    private String funnyTag;
    private Double catSimilarity;
    private Integer catDailyFeedCount;
    private Long feederLockSecondsRemaining;

    public AiSignalProcessResult() {}

    private AiSignalProcessResult(Builder b) {
        this.feederId = b.feederId;
        this.animalType = b.animalType;
        this.confidence = b.confidence;
        this.feedingTriggered = b.feedingTriggered;
        this.foodDispensedGrams = b.foodDispensedGrams;
        this.foodRemainingPercentage = b.foodRemainingPercentage;
        this.doorAction = b.doorAction;
        this.doorLocked = b.doorLocked;
        this.lockReason = b.lockReason;
        this.lockExpireSecondsRemaining = b.lockExpireSecondsRemaining;
        this.statusMessage = b.statusMessage;
        this.pestAlarm = b.pestAlarm;
        this.processingMode = b.processingMode;
        this.duplicateCat = b.duplicateCat;
        this.catFaceId = b.catFaceId;
        this.catSnapshotB64 = b.catSnapshotB64;
        this.funnyTag = b.funnyTag;
        this.catSimilarity = b.catSimilarity;
        this.catDailyFeedCount = b.catDailyFeedCount;
        this.feederLockSecondsRemaining = b.feederLockSecondsRemaining;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String feederId;
        private String animalType;
        private double confidence;
        private boolean feedingTriggered;
        private Integer foodDispensedGrams;
        private Double foodRemainingPercentage;
        private String doorAction;
        private boolean doorLocked;
        private String lockReason;
        private Long lockExpireSecondsRemaining;
        private String statusMessage;
        private boolean pestAlarm;
        private String processingMode;
        private Boolean duplicateCat;
        private String catFaceId;
        private String catSnapshotB64;
        private String funnyTag;
        private Double catSimilarity;
        private Integer catDailyFeedCount;
        private Long feederLockSecondsRemaining;

        public Builder feederId(String v) { this.feederId = v; return this; }
        public Builder animalType(String v) { this.animalType = v; return this; }
        public Builder confidence(double v) { this.confidence = v; return this; }
        public Builder feedingTriggered(boolean v) { this.feedingTriggered = v; return this; }
        public Builder foodDispensedGrams(Integer v) { this.foodDispensedGrams = v; return this; }
        public Builder foodRemainingPercentage(Double v) { this.foodRemainingPercentage = v; return this; }
        public Builder doorAction(String v) { this.doorAction = v; return this; }
        public Builder doorLocked(boolean v) { this.doorLocked = v; return this; }
        public Builder lockReason(String v) { this.lockReason = v; return this; }
        public Builder lockExpireSecondsRemaining(Long v) { this.lockExpireSecondsRemaining = v; return this; }
        public Builder statusMessage(String v) { this.statusMessage = v; return this; }
        public Builder pestAlarm(boolean v) { this.pestAlarm = v; return this; }
        public Builder processingMode(String v) { this.processingMode = v; return this; }
        public Builder duplicateCat(Boolean v) { this.duplicateCat = v; return this; }
        public Builder catFaceId(String v) { this.catFaceId = v; return this; }
        public Builder catSnapshotB64(String v) { this.catSnapshotB64 = v; return this; }
        public Builder funnyTag(String v) { this.funnyTag = v; return this; }
        public Builder catSimilarity(Double v) { this.catSimilarity = v; return this; }
        public Builder catDailyFeedCount(Integer v) { this.catDailyFeedCount = v; return this; }
        public Builder feederLockSecondsRemaining(Long v) { this.feederLockSecondsRemaining = v; return this; }
        public AiSignalProcessResult build() { return new AiSignalProcessResult(this); }
    }

    public String getFeederId() { return feederId; }
    public void setFeederId(String feederId) { this.feederId = feederId; }
    public String getAnimalType() { return animalType; }
    public void setAnimalType(String animalType) { this.animalType = animalType; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public boolean isFeedingTriggered() { return feedingTriggered; }
    public void setFeedingTriggered(boolean feedingTriggered) { this.feedingTriggered = feedingTriggered; }
    public Integer getFoodDispensedGrams() { return foodDispensedGrams; }
    public void setFoodDispensedGrams(Integer foodDispensedGrams) { this.foodDispensedGrams = foodDispensedGrams; }
    public Double getFoodRemainingPercentage() { return foodRemainingPercentage; }
    public void setFoodRemainingPercentage(Double foodRemainingPercentage) { this.foodRemainingPercentage = foodRemainingPercentage; }
    public String getDoorAction() { return doorAction; }
    public void setDoorAction(String doorAction) { this.doorAction = doorAction; }
    public boolean isDoorLocked() { return doorLocked; }
    public void setDoorLocked(boolean doorLocked) { this.doorLocked = doorLocked; }
    public String getLockReason() { return lockReason; }
    public void setLockReason(String lockReason) { this.lockReason = lockReason; }
    public Long getLockExpireSecondsRemaining() { return lockExpireSecondsRemaining; }
    public void setLockExpireSecondsRemaining(Long lockExpireSecondsRemaining) { this.lockExpireSecondsRemaining = lockExpireSecondsRemaining; }
    public String getStatusMessage() { return statusMessage; }
    public void setStatusMessage(String statusMessage) { this.statusMessage = statusMessage; }
    public boolean isPestAlarm() { return pestAlarm; }
    public void setPestAlarm(boolean pestAlarm) { this.pestAlarm = pestAlarm; }
    public String getProcessingMode() { return processingMode; }
    public void setProcessingMode(String processingMode) { this.processingMode = processingMode; }

    public Boolean getDuplicateCat() { return duplicateCat; }
    public void setDuplicateCat(Boolean duplicateCat) { this.duplicateCat = duplicateCat; }
    public String getCatFaceId() { return catFaceId; }
    public void setCatFaceId(String catFaceId) { this.catFaceId = catFaceId; }
    public String getCatSnapshotB64() { return catSnapshotB64; }
    public void setCatSnapshotB64(String catSnapshotB64) { this.catSnapshotB64 = catSnapshotB64; }
    public String getFunnyTag() { return funnyTag; }
    public void setFunnyTag(String funnyTag) { this.funnyTag = funnyTag; }
    public Double getCatSimilarity() { return catSimilarity; }
    public void setCatSimilarity(Double catSimilarity) { this.catSimilarity = catSimilarity; }
    public Integer getCatDailyFeedCount() { return catDailyFeedCount; }
    public void setCatDailyFeedCount(Integer catDailyFeedCount) { this.catDailyFeedCount = catDailyFeedCount; }
    public Long getFeederLockSecondsRemaining() { return feederLockSecondsRemaining; }
    public void setFeederLockSecondsRemaining(Long feederLockSecondsRemaining) { this.feederLockSecondsRemaining = feederLockSecondsRemaining; }
}
