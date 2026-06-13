package com.citypets.feeder.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

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

    @Column(name = "cat_face_id", length = 64)
    private String catFaceId;

    @Column(name = "cat_face_hash", length = 64)
    private String catFaceHash;

    @Column(name = "cat_snapshot_b64", columnDefinition = "LONGTEXT")
    @Lob
    private String catSnapshotB64;

    @Column(name = "cat_similarity")
    private Double catSimilarity;

    @Column(name = "cat_daily_feed_count")
    private Integer catDailyFeedCount;

    @Column(name = "duplicate_cat_feed", columnDefinition = "TINYINT(1) DEFAULT 0")
    private Boolean duplicateCatFeed = false;

    @Column(name = "funny_tag", length = 50)
    private String funnyTag;

    public enum AnimalType {
        STRAY_CAT, STRAY_DOG, PEST_ANIMAL, UNKNOWN
    }

    public enum DoorAction {
        NONE, OPEN_FOR_FEEDING, LOCK, UNLOCK
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFeederId() { return feederId; }
    public void setFeederId(String feederId) { this.feederId = feederId; }
    public AnimalType getAnimalType() { return animalType; }
    public void setAnimalType(AnimalType animalType) { this.animalType = animalType; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public Integer getFoodDispensedGrams() { return foodDispensedGrams; }
    public void setFoodDispensedGrams(Integer foodDispensedGrams) { this.foodDispensedGrams = foodDispensedGrams; }
    public Integer getFoodBeforeGrams() { return foodBeforeGrams; }
    public void setFoodBeforeGrams(Integer foodBeforeGrams) { this.foodBeforeGrams = foodBeforeGrams; }
    public Integer getFoodAfterGrams() { return foodAfterGrams; }
    public void setFoodAfterGrams(Integer foodAfterGrams) { this.foodAfterGrams = foodAfterGrams; }
    public Boolean getFeedingSuccess() { return feedingSuccess; }
    public void setFeedingSuccess(Boolean feedingSuccess) { this.feedingSuccess = feedingSuccess; }
    public DoorAction getDoorAction() { return doorAction; }
    public void setDoorAction(DoorAction doorAction) { this.doorAction = doorAction; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public LocalDateTime getDetectionTimestamp() { return detectionTimestamp; }
    public void setDetectionTimestamp(LocalDateTime detectionTimestamp) { this.detectionTimestamp = detectionTimestamp; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public String getCatFaceId() { return catFaceId; }
    public void setCatFaceId(String catFaceId) { this.catFaceId = catFaceId; }
    public String getCatFaceHash() { return catFaceHash; }
    public void setCatFaceHash(String catFaceHash) { this.catFaceHash = catFaceHash; }
    public String getCatSnapshotB64() { return catSnapshotB64; }
    public void setCatSnapshotB64(String catSnapshotB64) { this.catSnapshotB64 = catSnapshotB64; }
    public Double getCatSimilarity() { return catSimilarity; }
    public void setCatSimilarity(Double catSimilarity) { this.catSimilarity = catSimilarity; }
    public Integer getCatDailyFeedCount() { return catDailyFeedCount; }
    public void setCatDailyFeedCount(Integer catDailyFeedCount) { this.catDailyFeedCount = catDailyFeedCount; }
    public Boolean getDuplicateCatFeed() { return duplicateCatFeed; }
    public void setDuplicateCatFeed(Boolean duplicateCatFeed) { this.duplicateCatFeed = duplicateCatFeed; }
    public String getFunnyTag() { return funnyTag; }
    public void setFunnyTag(String funnyTag) { this.funnyTag = funnyTag; }
}
