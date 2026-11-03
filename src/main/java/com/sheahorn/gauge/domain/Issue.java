package com.sheahorn.gauge.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "issues")
public class Issue extends PanacheEntityBase {

    @Id
    @Column(name = "id", length = 36)
    public String id;

    @Column(name = "projectId", nullable = false, length = 36)
    public String projectId;

    @Column(name = "title", nullable = false)
    public String title;

    // Width matches IssueDescriptionPolicy.HARD_MAX_LENGTH — the app-level cap is the real limit,
    // the column must never be the thing that fails a request.
    @Column(name = "description", length = 1000000)
    public String description;

    /** A code from the configurable ISSUE status set (status_definitions), not an enum. */
    @Column(name = "status", nullable = false, length = 20)
    public String status;

    @Column(name = "priority", nullable = false, length = 20)
    public String priority;

    public Issue() {
    }

    @JsonCreator
    public Issue(
        @JsonProperty("id") String id,
        @JsonProperty("projectId") String projectId,
        @JsonProperty("title") String title,
        @JsonProperty("description") String description,
        @JsonProperty("status") String status,
        @JsonProperty("priority") Priority priority
    ) {
        this.id = id;
        this.projectId = projectId;
        this.title = title;
        this.description = description;
        this.status = status;
        this.priority = priority.name();
    }

    /** Convenience for callers holding a built-in status constant. */
    public Issue(String id, String projectId, String title, String description,
                 IssueStatus status, Priority priority) {
        this(id, projectId, title, description, status.name(), priority);
    }

    public String id() { return id; }
    public String projectId() { return projectId; }
    public String title() { return title; }
    public String description() { return description; }
    public String status() { return status; }
    public Priority priority() { return Priority.valueOf(priority); }

    public static Issue create(String projectId, String title, String description, Priority priority) {
        return new Issue(
            IdProviderHolder.provider().nextId(),
            projectId,
            title,
            description,
            StatusDefaults.START_CODE,
            priority
        );
    }
}
