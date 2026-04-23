package io.agentbrain.memory.service;

import io.agentbrain.events.AgentEventPublisher;
import io.agentbrain.memory.domain.WorkingMemory;
import io.agentbrain.memory.repository.WorkingMemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkingMemoryService {

    private final WorkingMemoryRepository repository;
    private final AgentEventPublisher eventPublisher;

    @Value("${agentbrain.memory.working-ttl-hours:24}")
    private int ttlHours;

    @Transactional(readOnly = true)
    public List<WorkingMemory> findAll() {
        return repository.findActive(LocalDateTime.now());
    }

    @Transactional
    public WorkingMemory create(String content, String tags) {
        WorkingMemory memory = WorkingMemory.builder()
                .content(content)
                .tags(tags)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(ttlHours))
                .build();
        WorkingMemory saved = repository.save(memory);
        eventPublisher.publish("MEMORY_WRITE", "Working memory added");
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    @Transactional
    @Scheduled(fixedDelay = 3600000) // every hour
    public void evictExpired() {
        List<WorkingMemory> expired = repository.findByExpiresAtBeforeOrExpiresAtIsNull(LocalDateTime.now());
        // filter only truly expired (not null expiresAt)
        expired.stream()
                .filter(m -> m.getExpiresAt() != null && m.getExpiresAt().isBefore(LocalDateTime.now()))
                .forEach(m -> repository.deleteById(m.getId()));
        log.debug("Evicted expired working memories");
    }

    @Transactional(readOnly = true)
    public long count() {
        return repository.findActive(LocalDateTime.now()).size();
    }

    @Transactional(readOnly = true)
    public List<WorkingMemory> search(String query) {
        return repository.searchByContentOrTags(query);
    }
}
