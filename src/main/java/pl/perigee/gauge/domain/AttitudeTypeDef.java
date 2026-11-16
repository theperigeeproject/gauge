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
 * A fixed attitude — how a status is <em>treated</em> as opposed to where it sits in the workflow.
 * Seed values, never user-editable, no surface API (ADR 0020 §3).
 *
 * <p>{@code REGULAR 0, EXCLUDED 1, ARCHIVE 2, TRASH 3}.
 *
 * <ul>
 *   <li>{@code REGULAR} — ordinary; participates in counting and priority bubbling.</li>
 *   <li>{@code EXCLUDED} — parked, reproducing today's {@code INACTIVE}: never bubbles a priority,
 *       ranks last, still shown.</li>
 *   <li>{@code ARCHIVE} / {@code TRASH} — omitted from <b>all</b> entity reads. Rows are retained;
 *       they simply never surface. One predicate applies this, as close to persistence as possible
 *       without binding to H2.</li>
 * </ul>
 *
 * <p>Referenced by {@code name} for the same reason as {@link ProgressLevelDef}: the set is fixed,
 * so names are stable and the id is only ever a primary key.
 */
@Entity
@Table(name = "attitude_types", uniqueConstraints = {
    @UniqueConstraint(name = "uk_attitude_types_name", columnNames = "name")
})
public class AttitudeTypeDef extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Integer id;

    @Column(name = "name", nullable = false, length = 20)
    public String name;

    /** Fixed. */
    @Column(name = "ordinal", nullable = false)
    public int ordinal;

    public AttitudeTypeDef() {
    }

    public AttitudeTypeDef(String name, int ordinal) {
        this.name = name;
        this.ordinal = ordinal;
    }

    public Integer id() { return id; }
    public String name() { return name; }
    public int ordinal() { return ordinal; }

    @Override
    public String toString() {
        return "AttitudeTypeDef[" + name + "=" + ordinal + "]";
    }
}
