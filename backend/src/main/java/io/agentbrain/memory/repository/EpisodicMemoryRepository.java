package io.agentbrain.memory.repository;

import io.agentbrain.memory.domain.EpisodicMemory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface EpisodicMemoryRepository extends JpaRepository<EpisodicMemory, Long> {

    Page<EpisodicMemory> findAllByOrderByOccurredAtDesc(Pageable pageable);

    @Query("SELECT e FROM EpisodicMemory e WHERE e.staged = false AND e.occurredAt >= :since")
    List<EpisodicMemory> findUnstagdSince(LocalDateTime since);

    @Query("SELECT e FROM EpisodicMemory e WHERE LOWER(e.content) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(e.tags) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<EpisodicMemory> searchByContentOrTags(String query);

    @Query("SELECT e FROM EpisodicMemory e ORDER BY e.salienceScore DESC")
    List<EpisodicMemory> findAllBySalienceDesc(Pageable pageable);
}
