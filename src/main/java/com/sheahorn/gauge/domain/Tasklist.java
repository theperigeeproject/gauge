package com.sheahorn.gauge.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tasklists")
public class Tasklist extends PanacheEntityBase {

    @Id
    @Column(name = "id", length = 36)
    public String id;

    @Column(name = "issueId", nullable = false, length = 36)
    public String issueId;

    @Column(name = "title", nullable = false)
    public String title;

    /** A code from the configurable TASKLIST status set, not an enum. */
    @Column(name = "status", nullable = false, length = 20)
    public String status;

    @Column(name = "decomposesTaskId", length = 36)
    public String decomposesTaskId;

    public Tasklist() {
    }

    @JsonCreator
    public Tasklist(
        @JsonProperty("id") String id,
        @JsonProperty("issueId") String issueId,
        @JsonProperty("title") String title,
        @JsonProperty("status") String status,
        @JsonProperty("decomposesTaskId") String decomposesTaskId
    ) {
        this.id = id;
        this.issueId = issueId;
        this.title = title;
        this.status = status;
        this.decomposesTaskId = decomposesTaskId;
    }

    /** Convenience for callers holding a built-in status constant. */
    public Tasklist(String id, String issueId, String title, TasklistStatus status, String decomposesTaskId) {
        this(id, issueId, title, status.name(), decomposesTaskId);
    }

    public String id() { return id; }
    public String issueId() { return issueId; }
    public String title() { return title; }
    public String status() { return status; }
    public String decomposesTaskId() { return decomposesTaskId; }

    public static Tasklist create(String issueId, String title, String decomposesTaskId) {
        return new Tasklist(
            IdProviderHolder.provider().nextId(),
            issueId,
            title,
            StatusDefaults.START_CODE,
            decomposesTaskId
        );
    }
}
