package pl.perigee.gauge.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A project. Carries a status <b>id</b> — lifecycle, set deliberately, never derived and never
 * bubbled (ADR 0020: a child's status does not change its parent's).
 *
 * <p>Implements {@link StatusBearing} so that the view mapper, the helpers on {@code StatusService}
 * and any future exclusion predicate can treat all four levels uniformly.
 * {@link #legacyStatusCode()} always returns null: projects gained a status in increment 3 and
 * <b>never had</b> a legacy code column, so there is nothing to migrate them from — every existing
 * row was given {@code OPENED} by the migration.
 */
@Entity
@Table(name = "projects")
public class Project extends PanacheEntityBase implements StatusBearing {

    @Id
    @Column(name = "id", length = 36)
    public String id;

    @Column(name = "name", nullable = false)
    public String name;

    @Column(name = "description", length = 4000)
    public String description;

    @Column(name = "parentId", length = 36)
    public String parentId;

    @Column(name = "removalLock", length = 20)
    public String removalLock;

    /**
     * The project's lifecycle status — an internal {@code ProjectStatusDef} id (ADR 0020 §5).
     *
     * <p>Nullable in the schema because an upgraded database starts empty here; the API never
     * accepts null.
     */
    @Column(name = "statusId")
    public Integer statusId;

    public Project() {
    }

    public Project(String id, String name, String description, String parentId,
                   String removalLock, Integer statusId) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.parentId = parentId;
        this.removalLock = removalLock != null ? removalLock : "";
        this.statusId = statusId;
    }

    public String id() { return id; }
    public String name() { return name; }
    public String description() { return description; }
    public String parentId() { return parentId; }
    public String removalLock() { return removalLock != null ? removalLock : ""; }

    @Override
    public Integer statusId() { return statusId; }

    @Override
    public void setStatusId(Integer statusId) { this.statusId = statusId; }

    /** Projects never had a legacy status column — see the class javadoc. */
    @Override
    public String legacyStatusCode() { return null; }

    public static Project create(String name, String description, String parentId,
                                 Integer statusId) {
        return new Project(
            IdProviderHolder.provider().nextId(),
            name,
            description,
            parentId,
            "",
            statusId
        );
    }
}
