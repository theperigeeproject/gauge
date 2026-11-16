package pl.perigee.gauge.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "projects")
public class Project extends PanacheEntityBase {

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
     * <p>Projects had no status before this, so there is no legacy code column to migrate from:
     * existing rows simply get the seeded {@code OPENED} status. Nullable in the schema because an
     * upgraded database starts empty here; the API never accepts null.
     *
     * <p><b>This does not bubble.</b> A child's status never changes its parent's — lifecycle is
     * per-node and set deliberately. See {@code ProjectStatusDef}.
     */
    @Column(name = "statusId")
    public Integer statusId;

    public Project() {
    }

    @JsonCreator
    public Project(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("parentId") String parentId,
        @JsonProperty("removalLock") String removalLock
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.parentId = parentId;
        this.removalLock = removalLock != null ? removalLock : "";
    }

    public String id() { return id; }
    public String name() { return name; }
    public String description() { return description; }
    public String parentId() { return parentId; }
    public String removalLock() { return removalLock != null ? removalLock : ""; }
    public Integer statusId() { return statusId; }

    public static Project create(String name, String description, String parentId) {
        return new Project(
            IdProviderHolder.provider().nextId(),
            name,
            description,
            parentId,
            ""
        );
    }
}
