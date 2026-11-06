package pl.perigee.gauge.domain;

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

    @Column(name = "description", length = 4000)
    public String description;

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
        @JsonProperty("status") IssueStatus status,
        @JsonProperty("priority") Priority priority
    ) {
        this.id = id;
        this.projectId = projectId;
        this.title = title;
        this.description = description;
        this.status = status.name();
        this.priority = priority.name();
    }

    public String id() { return id; }
    public String projectId() { return projectId; }
    public String title() { return title; }
    public String description() { return description; }
    public IssueStatus status() { return IssueStatus.valueOf(status); }
    public Priority priority() { return Priority.valueOf(priority); }

    public static Issue create(String projectId, String title, String description, Priority priority) {
        return new Issue(
            IdProviderHolder.provider().nextId(),
            projectId,
            title,
            description,
            IssueStatus.TODO,
            priority
        );
    }
}
