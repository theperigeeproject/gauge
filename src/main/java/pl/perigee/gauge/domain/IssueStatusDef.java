package pl.perigee.gauge.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * An issue status. Seed rows: {@code TODO}, {@code DOING}, {@code DONE}, {@code INACTIVE}.
 *
 * <p>Distinct from the {@link IssueStatus} enum, which still drives the write paths until the
 * switchover. The enum is deleted once the wire speaks codes exclusively — until then the two types
 * coexist and the {@code Def} suffix is what keeps them tellable apart at a glance.
 */
@Entity
@Table(name = "issue_statuses", uniqueConstraints = {
    @UniqueConstraint(name = "uk_issue_statuses_code", columnNames = "code"),
    @UniqueConstraint(name = "uk_issue_statuses_precedence", columnNames = "precedenceValue")
})
public class IssueStatusDef extends StatusBase {

    public IssueStatusDef() {
    }

    public IssueStatusDef(String code, int precedenceValue, String color,
                          String progressLevel, String attitudeType) {
        this.code = code;
        this.precedenceValue = precedenceValue;
        this.color = color;
        this.progressLevel = progressLevel;
        this.attitudeType = attitudeType;
    }
}
