package io.agentbrain.memory.service;

import io.agentbrain.memory.domain.EpisodicMemory;
import io.agentbrain.memory.domain.Lesson;
import io.agentbrain.memory.repository.EpisodicMemoryRepository;
import io.agentbrain.memory.repository.LessonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ContextBudgetService {

    private final EpisodicMemoryRepository episodicMemoryRepository;
    private final LessonRepository lessonRepository;

    @Value("${agentbrain.memory.context-budget-limit:20}")
    private int budgetLimit;

    @Transactional(readOnly = true)
    public ContextBudget buildContext(String query) {
        // Top-N episodes ranked by salience (× relevance if query provided)
        List<EpisodicMemory> topEpisodes = episodicMemoryRepository
                .findAllBySalienceDesc(PageRequest.of(0, budgetLimit));

        if (query != null && !query.isBlank()) {
            topEpisodes = rerankByRelevance(topEpisodes, query, budgetLimit);
        }

        // ACCEPTED lessons only — never STAGED, REJECTED, or REOPENED
        List<Lesson> acceptedLessons = lessonRepository
                .findByStatusOrderBySalienceDesc(Lesson.LessonStatus.ACCEPTED);

        return new ContextBudget(topEpisodes, acceptedLessons);
    }

    private List<EpisodicMemory> rerankByRelevance(List<EpisodicMemory> episodes, String query, int limit) {
        String[] queryTerms = query.toLowerCase().split("\\s+");
        return episodes.stream()
                .sorted(Comparator.comparingDouble(e -> -scoreRelevance(e, queryTerms)))
                .limit(limit)
                .toList();
    }

    private double scoreRelevance(EpisodicMemory ep, String[] terms) {
        String text = ep.getContent().toLowerCase();
        long matches = Arrays.stream(terms).filter(text::contains).count();
        double relevance = (double) matches / Math.max(terms.length, 1);
        return (ep.getSalienceScore() != null ? ep.getSalienceScore() : 1.0) * (1 + relevance);
    }

    public record ContextBudget(List<EpisodicMemory> episodes, List<Lesson> lessons) {}
}
