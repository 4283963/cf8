package com.citypets.feeder.dto;

import lombok.Data;

import java.util.Map;

@Data
public class AiSignalRequest {
    private String feederId;
    private String timestamp;
    private String animalType;
    private Double confidence;
    private Boolean aboveThreshold;
    private Map<String, Double> allProbabilities;
}
