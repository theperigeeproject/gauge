package pl.perigee.gauge.security;

import pl.perigee.gauge.domain.Issue;
import pl.perigee.gauge.domain.Project;
import pl.perigee.gauge.domain.Task;
import pl.perigee.gauge.domain.Tasklist;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Checks whether the current API key is allowed to access a given project
 * (or entity that belongs to a project tree).
 */
@ApplicationScoped
public class ProjectAccessGuard {

    @Inject
    CurrentApiKey currentApiKey;

    // ── Project ──────────────────────────────────────────────────

    /**
     * Returns true if the current API key is allowed to create a project
     * with the given parent. Restricted keys must create under a parent
     * in their scope (null parent = root project = denied).
     */
    public boolean canCreateProject(String parentId) {
        ApiKey key = currentApiKey.get();
        if (key == null || !key.isRestricted()) {
            return true;
        }
        if (parentId == null) {
            return false;
        }
        return canWriteProject(parentId);
    }

    /**
     * Returns true if the current API key is allowed to read the given project.
     * Unrestricted keys (including master) can access everything.
     * Restricted keys can only access the restricted project and its descendants.
     */
    public boolean canAccessProject(String projectId) {
        ApiKey key = currentApiKey.get();
        if (key == null || !key.isRestricted()) {
            return true;
        }
        return isProjectUnder(projectId, key.restrictedProjectId);
    }

    /**
     * Returns true if the current API key is allowed to write the given project.
     */
    public boolean canWriteProject(String projectId) {
        ApiKey key = currentApiKey.get();
        if (key == null || !key.isRestricted()) {
            return true;
        }
        return key.canWrite() && isProjectUnder(projectId, key.restrictedProjectId);
    }

    // ── Issue ────────────────────────────────────────────────────

    public boolean canAccessIssue(String issueId) {
        ApiKey key = currentApiKey.get();
        if (key == null || !key.isRestricted()) {
            return true;
        }
        Issue issue = Issue.findById(issueId);
        if (issue == null) {
            return false;
        }
        return isProjectUnder(issue.projectId, key.restrictedProjectId);
    }

    public boolean canWriteIssue(String issueId) {
        ApiKey key = currentApiKey.get();
        if (key == null || !key.isRestricted()) {
            return true;
        }
        if (!key.canWrite()) {
            return false;
        }
        Issue issue = Issue.findById(issueId);
        if (issue == null) {
            return false;
        }
        return isProjectUnder(issue.projectId, key.restrictedProjectId);
    }

    // ── Tasklist ─────────────────────────────────────────────────

    public boolean canAccessTasklist(String tasklistId) {
        ApiKey key = currentApiKey.get();
        if (key == null || !key.isRestricted()) {
            return true;
        }
        Tasklist tasklist = Tasklist.findById(tasklistId);
        if (tasklist == null) {
            return false;
        }
        return canAccessIssue(tasklist.issueId);
    }

    public boolean canWriteTasklist(String tasklistId) {
        ApiKey key = currentApiKey.get();
        if (key == null || !key.isRestricted()) {
            return true;
        }
        if (!key.canWrite()) {
            return false;
        }
        Tasklist tasklist = Tasklist.findById(tasklistId);
        if (tasklist == null) {
            return false;
        }
        return canWriteIssue(tasklist.issueId);
    }

    // ── Task ─────────────────────────────────────────────────────

    public boolean canAccessTask(String taskId) {
        ApiKey key = currentApiKey.get();
        if (key == null || !key.isRestricted()) {
            return true;
        }
        Task task = Task.findById(taskId);
        if (task == null) {
            return false;
        }
        return canAccessTasklist(task.tasklistId);
    }

    public boolean canWriteTask(String taskId) {
        ApiKey key = currentApiKey.get();
        if (key == null || !key.isRestricted()) {
            return true;
        }
        if (!key.canWrite()) {
            return false;
        }
        Task task = Task.findById(taskId);
        if (task == null) {
            return false;
        }
        return canWriteTasklist(task.tasklistId);
    }

    // ── helpers ──────────────────────────────────────────────────

    /**
     * Returns true if {@code projectId} is the restricted project or a descendant of it.
     * Walks up the parent chain from projectId.
     */
    private boolean isProjectUnder(String projectId, String restrictedProjectId) {
        if (projectId.equals(restrictedProjectId)) {
            return true;
        }
        String current = projectId;
        while (current != null) {
            Project p = Project.findById(current);
            if (p == null) {
                return false;
            }
            if (p.parentId == null) {
                return false;
            }
            if (p.parentId.equals(restrictedProjectId)) {
                return true;
            }
            current = p.parentId;
        }
        return false;
    }
}
