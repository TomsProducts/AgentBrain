package io.agentbrain.memory.service;

import io.agentbrain.memory.domain.Lesson;
import io.agentbrain.memory.repository.LessonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LessonRenderService {

    private final LessonRepository lessonRepository;

    @Transactional(readOnly = true)
    public String renderAcceptedLessons() {
        List<Lesson> accepted = lessonRepository.findByStatusOrderBySalienceDesc(Lesson.LessonStatus.ACCEPTED);
        if (accepted.isEmpty()) {
            return "# Lessons\n\n_No accepted lessons yet._\n";
        }
        StringBuilder sb = new StringBuilder("# Lessons\n\n");
        for (Lesson lesson : accepted) {
            sb.append("## ").append(lesson.getClaim()).append("\n");
            if (lesson.getConditions() != null && !lesson.getConditions().isBlank()) {
                sb.append("**Conditions:** ").append(lesson.getConditions()).append("\n");
            }
            if (lesson.getRationale() != null && !lesson.getRationale().isBlank()) {
                sb.append("**Rationale:** ").append(lesson.getRationale()).append("\n");
            }
            sb.append("**Salience:** ").append(String.format("%.2f", lesson.getSalience())).append("\n\n");
        }
        return sb.toString();
    }
}
