package com.campusfix.sla;

import com.campusfix.request.Priority;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SlaConfigRepository extends JpaRepository<SlaConfig, Long> {

    Optional<SlaConfig> findByPriority(Priority priority);

    boolean existsByPriority(Priority priority);
}
