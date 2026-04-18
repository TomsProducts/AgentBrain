package io.agentbrain.memory.controller;

import io.agentbrain.memory.domain.Lesson;
import io.agentbrain.memory.repository.LessonRepository;
import io.agentbrain.memory.service.ReviewStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/lessons")
@RequiredArgsConstructor
public class LessonController {

    private final LessonRepository lessonRepository;
    private final ReviewStateService reviewStateService;

    @GetMapping
    public List<Lesson> list(@RequestParam(required = false) String status) {
        if (status == null || status.isBlank()) {
            return lessonRepository.findAll();
        }
        return lessonRepository.findByStatus(Lesson.LessonStatus.valueOf(status.toUpperCase()));
    }

    @PostMapping("/{id}/graduate")
    public Lesson graduate(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String rationale = body.get("rationale");
        return reviewStateService.graduate(id, rationale);
    }

    @PostMapping("/{id}/reject")
    public Lesson reject(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return reviewStateService.reject(id, reason);
    }

    @PostMapping("/{id}/reopen")
    public Lesson reopen(@PathVariable Long id) {
        return reviewStateService.reopen(id);
    }
}
