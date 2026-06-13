package com.citypets.feeder.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
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
        ONLINE,
        OFFLINE,
        MAINTENANCE
    }
}
