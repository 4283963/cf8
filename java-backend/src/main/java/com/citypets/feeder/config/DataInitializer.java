package com.citypets.feeder.config;

import com.citypets.feeder.service.FeederService;
import com.citypets.feeder.dto.FeederDto;
import com.citypets.feeder.entity.FeederDevice;
import com.citypets.feeder.repository.FeederDeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final FeederDeviceRepository repo;
    private final FeederService feederService;

    public DataInitializer(FeederDeviceRepository repo, FeederService feederService) {
        this.repo = repo;
        this.feederService = feederService;
    }

    @Override
    public void run(String... args) {
        ensureFeeder("FEEDER-001", "阳光小区1号喂食机",
                "阳光小区东门-3号楼北侧", 5000);
        ensureFeeder("FEEDER-002", "阳光小区2号喂食机",
                "阳光小区西门-花园旁", 5000);
        ensureFeeder("FEEDER-003", "幸福里小区喂食机",
                "幸福里小区-中央广场南侧", 8000);
        log.info("Data initialization complete. Total feeders: {}", repo.count());
    }

    private void ensureFeeder(String id, String name, String location, int capacity) {
        if (repo.existsByFeederId(id)) {
            return;
        }
        FeederDto dto = FeederDto.builder()
                .feederId(id)
                .name(name)
                .location(location)
                .foodCapacityGrams(capacity)
                .foodRemainingGrams(capacity)
                .foodPercentage(100.0)
                .status(FeederDevice.FeederStatus.ONLINE)
                .doorLocked(false)
                .build();
        feederService.createOrUpdate(dto);
        log.info("Created default feeder: {}", id);
    }
}
