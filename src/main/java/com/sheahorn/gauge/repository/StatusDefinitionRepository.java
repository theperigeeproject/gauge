package com.sheahorn.gauge.repository;

import com.sheahorn.gauge.domain.StatusDefinition;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class StatusDefinitionRepository {

    @Transactional
    public StatusDefinition save(StatusDefinition definition) {
        Optional<StatusDefinition> existing = StatusDefinition.findByIdOptional(definition.id);
        if (existing.isPresent()) {
            StatusDefinition managed = existing.get();
            managed.code = definition.code;
            managed.label = definition.label;
            managed.level = definition.level;
            managed.category = definition.category;
            managed.rank = definition.rank;
            managed.color = definition.color;
            managed.showByDefault = definition.showByDefault;
            managed.persist();
            return managed;
        }
        definition.persist();
        return definition;
    }

    public Optional<StatusDefinition> findById(String id) {
        return StatusDefinition.findByIdOptional(id);
    }

    public List<StatusDefinition> findAll() {
        return StatusDefinition.list("order by level, rank, code");
    }

    public List<StatusDefinition> findByLevel(String level) {
        return StatusDefinition.list("level = ?1 order by rank, code", level);
    }

    public Optional<StatusDefinition> findByLevelAndCode(String level, String code) {
        return StatusDefinition.find("level = ?1 and code = ?2", level, code).firstResultOptional();
    }

    public long countAll() {
        return StatusDefinition.count();
    }

    @Transactional
    public void deleteById(String id) {
        StatusDefinition.deleteById(id);
    }
}
