package pl.perigee.gauge.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A project lifecycle status. Seed rows: {@code OPENED}, {@code CLOSED}.
 *
 * <p>Projects gain a real stored status here because it is the substrate for archive and trashbin
 * later (ADR 0020; issue 000001BA). Two things must not be "cleaned up" later:
 *
 * <ul>
 *   <li><b>A project's status does not bubble.</b> A child's status never changes its parent's. The
 *       tree already aggregates work through counts and bubbled priority; lifecycle is per-node and
 *       set deliberately. A project can legitimately be CLOSED while a child holds a HIGH issue —
 *       that is a finding, not a contradiction.</li>
 *   <li><b>Status is lifecycle, bubbledPriority is attention.</b> Different axes, both stay.</li>
 * </ul>
 */
@Entity
@Table(name = "project_statuses", uniqueConstraints = {
    @UniqueConstraint(name = "uk_project_statuses_code", columnNames = "code"),
    @UniqueConstraint(name = "uk_project_statuses_precedence", columnNames = "precedenceValue")
})
public class ProjectStatusDef extends StatusBase {

    public ProjectStatusDef() {
    }

    public ProjectStatusDef(String code, int precedenceValue, String color,
                            String progressLevel, String attitudeType) {
        this.code = code;
        this.precedenceValue = precedenceValue;
        this.color = color;
        this.progressLevel = progressLevel;
        this.attitudeType = attitudeType;
    }
}
