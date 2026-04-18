package io.agentbrain.memory.service;

import io.agentbrain.events.AgentEventPublisher;
import io.agentbrain.memory.domain.EpisodicMemory;
import io.agentbrain.memory.repository.EpisodicMemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EpisodicMemoryService {

    private final EpisodicMemoryRepository repository;
    private final AgentEventPublisher eventPublisher;

    @Value("${agentbrain.memory.episodic-ttl-days:90}")
    private int ttlDays;

    @Transactional(readOnly = true)
    public Page<EpisodicMemory> findAll(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return repository.findAllByOrderByOccurredAtDesc(pageable);
    }

    @Transactional
    public EpisodicMemory create(String content, String tags) {
        EpisodicMemory memory = EpisodicMemory.builder()
                .content(content)
                .tags(tags)
                .salienceScore(1.0)
                .staged(false)
                .occurredAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(ttlDays))
                .build();
        EpisodicMemory saved = repository.save(memory);
        eventPublisher.publish("MEMORY_WRITE", "Episodic memory added");
        return saved;
    }

    @Transactional(readOnly = true)
    public List<EpisodicMemory> findUnstagedSince(LocalDateTime since) {
        return repository.findUnstagdSince(since);
    }

    @Transactional
    public void markAsStaged(List<Long> ids) {
        ids.forEach(id -> repository.findById(id).ifPresent(e -> {
            e.setStaged(true);
            repository.save(e);
        }));
    }

    @Transactional
    public void applySalienceDecay(List<EpisodicMemory> episodes) {
        episodes.forEach(e -> {
            double decayed = e.getSalienceScore() * 0.99;
            e.setSalienceScore(decayed);
            repository.save(e);
        });
    }

    @Transactional
    @Scheduled(fixedDelay = 86400000) // every 24h
    public void evictExpired() {
        repository.findAll().stream()
                .filter(e -> e.getExpiresAt() != null && e.getExpiresAt().isBefore(LocalDateTime.now()))
                .forEach(e -> repository.deleteById(e.getId()));
        log.debug("Evicted expired episodic memories");
    }

    @Transactional(readOnly = true)
    public long count() {
        return repository.count();
    }

    @Transactional(readOnly = true)
    public List<EpisodicMemory> search(String query) {
        return repository.searchByContentOrTags(query);
    }

    @Transactional(readOnly = true)
    public List<EpisodicMemory> findTopBySalience(int limit) {
        return repository.findAllBySalienceDesc(PageRequest.of(0, limit));
    }
}
