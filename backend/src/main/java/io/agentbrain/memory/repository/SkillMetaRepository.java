package io.agentbrain.memory.repository;

import io.agentbrain.memory.domain.SkillMeta;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillMetaRepository extends JpaRepository<SkillMeta, Long> {
}
