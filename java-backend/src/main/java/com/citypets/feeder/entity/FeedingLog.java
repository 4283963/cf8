package com.citypets.feeder.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "feeding_log")
public class FeedingLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "feeder_id", nullable = false, length = 50)
    private String feederId;

    @Column(name = "animal_type", length = 30)
    @Enumerated(EnumType.STRING)
    private AnimalType animalType;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "food_dispensed_grams")
    private Integer foodDispensedGrams;

    @Column(name = "food_before_grams")
    private Integer foodBeforeGrams;

    @Column(name = "food_after_grams")
    private Integer foodAfterGrams;

    @Column(name = "feeding_success", columnDefinition = "TINYINT(1) DEFAULT 0")
    private Boolean feedingSuccess = false;

    @Column(name = "door_action", length = 20)
    @Enumerated(EnumType.STRING)
    private DoorAction doorAction;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "detection_timestamp")
    private LocalDateTime detectionTimestamp;

    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    public enum AnimalType {
        STRAY_CAT,
        STRAY_DOG,
        PEST_ANIMAL,
        UNKNOWN
    }

    public enum DoorAction {
        NONE,
        OPEN_FOR_FEEDING,
        LOCK,
        UNLOCK
    }
}
