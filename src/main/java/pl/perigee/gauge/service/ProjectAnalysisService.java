package pl.perigee.gauge.service;

import pl.perigee.gauge.config.StatusSeedStartup;
import pl.perigee.gauge.domain.*;
import pl.perigee.gauge.repository.IssueRepository;
import pl.perigee.gauge.repository.ProjectRepository;
import pl.perigee.gauge.repository.TaskRepository;
import pl.perigee.gauge.repository.TasklistRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.*;

/**
 * Computes project analysis stats (issue counts, subproject counts,
 * task counts, bubbled priority) for all projects, aggregating
 * recursively across subproject trees.
 *
 * <p><b>No status is named here.</b> Since increment 4 of 0000186F statuses are data, so the four
 * legacy buckets are derived from the two axes plus the progress level (ADR 0020 §6):
 *
 * <ul>
 *   <li>{@code issueDone} — terminal, i.e. FINISHED/FINALIZED. Hides by default, still counted.</li>
 *   <li>{@code issueInactive} — parked, i.e. EXCLUDED. Never bubbles a priority, ranks last.</li>
 *   <li>{@code issueTodo} — active and CREATED.</li>
 *   <li>{@code issueDoing} — active and anything else.</li>
 * </ul>
 *
 * <p>The last rule is deliberately a catch-all rather than {@code progressLevel == ONGOING}: a row
 * whose status was never migrated reads as {@code NEEDS_MIGRATION} (HALTED/MIGRATION), and a custom
 * status an admin invents tomorrow would too. Bucketing them with "active work" keeps them counted
 * and bubbling instead of making them invisible in the tree — which is the one thing an analysis
 * must never do. Increment 5 (task 00001A5F) replaces these four fixed fields with a per-code map,
 * at which point the catch-all disappears by construction.
 */
@ApplicationScoped
public class ProjectAnalysisService {

    @Inject
    ProjectRepository projectRepository;

    @Inject
    IssueRepository issueRepository;

    @Inject
    TasklistRepository tasklistRepository;

    @Inject
    TaskRepository taskRepository;

    @Inject
    StatusService statusService;

    /**
     * Returns a mutable map of projectId → ProjectAnalysisResult
     * for all projects, with recursive aggregation.
     */
    public Map<String, ProjectAnalysisResult> analyzeAll() {
        List<Project> allProjects = projectRepository.findAll();
        List<Issue> allIssues = issueRepository.findAll();
        List<Tasklist> allTasklists = tasklistRepository.findAll();
        List<Task> allTasks = taskRepository.findAll();

        // Index children by parentId
        Map<String, List<Project>> childrenByParent = new HashMap<>();
        for (Project p : allProjects) {
            String pid = p.parentId() != null ? p.parentId() : "__root__";
            childrenByParent.computeIfAbsent(pid, k -> new ArrayList<>()).add(p);
        }

        // Index issues by projectId
        Map<String, List<Issue>> issuesByProject = new HashMap<>();
        for (Issue i : allIssues) {
            issuesByProject.computeIfAbsent(i.projectId(), k -> new ArrayList<>()).add(i);
        }

        // Index tasklists by issueId
        Map<String, List<Tasklist>> tasklistsByIssue = new HashMap<>();
        for (Tasklist tl : allTasklists) {
            tasklistsByIssue.computeIfAbsent(tl.issueId(), k -> new ArrayList<>()).add(tl);
        }

        // Index tasks by tasklistId
        Map<String, List<Task>> tasksByTasklist = new HashMap<>();
        for (Task t : allTasks) {
            tasksByTasklist.computeIfAbsent(t.tasklistId(), k -> new ArrayList<>()).add(t);
        }

        Map<String, ProjectAnalysisResult> results = new HashMap<>();
        for (Project p : allProjects) {
            results.put(p.id(), computeRecursive(p.id(), childrenByParent,
                issuesByProject, tasklistsByIssue, tasksByTasklist));
        }
        return results;
    }

