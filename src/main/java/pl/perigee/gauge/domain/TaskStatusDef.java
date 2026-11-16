package pl.perigee.gauge.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A task status. Seed rows: {@code TODO}, {@code DOING}, {@code DONE}, {@code FAILED},
 * {@code CANCELED}.
 *
 * <p>{@code FAILED} is {@code HALTED} but {@code REGULAR} — a failed task is a blocker, not a parked
 * one, so it keeps bubbling priority. Only {@code EXCLUDED} statuses go quiet.
 */
@Entity
@Table(name = "task_statuses", uniqueConstraints = {
    @UniqueConstraint(name = "uk_task_statuses_code", columnNames = "code"),
    @UniqueConstraint(name = "uk_task_statuses_precedence", columnNames = "precedenceValue")
})
public class TaskStatusDef extends StatusBase {

    public TaskStatusDef() {
    }

    public TaskStatusDef(String code, int precedenceValue, String color,
                         String progressLevel, String attitudeType) {
        this.code = code;
        this.precedenceValue = precedenceValue;
        this.color = color;
        this.progressLevel = progressLevel;
        this.attitudeType = attitudeType;
    }
}
