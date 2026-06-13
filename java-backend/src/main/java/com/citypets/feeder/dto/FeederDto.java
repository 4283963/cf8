package com.citypets.feeder.dto;

import com.citypets.feeder.entity.FeederDevice;

import java.time.LocalDateTime;

public class FeederDto {
    private Long id;
    private String feederId;
    private String name;
    private String location;
    private Integer foodCapacityGrams;
    private Integer foodRemainingGrams;
    private Double foodPercentage;
    private FeederDevice.FeederStatus status;
    private Boolean doorLocked;
    private String lockReason;
    private LocalDateTime lockUntil;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public FeederDto() {}

    private FeederDto(Builder b) {
        this.id = b.id;
        this.feederId = b.feederId;
        this.name = b.name;
        this.location = b.location;
        this.foodCapacityGrams = b.foodCapacityGrams;
        this.foodRemainingGrams = b.foodRemainingGrams;
        this.foodPercentage = b.foodPercentage;
        this.status = b.status;
        this.doorLocked = b.doorLocked;
        this.lockReason = b.lockReason;
        this.lockUntil = b.lockUntil;
        this.createTime = b.createTime;
        this.updateTime = b.updateTime;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long id;
        private String feederId;
        private String name;
        private String location;
        private Integer foodCapacityGrams;
        private Integer foodRemainingGrams;
        private Double foodPercentage;
        private FeederDevice.FeederStatus status;
        private Boolean doorLocked;
        private String lockReason;
        private LocalDateTime lockUntil;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;

        public Builder id(Long v) { this.id = v; return this; }
        public Builder feederId(String v) { this.feederId = v; return this; }
        public Builder name(String v) { this.name = v; return this; }
        public Builder location(String v) { this.location = v; return this; }
        public Builder foodCapacityGrams(Integer v) { this.foodCapacityGrams = v; return this; }
        public Builder foodRemainingGrams(Integer v) { this.foodRemainingGrams = v; return this; }
        public Builder foodPercentage(Double v) { this.foodPercentage = v; return this; }
        public Builder status(FeederDevice.FeederStatus v) { this.status = v; return this; }
        public Builder doorLocked(Boolean v) { this.doorLocked = v; return this; }
        public Builder lockReason(String v) { this.lockReason = v; return this; }
        public Builder lockUntil(LocalDateTime v) { this.lockUntil = v; return this; }
        public Builder createTime(LocalDateTime v) { this.createTime = v; return this; }
        public Builder updateTime(LocalDateTime v) { this.updateTime = v; return this; }
        public FeederDto build() { return new FeederDto(this); }
    }

    public static FeederDto from(FeederDevice d) {
        if (d == null) return null;
        return FeederDto.builder()
                .id(d.getId())
                .feederId(d.getFeederId())
                .name(d.getName())
                .location(d.getLocation())
                .foodCapacityGrams(d.getFoodCapacityGrams())
                .foodRemainingGrams(d.getFoodRemainingGrams())
                .foodPercentage(d.getFoodPercentage())
                .status(d.getStatus())
                .doorLocked(d.getDoorLocked())
                .lockReason(d.getLockReason())
                .lockUntil(d.getLockUntil())
                .createTime(d.getCreateTime())
                .updateTime(d.getUpdateTime())
                .build();
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
    public FeederDevice.FeederStatus getStatus() { return status; }
    public void setStatus(FeederDevice.FeederStatus status) { this.status = status; }
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