    private ProjectAnalysisResult computeRecursive(
            String projectId,
            Map<String, List<Project>> childrenByParent,
            Map<String, List<Issue>> issuesByProject,
            Map<String, List<Tasklist>> tasklistsByIssue,
            Map<String, List<Task>> tasksByTasklist) {

        int issueTodo = 0, issueDoing = 0, issueInactive = 0, issueDone = 0;
        int subprojectCount = 0;
        int taskTodo = 0, taskDoing = 0;
        Priority highestPriority = null;

        // Aggregate this project's direct issues
        List<Issue> directIssues = issuesByProject.getOrDefault(projectId, List.of());
        for (Issue issue : directIssues) {
            StatusBase status = statusService.of(StatusLevel.ISSUE, issue);
            if (statusService.isTerminal(status)) {
                issueDone++;
            } else if (statusService.isParked(status)) {
                issueInactive++;
            } else if (StatusSeedStartup.CREATED.equals(status.progressLevel)) {
                issueTodo++;
            } else {
                issueDoing++;
            }

            // Track highest priority among ACTIVE issues only. A parked issue is quiet — it
            // demands no attention, so it neither bubbles a priority nor counts as finished work.
            if (statusService.isActive(status)) {
                if (highestPriority == null || issue.priority().severity > highestPriority.severity) {
                    highestPriority = issue.priority();
                }
            }

            // Aggregate tasks for this issue
            List<Tasklist> tasklists = tasklistsByIssue.getOrDefault(issue.id(), List.of());
            for (Tasklist tl : tasklists) {
                List<Task> tasks = tasksByTasklist.getOrDefault(tl.id(), List.of());
                for (Task t : tasks) {
                    StatusBase taskStatus = statusService.of(StatusLevel.TASK, t);
                    if (statusService.isTerminal(taskStatus) || statusService.isParked(taskStatus)) {
                        continue; // done/failed-out work is not "todo" or "doing"
                    }
                    if (StatusSeedStartup.CREATED.equals(taskStatus.progressLevel)) {
                        taskTodo++;
                    } else {
                        taskDoing++;
                    }
                }
            }
        }

        // Recurse into subprojects
        List<Project> children = childrenByParent.getOrDefault(projectId, List.of());
        subprojectCount = children.size();
        for (Project child : children) {
            ProjectAnalysisResult childResult = computeRecursive(child.id(),
                childrenByParent, issuesByProject, tasklistsByIssue, tasksByTasklist);
            issueTodo += childResult.issueTodo;
            issueDoing += childResult.issueDoing;
            issueInactive += childResult.issueInactive;
            issueDone += childResult.issueDone;
            subprojectCount += childResult.subprojectCount;
            taskTodo += childResult.taskTodo;
            taskDoing += childResult.taskDoing;
            // Merge highest priority from child (only real priorities bubble)
            if (childResult.bubbledPriority != BubbledPriority.NONE
                && childResult.bubbledPriority != BubbledPriority.DONE
                && childResult.bubbledPriority != BubbledPriority.INACTIVE) {
                Priority childPrio = switch (childResult.bubbledPriority) {
                    case HIGH -> Priority.HIGH;
                    case NORMAL -> Priority.NORMAL;
                    case LOW -> Priority.LOW;
                    default -> null;
                };
                if (childPrio != null && (highestPriority == null || childPrio.severity > highestPriority.severity)) {
                    highestPriority = childPrio;
                }
            }
        }

        BubbledPriority bp = computeBubbledPriority(
            issueTodo + issueDoing, issueInactive, issueDone, highestPriority);
        return new ProjectAnalysisResult(issueTodo, issueDoing, issueInactive, issueDone,
            subprojectCount, taskTodo, taskDoing, bp);
    }

    /**
     * Bubbled priority resolution:
     * 1. nothing at all                   → NONE
     * 2. any active issue                 → highest active priority (LOW/NORMAL/HIGH)
     * 3. no active, some DONE             → DONE
     * 4. no active, only parked INACTIVE  → INACTIVE
     *
     * <p>"Active" is {@code isActive} on the status row, not a named status: an unmigrated row is
     * active, so it bubbles - the honest default for a row nobody has classified yet.
     */
    private BubbledPriority computeBubbledPriority(int activeCount, int inactiveCount,
                                                   int doneCount, Priority highestPriority) {
        if (activeCount == 0 && inactiveCount == 0 && doneCount == 0) return BubbledPriority.NONE;
        if (activeCount > 0) {
            if (highestPriority == null) return BubbledPriority.NONE;
            return switch (highestPriority) {
                case HIGH -> BubbledPriority.HIGH;
                case NORMAL -> BubbledPriority.NORMAL;
                case LOW -> BubbledPriority.LOW;
            };
        }
        if (doneCount > 0) return BubbledPriority.DONE;
        return BubbledPriority.INACTIVE;
    }
}
