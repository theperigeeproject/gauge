package pl.perigee.gauge.service;

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
            switch (issue.status()) {
                case TODO -> issueTodo++;
                case DOING -> issueDoing++;
                case INACTIVE -> issueInactive++;
                case DONE -> issueDone++;
            }
            // Track highest priority among active (TODO/DOING) issues only.
            // INACTIVE issues are parked — they demand no attention, so they
            // neither bubble a priority nor count as finished work.
            if (issue.status() == IssueStatus.TODO || issue.status() == IssueStatus.DOING) {
                if (highestPriority == null || issue.priority().severity > highestPriority.severity) {
                    highestPriority = issue.priority();
                }
            }
            // Aggregate tasks for this issue
            List<Tasklist> tasklists = tasklistsByIssue.getOrDefault(issue.id(), List.of());
            for (Tasklist tl : tasklists) {
                List<Task> tasks = tasksByTasklist.getOrDefault(tl.id(), List.of());
                for (Task t : tasks) {
                    switch (t.status()) {
                        case TODO -> taskTodo++;
                        case DOING -> taskDoing++;
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
