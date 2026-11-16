package pl.perigee.gauge.service;

import pl.perigee.gauge.domain.Issue;
import pl.perigee.gauge.domain.Project;
import pl.perigee.gauge.domain.StatusBase;
import pl.perigee.gauge.domain.StatusLevel;
import pl.perigee.gauge.domain.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Answers "how many rows point at this status?" — the full scan that every status removal must do
 * before it decides anything (ADR 0020 §10).
 *
 * <p><b>All four levels are counted by {@code statusId} now.</b> Increment 4 of 0000186F completed
 * the switchover: entities carry no status code any more, so a removal policy that still matched on
 * the legacy varchar would see zero references for every row created since — silent data loss under
 * {@code remove}, a silent no-op under {@code prevent}.
 *
 * <p><b>A row with a null {@code statusId} references no status.</b> It is "not migrated yet", and
 * it is deliberately invisible to every policy here: reassigning it would invent a status nobody
 * chose, and deleting it because some <em>other</em> status was removed would be vandalism. Such
 * rows are found and fixed by {@code StatusMigrationService}, not by this class.
 *
 * <p>This is the one place that knows how entities reference a status, so a future change of the
 * reference (an FK, a join table) touches one class instead of every removal path.
 */
@ApplicationScoped
public class StatusReferenceScanner {

    /** How many rows of this level currently carry this status. */
    public long count(StatusLevel level, StatusBase status) {
        if (status.id == null) {
            return 0L;
        }
        return switch (level) {
            case PROJECT -> Project.count("statusId", status.id);
            case ISSUE -> Issue.count("statusId", status.id);
            case TASKLIST -> pl.perigee.gauge.domain.Tasklist.count("statusId", status.id);
            case TASK -> pl.perigee.gauge.domain.Task.count("statusId", status.id);
        };
    }

    /**
     * Cascade-deletes every row carrying this status. Used by the {@code remove} orphan policy.
     *
     * <p>Deliberately blunt: the caller has already said it wants the rows gone. Anything that must
     * not be silently destroyed belongs behind {@code prevent}, which is the safer policy and the
     * one a caller gets when they express no preference.
     *
     * <p><b>Projects are refused, deliberately.</b> Deleting a project row is not a flat statement:
     * it orphans issues, tasklists, tasks and subprojects, and must respect {@code removalLock}
     * (ADR 0008) and the cascade rules in {@code ProjectService}. A status deletion cannot honour
     * any of that, so it must not pretend to. The caller is told to move the projects first or
     * delete them explicitly.
     */
    @Transactional
    public long removeAll(StatusLevel level, StatusBase status) {
        if (level == StatusLevel.PROJECT) {
            throw new ValidationException(
                "PROJECT_REMOVAL_REQUIRES_MIGRATE",
                "A project status cannot be removed with policy=remove: deleting project rows here "
                    + "would bypass removalLock and orphan their issues, tasklists, tasks and "
                    + "subprojects. Use policy=migrate&migrateTo=<code> to move the projects to "
                    + "another status, or delete the projects explicitly first."
            );
        }
        if (status.id == null) {
            return 0L;
        }
        long affected = switch (level) {
            case PROJECT -> 0L;
            case ISSUE -> Issue.delete("statusId", status.id);
            case TASKLIST -> pl.perigee.gauge.domain.Tasklist.delete("statusId", status.id);
            case TASK -> pl.perigee.gauge.domain.Task.delete("statusId", status.id);
        };
        detachStale(level);
        return affected;
    }

    /**
     * Rewrites every row carrying {@code from} to {@code to}. Used by the {@code migrate} policy.
     *
     * <p>Targets are named by status object, never by id at the API boundary — ids are internal
     * (ADR 0020 §2).
     */
    @Transactional
    public long reassign(StatusLevel level, StatusBase from, StatusBase to) {
        if (to.id == null) {
            throw new ValidationException(
                "MIGRATE_TARGET_REQUIRED",
                "The migration target has no status id; it cannot be stored on a row."
            );
        }
        long affected = switch (level) {
            case PROJECT -> Project.update("statusId = ?1 WHERE statusId = ?2", to.id, from.id);
            case ISSUE -> Issue.update("statusId = ?1 WHERE statusId = ?2", to.id, from.id);
            case TASKLIST -> pl.perigee.gauge.domain.Tasklist.update(
                "statusId = ?1 WHERE statusId = ?2", to.id, from.id);
            case TASK -> pl.perigee.gauge.domain.Task.update(
                "statusId = ?1 WHERE statusId = ?2", to.id, from.id);
        };
        detachStale(level);
        return affected;
    }

    /**
     * Bulk JPQL writes go straight to the database and bypass the persistence context, so entities
     * already loaded in this transaction keep their old status and a later {@code findById} returns
     * stale data. Flushing and clearing afterwards makes the in-memory view agree with the rows.
     */
    private static void detachStale(StatusLevel level) {
        switch (level) {
            case ISSUE -> {
                Issue.getEntityManager().flush();
                Issue.getEntityManager().clear();
            }
            case TASKLIST -> {
                pl.perigee.gauge.domain.Tasklist.getEntityManager().flush();
                pl.perigee.gauge.domain.Tasklist.getEntityManager().clear();
            }
            case TASK -> {
                pl.perigee.gauge.domain.Task.getEntityManager().flush();
                pl.perigee.gauge.domain.Task.getEntityManager().clear();
            }
            case PROJECT -> {
                Project.getEntityManager().flush();
                Project.getEntityManager().clear();
            }
        }
    }
}
