package pl.perigee.gauge.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

/**
 * Shared shape of a configurable status row. See ADR 0020.
 *
 * <p><b>This is a {@code @MappedSuperclass}, not an entity.</b> Exactly four entity classes extend
 * it — {@link ProjectStatusDef}, {@link IssueStatusDef}, {@link TasklistStatusDef},
 * {@link TaskStatusDef} — one table each. The four are identical today <em>on purpose</em>: the
 * whole point of reifying statuses is that one level can grow a property the others must not have.
 * Do not collapse them into one table with a level discriminator; that turns the next per-level
 * property into a nullable column that lies about which levels use it.
 *
 * <p><b>Identity.</b> The id is a database-generated int, deliberately outside {@code IdProvider}.
 * These ids exist only between persistence and the status service — they never appear in an API
 * field, a URL, a query parameter or an error body. ADR 5's hex-8 format exists so a human or an
 * LLM can read and quote an id reliably; nobody ever reads these, so they earn nothing from it.
 * Consequently these tables are <em>not</em> in {@code IdProvider}'s startup scan.
 *
 * <p><b>code is the surface identity.</b> Requests accept a code, responses return a code, and this
 * column <em>is</em> the display label — there is no separate name. Because entities reference the
 * id and not the code, renaming a status is free: no row rewriting anywhere.
 */
@MappedSuperclass
public abstract class StatusBase extends PanacheEntityBase {

    /** Internal surrogate key. Never serialised to a client. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Integer id;

    /** The human name and the wire identity. Unique within its own table. */
    @Column(name = "code", nullable = false, length = 40)
    public String code;

    /** Display and sort order, ascending. Unique within its own table. */
    @Column(name = "precedenceValue", nullable = false)
    public int precedenceValue;

    /** Display colour, hex. Cosmetic only — nothing branches on it. */
    @Column(name = "color", length = 20)
    public String color;

    /**
     * A {@code progress_levels.name}. Referenced by <b>name</b>, not by id: progress levels are a
     * fixed closed set, so their names are stable by definition (ADR 0020 §2/§3).
     */
    @Column(name = "progressLevel", nullable = false, length = 20)
    public String progressLevel;

    /** An {@code attitude_types.name}, referenced by name for the same reason. */
    @Column(name = "attitudeType", nullable = false, length = 20)
    public String attitudeType;

    public Integer id() { return id; }
    public String code() { return code; }
    public int precedenceValue() { return precedenceValue; }
    public String color() { return color; }
    public String progressLevel() { return progressLevel; }
    public String attitudeType() { return attitudeType; }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + code + " " + progressLevel + "/" + attitudeType + "]";
    }
}
