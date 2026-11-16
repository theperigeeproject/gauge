package pl.perigee.gauge.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A tasklist status. Seed rows: {@code TODO}, {@code DOING}, {@code DONE}, {@code CANCELED}.
 *
 * <p>{@code CANCELED} is {@code FINALIZED}, so it inherits the hide-by-default rule that {@code DONE}
 * has today — a tasklist can become irrelevant without its issue being finished.
 */
@Entity
@Table(name = "tasklist_statuses", uniqueConstraints = {
    @UniqueConstraint(name = "uk_tasklist_statuses_code", columnNames = "code"),
    @UniqueConstraint(name = "uk_tasklist_statuses_precedence", columnNames = "precedenceValue")
})
public class TasklistStatusDef extends StatusBase {

    public TasklistStatusDef() {
    }

    public TasklistStatusDef(String code, int precedenceValue, String color,
                             String progressLevel, String attitudeType) {
        this.code = code;
        this.precedenceValue = precedenceValue;
        this.color = color;
        this.progressLevel = progressLevel;
        this.attitudeType = attitudeType;
    }
}
