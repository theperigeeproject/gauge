package pl.perigee.gauge.support;

import pl.perigee.gauge.domain.StatusLevel;
import pl.perigee.gauge.service.StatusService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Test-side shortcut for "give me the id of this status of this level".
 *
 * <p>Increment 4 of 0000186F deleted the status enums, because statuses are data (ADR 0020). Tests
 * therefore cannot name a status with a constant any more — they resolve a code, exactly like a
 * caller would, through {@link StatusService}. That keeps the production rule intact ("translation
 * happens in one place") and stops tests from hard-coding auto-increment ids, which would break the
 * moment a seed is inserted in a different order.
 */
@ApplicationScoped
public class StatusIds {

    @Inject
    StatusService statusService;

    public Integer project(String code) {
        return statusService.resolveForWrite(StatusLevel.PROJECT, code);
    }

    public Integer issue(String code) {
        return statusService.resolveForWrite(StatusLevel.ISSUE, code);
    }

    public Integer tasklist(String code) {
        return statusService.resolveForWrite(StatusLevel.TASKLIST, code);
    }

    public Integer task(String code) {
        return statusService.resolveForWrite(StatusLevel.TASK, code);
    }

    /**
     * The id of a status that EXISTS but may not be authorable right now (a flag-disabled
     * progress level, for instance). For fixtures that model rows written before the flag existed.
     */
    public Integer any(StatusLevel level, String code) {
        return statusService.find(level, code).orElseThrow().id;
    }
}
