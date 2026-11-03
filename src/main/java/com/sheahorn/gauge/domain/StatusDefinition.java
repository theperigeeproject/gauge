package com.sheahorn.gauge.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A configurable status. Replaces the hardcoded per-level enums as the source of truth
 * (the enums survive only as seed data, see {@code StatusSeedStartup}).
 *
 * <p>{@code code} is what lands in {@code issues.status} / {@code tasklists.status} /
 * {@code tasks.status}. {@code category} and {@code rank} carry the behaviour: analysis
 * reads the category, sorting and display read the rank.
 */
@Entity
@Table(name = "status_definitions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"level", "code"}))
public class StatusDefinition extends PanacheEntityBase {

    @Id
    @Column(name = "id", length = 36)
    public String id;

    @Column(name = "code", nullable = false, length = 32)
    public String code;

    @Column(name = "label", nullable = false, length = 64)
    public String label;

    @Column(name = "level", nullable = false, length = 16)
    public String level;

    @Column(name = "category", nullable = false, length = 16)
    public String category;

    @Column(name = "rank", nullable = false)
    public int rank;

    @Column(name = "color", nullable = false, length = 7)
    public String color;

    @Column(name = "showByDefault", nullable = false)
    public boolean showByDefault;

    /** Built-in statuses are seeded and protected from deletion. */
    @Column(name = "builtin", nullable = false)
    public boolean builtin;

    public StatusDefinition() {
    }

    public StatusDefinition(String id, String code, String label, StatusLevel level,
                            StatusCategory category, int rank, String color,
                            boolean showByDefault, boolean builtin) {
        this.id = id;
        this.code = code;
        this.label = label;
        this.level = level.name();
        this.category = category.name();
        this.rank = rank;
        this.color = color;
        this.showByDefault = showByDefault;
        this.builtin = builtin;
    }

    public String id() { return id; }
    public String code() { return code; }
    public String label() { return label; }
    public StatusLevel level() { return StatusLevel.valueOf(level); }
    public StatusCategory category() { return StatusCategory.valueOf(category); }
    public int rank() { return rank; }
    public String color() { return color; }
    public boolean showByDefault() { return showByDefault; }
    public boolean builtin() { return builtin; }

    public static StatusDefinition create(String code, String label, StatusLevel level,
                                          StatusCategory category, int rank, String color,
                                          boolean showByDefault, boolean builtin) {
        return new StatusDefinition(
            IdProviderHolder.provider().nextId(), code, label, level, category, rank,
            color, showByDefault, builtin);
    }
}
