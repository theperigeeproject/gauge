package pl.perigee.gauge.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tasks")
public class Task extends PanacheEntityBase implements StatusBearing {

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

    /** LEGACY, pre-migration status code. Read only by the migration (ADR 0020 §5). */
    @Column(name = "status", length = 20)
    public String status;

    /** The real status referent: an internal {@code TaskStatusDef} id. Nullable until migrated. */
    @Column(name = "statusId")
    public Integer statusId;

    public Task() {
    }

    @JsonCreator
    public Task(
        @JsonProperty("id") String id,
        @JsonProperty("tasklistId") String tasklistId,
        @JsonProperty("ordinal") int ordinal,
        @JsonProperty("title") String title,
        @JsonProperty("description") String description,
        @JsonProperty("status") TaskStatus status
    ) {
        this.id = id;
        this.tasklistId = tasklistId;
        this.ordinal = ordinal;
        this.title = title;
        this.description = description;
        this.status = status.name();
    }

    public String id() { return id; }
    public String tasklistId() { return tasklistId; }
    public int ordinal() { return ordinal; }
    public String title() { return title; }
    public String description() { return description; }
    public TaskStatus status() { return TaskStatus.valueOf(status); }

    @Override
    public Integer statusId() { return statusId; }

    @Override
    public void setStatusId(Integer statusId) { this.statusId = statusId; }

    @Override
    public String legacyStatusCode() { return status; }

    public static Task create(String tasklistId, int ordinal, String title, String description) {
        return new Task(
            IdProviderHolder.provider().nextId(),
            tasklistId,
            ordinal,
            title,
            description,
            TaskStatus.TODO
        );
    }
}
