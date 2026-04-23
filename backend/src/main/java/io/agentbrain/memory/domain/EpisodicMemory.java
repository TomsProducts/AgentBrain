package io.agentbrain.memory.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "episodic_memory")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EpisodicMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    private String tags;

    private Double salienceScore;

    private Boolean staged;

    private LocalDateTime occurredAt;

    private LocalDateTime expiresAt;
}
