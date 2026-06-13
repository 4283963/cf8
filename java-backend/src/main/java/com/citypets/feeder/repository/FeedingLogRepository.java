package com.citypets.feeder.repository;

import com.citypets.feeder.entity.FeedingLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FeedingLogRepository extends JpaRepository<FeedingLog, Long> {

    Page<FeedingLog> findByFeederIdOrderByCreateTimeDesc(String feederId, Pageable pageable);

    @Query("SELECT f FROM FeedingLog f WHERE f.feederId = :feederId " +
           "AND f.animalType = :type AND f.createTime >= :since ORDER BY f.createTime DESC")
    List<FeedingLog> findRecentByType(@Param("feederId") String feederId,
                                      @Param("type") FeedingLog.AnimalType type,
                                      @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(f) FROM FeedingLog f WHERE f.feederId = :feederId " +
           "AND f.animalType = com.citypets.feeder.entity.FeedingLog$AnimalType.PEST_ANIMAL " +
           "AND f.createTime >= :windowStart")
    long countRecentPestDetections(@Param("feederId") String feederId,
                                   @Param("windowStart") LocalDateTime windowStart);
}
