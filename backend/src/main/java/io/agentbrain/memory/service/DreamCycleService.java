package io.agentbrain.memory.service;

import io.agentbrain.events.AgentEventPublisher;
import io.agentbrain.memory.domain.EpisodicMemory;
import io.agentbrain.memory.domain.Lesson;
import io.agentbrain.memory.repository.LessonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class DreamCycleService {

    private final EpisodicMemoryService episodicMemoryService;
    private final ClusteringService clusteringService;
    private final LessonValidationService lessonValidationService;
    private final LessonRepository lessonRepository;
    private final AgentEventPublisher eventPublisher;

    private volatile String lastRunResult = "Never run";
    private volatile LocalDateTime lastRunAt = null;

    @Scheduled(cron = "${agentbrain.dream.cron}")
    @Transactional
    public void runDream() {
        log.info("Dream cycle starting...");
        lastRunAt = LocalDateTime.now();

        // 1. Load unstaged episodic memories from last 24h
        List<EpisodicMemory> recent = episodicMemoryService.findUnstagedSince(
                LocalDateTime.now().minusHours(24));
        log.debug("Found {} unstaged episodes in last 24h", recent.size());

        if (recent.isEmpty()) {
            lastRunResult = "No episodes to process";
            eventPublisher.publish("DREAM_COMPLETE", lastRunResult);
            return;
        }

        // 2. Cluster them with ClusteringService (Jaccard, no LLM)
        Map<Integer, List<EpisodicMemory>> clusters = clusteringService.cluster(recent);
        log.debug("Clustered into {} groups", clusters.size());

        // 3. For each cluster: build candidate lesson from n-gram extraction
        AtomicInteger staged = new AtomicInteger(0);
        clusters.forEach((clusterId, episodes) -> {
            String claim = clusteringService.extractClaim(episodes);
            String patternId = UUID.randomUUID().toString().substring(0, 8);

            // 4. Validate (not blank, not exact duplicate)
            LessonValidationService.ValidationResult result = lessonValidationService.validate(claim);
            if (!result.isValid()) {
                log.debug("Skipping candidate '{}': {}", claim, result.reason());
                return;
            }

            // 5. Save as STAGED lesson
            Lesson lesson = Lesson.builder()
                    .claim(claim)
                    .status(Lesson.LessonStatus.STAGED)
                    .patternId(patternId)
                    .salience(1.0)
                    .createdAt(LocalDateTime.now())
                    .build();
            lessonRepository.save(lesson);
            staged.incrementAndGet();
        });

        // 6. Apply sigmoid salience decay to ACCEPTED lessons
        List<Lesson> accepted = lessonRepository.findByStatus(Lesson.LessonStatus.ACCEPTED);
        accepted.forEach(lesson -> {
            double decayed = sigmoidDecay(lesson.getSalience());
            lesson.setSalience(decayed);
            lessonRepository.save(lesson);
        });

        // 7. Mark episodes as staged
        episodicMemoryService.markAsStaged(recent.stream().map(EpisodicMemory::getId).toList());

        // 8. Publish event
        lastRunResult = "Staged " + staged.get() + " candidates from " + clusters.size() + " clusters";
        log.info("Dream cycle complete: {}", lastRunResult);
        eventPublisher.publish("DREAM_COMPLETE", lastRunResult);
    }

    private double sigmoidDecay(double salience) {
        // Sigmoid-shaped decay: high salience decays slowly, low decays faster
        return salience / (1 + 0.01 * (1 - salience));
    }

    public String getLastRunResult() {
        return lastRunResult;
    }

    public LocalDateTime getLastRunAt() {
        return lastRunAt;
    }
}
