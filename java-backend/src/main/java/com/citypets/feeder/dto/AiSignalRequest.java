package com.citypets.feeder.dto;

import java.util.Map;

public class AiSignalRequest {
    private String feederId;
    private String timestamp;
    private String animalType;
    private Double confidence;
    private Boolean aboveThreshold;
    private Map<String, Double> allProbabilities;

    private Boolean duplicateCat;
    private String catFaceId;
    private String catFaceHash;
    private String catSnapshotB64;
    private Double catLastSeenAgoSec;
    private Double catSimilarity;
    private Integer catDailyFeedCount;

    public String getFeederId() { return feederId; }
    public void setFeederId(String feederId) { this.feederId = feederId; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public String getAnimalType() { return animalType; }
    public void setAnimalType(String animalType) { this.animalType = animalType; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public Boolean getAboveThreshold() { return aboveThreshold; }
    public void setAboveThreshold(Boolean aboveThreshold) { this.aboveThreshold = aboveThreshold; }
    public Map<String, Double> getAllProbabilities() { return allProbabilities; }
    public void setAllProbabilities(Map<String, Double> allProbabilities) { this.allProbabilities = allProbabilities; }

    public Boolean getDuplicateCat() { return duplicateCat; }
    public void setDuplicateCat(Boolean duplicateCat) { this.duplicateCat = duplicateCat; }
    public String getCatFaceId() { return catFaceId; }
    public void setCatFaceId(String catFaceId) { this.catFaceId = catFaceId; }
    public String getCatFaceHash() { return catFaceHash; }
    public void setCatFaceHash(String catFaceHash) { this.catFaceHash = catFaceHash; }
    public String getCatSnapshotB64() { return catSnapshotB64; }
    public void setCatSnapshotB64(String catSnapshotB64) { this.catSnapshotB64 = catSnapshotB64; }
    public Double getCatLastSeenAgoSec() { return catLastSeenAgoSec; }
    public void setCatLastSeenAgoSec(Double catLastSeenAgoSec) { this.catLastSeenAgoSec = catLastSeenAgoSec; }
    public Double getCatSimilarity() { return catSimilarity; }
    public void setCatSimilarity(Double catSimilarity) { this.catSimilarity = catSimilarity; }
    public Integer getCatDailyFeedCount() { return catDailyFeedCount; }
    public void setCatDailyFeedCount(Integer catDailyFeedCount) { this.catDailyFeedCount = catDailyFeedCount; }
}
