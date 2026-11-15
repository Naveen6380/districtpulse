package org.example.districtpulse.source;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SyncLogRepository extends JpaRepository<SyncLogEntity, Long> {
    List<SyncLogEntity> findTop20BySourceIdOrderByStartedAtDesc(Long sourceId);
}