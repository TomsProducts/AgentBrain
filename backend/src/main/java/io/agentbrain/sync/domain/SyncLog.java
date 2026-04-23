package io.agentbrain.sync.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "sync_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 1000)
    private String path;

    @Column(length = 64)
    private String contentHash;

    @Column(nullable = false)
    private Long ts;

    @Column(nullable = false, length = 20)
    private String source;

    private Boolean deleted;

    private LocalDateTime syncedAt;
}
