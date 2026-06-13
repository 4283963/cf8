package com.citypets.feeder.dto;

import java.util.Map;

public class AiSignalRequest {
    private String feederId;
    private String timestamp;
    private String animalType;
    private Double confidence;
    private Boolean aboveThreshold;
    private Map<String, Double> allProbabilities;

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
}
