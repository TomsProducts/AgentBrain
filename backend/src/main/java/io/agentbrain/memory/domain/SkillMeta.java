package io.agentbrain.memory.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "skill_meta")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillMeta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    private String filePath;

    private Integer failCount;

    private LocalDateTime lastUsedAt;

    private Boolean active;

    private LocalDateTime createdAt;
}
