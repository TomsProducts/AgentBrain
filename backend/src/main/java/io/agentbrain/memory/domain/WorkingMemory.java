package io.agentbrain.memory.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "working_memory")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkingMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    private String tags;

    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;
}
