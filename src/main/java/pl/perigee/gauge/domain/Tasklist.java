package pl.perigee.gauge.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A tasklist. Carries a status <b>id</b>; the wire code is resolved by
 * {@code resource/dto/TasklistView} through {@code StatusService}.
 */
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

    /** LEGACY, pre-migration status code. Never written again after increment 4 (ADR 0020 §5). */
    @Column(name = "status", length = 20)
    public String status;

    /** The real status referent: an internal {@code TasklistStatusDef} id. Nullable until migrated. */
    @Column(name = "statusId")
    public Integer statusId;

    @Column(name = "decomposesTaskId", length = 36)
    public String decomposesTaskId;

    public Tasklist() {
    }

    public Tasklist(String id, String issueId, String title, Integer statusId,
                    String decomposesTaskId) {
        this.id = id;
        this.issueId = issueId;
        this.title = title;
        this.statusId = statusId;
        this.decomposesTaskId = decomposesTaskId;
    }

    public String id() { return id; }
    public String issueId() { return issueId; }
    public String title() { return title; }
    public String decomposesTaskId() { return decomposesTaskId; }

    @Override
    public Integer statusId() { return statusId; }

    @Override
    public void setStatusId(Integer statusId) { this.statusId = statusId; }

    @Override
    public String legacyStatusCode() { return status; }

    public static Tasklist create(String issueId, String title, String decomposesTaskId,
                                  Integer statusId) {
        return new Tasklist(
            IdProviderHolder.provider().nextId(),
            issueId,
            title,
            statusId,
            decomposesTaskId
        );
    }
}
