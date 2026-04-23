package io.agentbrain.sync.repository;

import io.agentbrain.sync.domain.SyncLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SyncLogRepository extends JpaRepository<SyncLog, Long> {

    List<SyncLog> findBySourceAndTsGreaterThan(String source, Long ts);
}
