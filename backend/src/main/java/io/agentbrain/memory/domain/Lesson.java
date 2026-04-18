package io.agentbrain.memory.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "lessons")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String claim;

    @Column(columnDefinition = "TEXT")
    private String conditions;

    @Enumerated(EnumType.STRING)
    private LessonStatus status;

    private String patternId;

    @Column(columnDefinition = "TEXT")
    private String rationale;

    private Double salience;

    private LocalDateTime createdAt;

    private LocalDateTime graduatedAt;

    public enum LessonStatus {
        STAGED, ACCEPTED, REJECTED, REOPENED
    }
}
