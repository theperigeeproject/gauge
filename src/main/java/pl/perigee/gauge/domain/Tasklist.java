package pl.perigee.gauge.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tasklists")
public class Tasklist extends PanacheEntityBase implements StatusBearing {

    @Id
    @Column(name = "id", length = 36)
    public String id;

    @Column(name = "issueId", nullable = false, length = 36)
    public String issueId;

    @Column(name = "title", nullable = false)
    public String title;

    /** LEGACY, pre-migration status code. Read only by the migration (ADR 0020 §5). */
    @Column(name = "status", length = 20)
    public String status;

    /** The real status referent: an internal {@code TasklistStatusDef} id. Nullable until migrated. */
    @Column(name = "statusId")
    public Integer statusId;

    @Column(name = "decomposesTaskId", length = 36)
    public String decomposesTaskId;

    public Tasklist() {
    }

    @JsonCreator
    public Tasklist(
        @JsonProperty("id") String id,
        @JsonProperty("issueId") String issueId,
        @JsonProperty("title") String title,
        @JsonProperty("status") TasklistStatus status,
        @JsonProperty("decomposesTaskId") String decomposesTaskId
    ) {
        this.id = id;
        this.issueId = issueId;
        this.title = title;
        this.status = status.name();
        this.decomposesTaskId = decomposesTaskId;
    }

    public String id() { return id; }
    public String issueId() { return issueId; }
    public String title() { return title; }
    public TasklistStatus status() { return TasklistStatus.valueOf(status); }
    public String decomposesTaskId() { return decomposesTaskId; }

    @Override
    public Integer statusId() { return statusId; }

    @Override
    public void setStatusId(Integer statusId) { this.statusId = statusId; }

    @Override
    public String legacyStatusCode() { return status; }

    public static Tasklist create(String issueId, String title, String decomposesTaskId) {
        return new Tasklist(
            IdProviderHolder.provider().nextId(),
            issueId,
            title,
            TasklistStatus.TODO,
            decomposesTaskId
        );
    }
}
