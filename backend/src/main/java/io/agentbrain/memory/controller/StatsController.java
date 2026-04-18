package io.agentbrain.memory.controller;

import io.agentbrain.memory.domain.Lesson;
import io.agentbrain.memory.repository.LessonRepository;
import io.agentbrain.memory.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final WorkingMemoryService workingMemoryService;
    private final EpisodicMemoryService episodicMemoryService;
    private final LessonRepository lessonRepository;
    private final DreamCycleService dreamCycleService;

    @GetMapping
    public Map<String, Object> stats() {
        return Map.of(
                "workingMemoryCount", workingMemoryService.count(),
                "episodicMemoryCount", episodicMemoryService.count(),
                "stagedLessons", lessonRepository.findByStatus(Lesson.LessonStatus.STAGED).size(),
                "acceptedLessons", lessonRepository.findByStatus(Lesson.LessonStatus.ACCEPTED).size(),
                "lastDreamRun", dreamCycleService.getLastRunAt() != null ? dreamCycleService.getLastRunAt().toString() : "never",
                "lastDreamResult", dreamCycleService.getLastRunResult()
        );
    }
}
