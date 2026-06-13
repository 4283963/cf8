package com.citypets.feeder.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "feeder_device")
public class FeederDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "feeder_id", unique = true, nullable = false, length = 50)
    private String feederId;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "location", length = 200)
    private String location;

    @Column(name = "food_capacity_grams")
    private Integer foodCapacityGrams = 5000;

    @Column(name = "food_remaining_grams")
    private Integer foodRemainingGrams = 5000;

    @Column(name = "food_percentage")
    private Double foodPercentage = 100.0;

    @Column(name = "status", length = 20)
    @Enumerated(EnumType.STRING)
    private FeederStatus status = FeederStatus.ONLINE;

    @Column(name = "door_locked", columnDefinition = "TINYINT(1) DEFAULT 0")
    private Boolean doorLocked = false;

    @Column(name = "lock_reason", length = 200)
    private String lockReason;

    @Column(name = "lock_until")
    private LocalDateTime lockUntil;

    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    public enum FeederStatus {
        ONLINE, OFFLINE, MAINTENANCE
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFeederId() { return feederId; }
    public void setFeederId(String feederId) { this.feederId = feederId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Integer getFoodCapacityGrams() { return foodCapacityGrams; }
    public void setFoodCapacityGrams(Integer foodCapacityGrams) { this.foodCapacityGrams = foodCapacityGrams; }
    public Integer getFoodRemainingGrams() { return foodRemainingGrams; }
    public void setFoodRemainingGrams(Integer foodRemainingGrams) { this.foodRemainingGrams = foodRemainingGrams; }
    public Double getFoodPercentage() { return foodPercentage; }
    public void setFoodPercentage(Double foodPercentage) { this.foodPercentage = foodPercentage; }
    public FeederStatus getStatus() { return status; }
    public void setStatus(FeederStatus status) { this.status = status; }
    public Boolean getDoorLocked() { return doorLocked; }
    public void setDoorLocked(Boolean doorLocked) { this.doorLocked = doorLocked; }
    public String getLockReason() { return lockReason; }
    public void setLockReason(String lockReason) { this.lockReason = lockReason; }
    public LocalDateTime getLockUntil() { return lockUntil; }
    public void setLockUntil(LocalDateTime lockUntil) { this.lockUntil = lockUntil; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
