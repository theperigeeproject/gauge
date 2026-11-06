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
