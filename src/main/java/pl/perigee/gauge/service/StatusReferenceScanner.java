package pl.perigee.gauge.service;

import pl.perigee.gauge.domain.Issue;
import pl.perigee.gauge.domain.Project;
import pl.perigee.gauge.domain.StatusLevel;
import pl.perigee.gauge.domain.StatusBase;
import pl.perigee.gauge.domain.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Answers "how many rows point at this status?" — the full scan that every status removal must do
 * before it decides anything (ADR 0020 §10).
 *
 * <p><b>This is the one place that knows how entities reference a status, and it is temporary.</b>
 * Issues, tasklists and tasks still carry the legacy status <em>code</em> in a varchar, so counting
 * and rewriting for those three go through the code. Projects are the exception and always were:
 * they have <b>no</b> legacy column at all, only {@code statusId}, so they are counted by id.
 *
 * <p>Once the wire switchover lands the other three flip to ids as well and this class's signature
 * does not change. Keeping the seam here means that flip touches one class instead of every removal
 * path.
 *
 * <p><b>Projects must be counted by id or deletion corrupts them.</b> Returning zero for
 * {@link StatusLevel#PROJECT} was correct when projects had no status column; it stopped being
 * correct the moment they gained one, because a project status would then always look unused,
 * deletion would proceed, and every project pointing at it would resolve to a dangling id — which
 * {@code StatusService.byId} correctly reports as corruption, far from the edit that caused it.
 */
@ApplicationScoped
public class StatusReferenceScanner {

    /** How many rows of this level currently carry this status. */
    public long count(StatusLevel level, StatusBase status) {
        return switch (level) {
            case PROJECT -> status.id == null ? 0L : Project.count("statusId", status.id);
            case ISSUE -> Issue.find("status", status.code).count();
            case TASKLIST -> pl.perigee.gauge.domain.Tasklist.find("status", status.code).count();
            case TASK -> pl.perigee.gauge.domain.Task.find("status", status.code).count();
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
        long affected = switch (level) {
            case PROJECT -> 0L;
            case ISSUE -> Issue.delete("status", status.code);
            case TASKLIST -> pl.perigee.gauge.domain.Tasklist.delete("status", status.code);
            case TASK -> pl.perigee.gauge.domain.Task.delete("status", status.code);
        };
        detachStale(level);
        return affected;
    }

    /**
     * Rewrites every row carrying {@code from} to {@code to}. Used by the {@code migrate} policy.
     *
     * <p>Targets are named by status object, never by id at the API boundary — ids are internal
     * (ADR 0020 §2). Projects are rewritten by id, because that is the only thing they carry.
     */
    @Transactional
    public long reassign(StatusLevel level, StatusBase from, StatusBase to) {
        long affected = switch (level) {
            case PROJECT -> Project.update("statusId = ?1 WHERE statusId = ?2", to.id, from.id);
            case ISSUE -> Issue.update("status = ?1 WHERE status = ?2", to.code, from.code);
            case TASKLIST -> pl.perigee.gauge.domain.Tasklist.update(
                "status = ?1 WHERE status = ?2", to.code, from.code);
            case TASK -> pl.perigee.gauge.domain.Task.update(
                "status = ?1 WHERE status = ?2", to.code, from.code);
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
