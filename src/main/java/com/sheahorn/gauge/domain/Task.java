package com.sheahorn.gauge.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tasks")
public class Task extends PanacheEntityBase {

    @Id
    @Column(name = "id", length = 36)
    public String id;

    @Column(name = "tasklistId", nullable = false, length = 36)
    public String tasklistId;

    @Column(name = "ordinal", nullable = false)
    public int ordinal;

    @Column(name = "title", nullable = false)
    public String title;

    @Column(name = "description", length = 4000)
    public String description;

    /** A code from the configurable TASK status set, not an enum. */
    @Column(name = "status", nullable = false, length = 20)
    public String status;

    public Task() {
    }

    @JsonCreator
    public Task(
        @JsonProperty("id") String id,
        @JsonProperty("tasklistId") String tasklistId,
        @JsonProperty("ordinal") int ordinal,
        @JsonProperty("title") String title,
        @JsonProperty("description") String description,
        @JsonProperty("status") String status
    ) {
        this.id = id;
        this.tasklistId = tasklistId;
        this.ordinal = ordinal;
        this.title = title;
        this.description = description;
        this.status = status;
    }

    /** Convenience for callers holding a built-in status constant. */
    public Task(String id, String tasklistId, int ordinal, String title, String description, TaskStatus status) {
        this(id, tasklistId, ordinal, title, description, status.name());
    }

    public String id() { return id; }
    public String tasklistId() { return tasklistId; }
    public int ordinal() { return ordinal; }
    public String title() { return title; }
    public String description() { return description; }
    public String status() { return status; }

    public static Task create(String tasklistId, int ordinal, String title, String description) {
        return new Task(
            IdProviderHolder.provider().nextId(),
            tasklistId,
            ordinal,
            title,
            description,
            StatusDefaults.START_CODE
        );
    }
}
