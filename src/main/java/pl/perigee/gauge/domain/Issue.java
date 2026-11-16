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
public class Issue extends PanacheEntityBase implements StatusBearing {

    @Id
    @Column(name = "id", length = 36)
    public String id;

    @Column(name = "projectId", nullable = false, length = 36)
    public String projectId;

    @Column(name = "title", nullable = false)
    public String title;

    // Width matches IssueDescriptionPolicy.HARD_MAX_LENGTH. The application-level cap
    // (4000 by default, lifted by gauge.issue.description.allow-long) is enforced before
    // this column is ever reached, so the column is never the thing that fails a request.
    @Column(name = "description", length = 1_000_000)
    public String description;

    /**
     * LEGACY, pre-migration status code. Untouched by the new model and never written again.
     * Kept because renaming a column is something Hibernate's {@code update} cannot do (ADR 0020 §5).
     * Read only by {@code StatusDataMigrationStartup}.
     */
    @Column(name = "status", length = 20)
    public String status;

    /**
     * The real status referent: an internal {@code IssueStatusDef} id (ADR 0020 §2/§5).
     * Nullable because an upgraded database starts empty here; the API never accepts null.
     */
    @Column(name = "statusId")
    public Integer statusId;

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

    @Override
    public Integer statusId() { return statusId; }

    @Override
    public void setStatusId(Integer statusId) { this.statusId = statusId; }

    @Override
    public String legacyStatusCode() { return status; }

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
