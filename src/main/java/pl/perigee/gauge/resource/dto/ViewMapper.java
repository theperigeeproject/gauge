package pl.perigee.gauge.resource.dto;

import pl.perigee.gauge.domain.Issue;
import pl.perigee.gauge.domain.Project;
import pl.perigee.gauge.domain.StatusBase;
import pl.perigee.gauge.domain.StatusLevel;
import pl.perigee.gauge.domain.Task;
import pl.perigee.gauge.domain.Tasklist;
import pl.perigee.gauge.service.StatusService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Comparator;
import java.util.List;

/**
 * The single place where a stored status <b>id</b> becomes the wire <b>code</b> (ADR 0020 §2/§4).
 *
 * <p>Every mapping goes through {@link StatusService#codeOf}, never a status repository: a null
 * status id is legal and must resolve to {@code NEEDS_MIGRATION}, and a mapper that reached for a
 * repository would turn a perfectly legal unmigrated row into a blank or an exception.
 *
 * <p>Nothing else may serialize an entity. The entities carry `statusId` and the legacy
 * `status` column, and neither belongs on the wire.
 */
@ApplicationScoped
public class ViewMapper {

    @Inject
    StatusService statusService;

    public IssueView issue(Issue issue) {
        return new IssueView(
            issue.id,
            issue.projectId,
            issue.title,
            issue.description,
            statusService.codeOf(StatusLevel.ISSUE, issue),
            issue.priority
        );
    }

    public List<IssueView> issues(List<Issue> issues) {
        return issues.stream().map(this::issue).toList();
    }

    public TasklistView tasklist(Tasklist tasklist) {
        return new TasklistView(
            tasklist.id,
            tasklist.issueId,
            tasklist.title,
            statusService.codeOf(StatusLevel.TASKLIST, tasklist),
            tasklist.decomposesTaskId
        );
    }

    public List<TasklistView> tasklists(List<Tasklist> tasklists) {
        return tasklists.stream().map(this::tasklist).toList();
    }

    public TaskView task(Task task) {
        return new TaskView(
            task.id,
            task.tasklistId,
            task.ordinal,
            task.title,
            task.description,
            statusService.codeOf(StatusLevel.TASK, task)
        );
    }

    public List<TaskView> tasks(List<Task> tasks) {
        return tasks.stream().map(this::task).toList();
    }

    public ProjectView project(Project project) {
        return new ProjectView(
            project.id,
            project.name,
            project.description,
            project.parentId,
            project.removalLock(),
            statusService.codeOf(StatusLevel.PROJECT, project)
        );
    }

    public List<ProjectView> projects(List<Project> projects) {
        return projects.stream().map(this::project).toList();
    }

    // ── Status definitions (discovery, not authoring) ───────────────

    /**
     * The statuses a renderer may build controls and counters from: only the readable ones, in
     * {@code precedenceValue} order.
     *
     * <p>ARCHIVE and TRASH are filtered out because their entity rows never surface (§8) — a column
     * or filter option for one could only ever hold zero, i.e. a permanently dead control. The
     * predicates are resolved here rather than in the template, so a browser script never learns
     * what a progress level means.
     */
    public List<StatusView> statuses(StatusLevel level) {
        Integer defaultId = statusService.defaultStatusId(level);
        return statusService.available(level).stream()
            .filter(statusService::isReadVisible)
            .sorted(Comparator.comparingInt(s -> s.precedenceValue))
            .map(status -> status(status, defaultId))
            .toList();
    }

    private StatusView status(StatusBase status, Integer defaultStatusId) {
        return new StatusView(
            status.code,
            status.color,
            status.precedenceValue,
            status.progressLevel,
            status.attitudeType,
            statusService.isTerminal(status),
            statusService.isParked(status),
            statusService.isActive(status),
            statusService.isReadVisible(status),
            defaultStatusId != null && defaultStatusId.equals(status.id)
        );
    }
}
