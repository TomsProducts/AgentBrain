package io.agentbrain.memory.repository;

import io.agentbrain.memory.domain.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

    List<Lesson> findByStatus(Lesson.LessonStatus status);

    List<Lesson> findByStatusOrderBySalienceDesc(Lesson.LessonStatus status);

    boolean existsByClaimIgnoreCase(String claim);

    @Query("SELECT l FROM Lesson l WHERE LOWER(l.claim) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(l.conditions) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Lesson> searchByClaimOrConditions(String query);
}
