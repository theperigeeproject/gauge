package pl.perigee.gauge.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A fixed progress level — a high-level kanban column a status sits in. Seeded, never user-editable,
 * no surface API (ADR 0020 §3).
 *
 * <p>The six values and their ordinals are fixed:
 * {@code NASCENT 0, CREATED 1, ONGOING 2, HALTED 3, FINISHED 4, FINALIZED 5}.
 *
 * <p><b>Status rows reference this table by {@code name}, not by {@code id}</b> — it is a closed
 * set, so its names are stable. The id exists only because a table needs a primary key; nothing
 * points at it.
 *
 * <p>{@code FINISHED} and {@code FINALIZED} are what makes a status <em>terminal</em>, which is how
 * the old "DONE is hidden by default" rule survives without anyone branching on a status name.
 */
@Entity
@Table(name = "progress_levels", uniqueConstraints = {
    @UniqueConstraint(name = "uk_progress_levels_name", columnNames = "name")
})
public class ProgressLevelDef extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Integer id;

    @Column(name = "name", nullable = false, length = 20)
    public String name;

    /** Fixed. Ascending order in the UI. */
    @Column(name = "ordinal", nullable = false)
    public int ordinal;

    public ProgressLevelDef() {
    }

    public ProgressLevelDef(String name, int ordinal) {
        this.name = name;
        this.ordinal = ordinal;
    }

    public Integer id() { return id; }
    public String name() { return name; }
    public int ordinal() { return ordinal; }

    @Override
    public String toString() {
        return "ProgressLevelDef[" + name + "=" + ordinal + "]";
    }
}
