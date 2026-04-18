package io.agentbrain.memory.service;

import io.agentbrain.events.AgentEventPublisher;
import io.agentbrain.memory.domain.Lesson;
import io.agentbrain.memory.repository.LessonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewStateService {

    private final LessonRepository lessonRepository;
    private final AgentEventPublisher eventPublisher;

    @Transactional
    public Lesson graduate(Long id, String rationale) {
        if (rationale == null || rationale.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rationale is required to graduate a lesson");
        }
        Lesson lesson = findOrThrow(id);
        if (lesson.getStatus() != Lesson.LessonStatus.STAGED && lesson.getStatus() != Lesson.LessonStatus.REOPENED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only STAGED or REOPENED lessons can be graduated");
        }
        lesson.setStatus(Lesson.LessonStatus.ACCEPTED);
        lesson.setRationale(rationale);
        lesson.setGraduatedAt(LocalDateTime.now());
        Lesson saved = lessonRepository.save(lesson);
        eventPublisher.publish("LESSON_GRADUATED", "Lesson graduated: " + lesson.getClaim());
        return saved;
    }

    @Transactional
    public Lesson reject(Long id, String reason) {
        Lesson lesson = findOrThrow(id);
        if (lesson.getStatus() != Lesson.LessonStatus.STAGED && lesson.getStatus() != Lesson.LessonStatus.REOPENED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only STAGED or REOPENED lessons can be rejected");
        }
        lesson.setStatus(Lesson.LessonStatus.REJECTED);
        if (reason != null && !reason.isBlank()) {
            lesson.setConditions("Rejected: " + reason);
        }
        Lesson saved = lessonRepository.save(lesson);
        eventPublisher.publish("LESSON_REJECTED", "Lesson rejected: " + lesson.getClaim());
        return saved;
    }

    @Transactional
    public Lesson reopen(Long id) {
        Lesson lesson = findOrThrow(id);
        if (lesson.getStatus() != Lesson.LessonStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only REJECTED lessons can be reopened");
        }
        lesson.setStatus(Lesson.LessonStatus.STAGED);
        Lesson saved = lessonRepository.save(lesson);
        eventPublisher.publish("LESSON_REOPENED", "Lesson reopened: " + lesson.getClaim());
        return saved;
    }

    private Lesson findOrThrow(Long id) {
        return lessonRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found: " + id));
    }
}
