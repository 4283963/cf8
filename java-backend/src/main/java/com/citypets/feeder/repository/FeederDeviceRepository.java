package com.citypets.feeder.repository;

import com.citypets.feeder.entity.FeederDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FeederDeviceRepository extends JpaRepository<FeederDevice, Long> {
    Optional<FeederDevice> findByFeederId(String feederId);
    boolean existsByFeederId(String feederId);
}
