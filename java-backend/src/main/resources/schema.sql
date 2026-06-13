CREATE DATABASE IF NOT EXISTS stray_animal_feeder
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE stray_animal_feeder;

CREATE TABLE IF NOT EXISTS feeder_device (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    feeder_id VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100),
    location VARCHAR(200),
    food_capacity_grams INT DEFAULT 5000,
    food_remaining_grams INT DEFAULT 5000,
    food_percentage DOUBLE DEFAULT 100.0,
    status VARCHAR(20) DEFAULT 'ONLINE',
    door_locked TINYINT(1) DEFAULT 0,
    lock_reason VARCHAR(200),
    lock_until DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_feeder_id (feeder_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS feeding_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    feeder_id VARCHAR(50) NOT NULL,
    animal_type VARCHAR(30),
    confidence DOUBLE,
    food_dispensed_grams INT DEFAULT 0,
    food_before_grams INT,
    food_after_grams INT,
    feeding_success TINYINT(1) DEFAULT 0,
    door_action VARCHAR(20),
    failure_reason VARCHAR(500),
    detection_timestamp DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_feeder_time (feeder_id, create_time DESC),
    INDEX idx_animal_type (animal_type),
    INDEX idx_feeding_success (feeding_success)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO feeder_device (feeder_id, name, location, food_capacity_grams, food_remaining_grams, food_percentage) VALUES
('FEEDER-001', '阳光小区1号喂食机', '阳光小区东门-3号楼北侧', 5000, 5000, 100.0),
('FEEDER-002', '阳光小区2号喂食机', '阳光小区西门-花园旁', 5000, 4850, 97.0),
('FEEDER-003', '幸福里小区喂食机', '幸福里小区-中央广场南侧', 8000, 8000, 100.0)
ON DUPLICATE KEY UPDATE update_time = CURRENT_TIMESTAMP;
