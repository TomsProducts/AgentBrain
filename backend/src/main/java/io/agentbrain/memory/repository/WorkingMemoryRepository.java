package io.agentbrain.memory.repository;

import io.agentbrain.memory.domain.WorkingMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface WorkingMemoryRepository extends JpaRepository<WorkingMemory, Long> {

    List<WorkingMemory> findByExpiresAtBeforeOrExpiresAtIsNull(LocalDateTime now);

    @Query("SELECT w FROM WorkingMemory w WHERE w.expiresAt IS NULL OR w.expiresAt > :now")
    List<WorkingMemory> findActive(LocalDateTime now);

    @Query("SELECT w FROM WorkingMemory w WHERE LOWER(w.content) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(w.tags) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<WorkingMemory> searchByContentOrTags(String query);
}
