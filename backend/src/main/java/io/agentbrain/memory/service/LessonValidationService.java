package io.agentbrain.memory.service;

import io.agentbrain.memory.domain.Lesson;
import io.agentbrain.memory.repository.LessonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LessonValidationService {

    private final LessonRepository lessonRepository;

    public ValidationResult validate(String claim) {
        if (claim == null || claim.isBlank()) {
            return ValidationResult.invalid("Claim must not be blank");
        }
        if (lessonRepository.existsByClaimIgnoreCase(claim.trim())) {
            return ValidationResult.invalid("A lesson with this exact claim already exists");
        }
        return ValidationResult.ok();
    }

    public record ValidationResult(boolean isValid, String reason) {
        public static ValidationResult ok() {
            return new ValidationResult(true, null);
        }
        public static ValidationResult invalid(String reason) {
            return new ValidationResult(false, reason);
        }
    }
}
