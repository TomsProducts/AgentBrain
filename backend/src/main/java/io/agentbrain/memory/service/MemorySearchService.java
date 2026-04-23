package io.agentbrain.memory.service;

import io.agentbrain.memory.domain.EpisodicMemory;
import io.agentbrain.memory.domain.Lesson;
import io.agentbrain.memory.domain.WorkingMemory;
import io.agentbrain.memory.repository.EpisodicMemoryRepository;
import io.agentbrain.memory.repository.LessonRepository;
import io.agentbrain.memory.repository.WorkingMemoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MemorySearchService {

    private final WorkingMemoryRepository workingMemoryRepository;
    private final EpisodicMemoryRepository episodicMemoryRepository;
    private final LessonRepository lessonRepository;

    @Transactional(readOnly = true)
    public SearchResult search(String query) {
        List<WorkingMemory> working = workingMemoryRepository.searchByContentOrTags(query);
        List<EpisodicMemory> episodic = episodicMemoryRepository.searchByContentOrTags(query);
        List<Lesson> lessons = lessonRepository.searchByClaimOrConditions(query);
        return new SearchResult(working, episodic, lessons);
    }

    public record SearchResult(
            List<WorkingMemory> workingMemory,
            List<EpisodicMemory> episodicMemory,
            List<Lesson> lessons
    ) {}
}
