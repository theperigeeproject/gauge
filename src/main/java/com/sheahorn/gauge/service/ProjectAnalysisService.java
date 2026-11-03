package com.sheahorn.gauge.service;

import com.sheahorn.gauge.domain.*;
import com.sheahorn.gauge.repository.IssueRepository;
import com.sheahorn.gauge.repository.ProjectRepository;
import com.sheahorn.gauge.repository.TaskRepository;
import com.sheahorn.gauge.repository.TasklistRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.*;

/**
 * Computes project analysis stats (status counts, subproject counts, bubbled priority)
 * for all projects, aggregating recursively across subproject trees.
 *
 * <p>Counts are keyed by status code, so new statuses show up as new columns without code
 * changes. Behaviour (what bubbles, what counts as finished, what is ignored) is decided by
 * the status's {@link StatusCategory}, never by its code.
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
    StatusRegistry statusRegistry;

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

        Map<String, Integer> issueCounts = new LinkedHashMap<>();
        Map<String, Integer> taskCounts = new LinkedHashMap<>();
        int subprojectCount;
        Priority highestPriority = null;

        // Aggregate this project's direct issues and their tasks
        List<Issue> directIssues = issuesByProject.getOrDefault(projectId, List.of());
        for (Issue issue : directIssues) {
            issueCounts.merge(issue.status(), 1, Integer::sum);

            // Only ACTIVE statuses bubble a priority. PARKED (e.g. INACTIVE) demands no
            // attention; FINISHED is done and bubbles nothing.
            StatusCategory category = statusRegistry.category(StatusLevel.ISSUE, issue.status());
            if (category == StatusCategory.ACTIVE) {
                Priority p = issue.priority();
                if (highestPriority == null || p.severity > highestPriority.severity) {
                    highestPriority = p;
                }
            }

            List<Tasklist> tasklists = tasklistsByIssue.getOrDefault(issue.id(), List.of());
            for (Tasklist tl : tasklists) {
                List<Task> tasks = tasksByTasklist.getOrDefault(tl.id(), List.of());
                for (Task t : tasks) {
                    taskCounts.merge(t.status(), 1, Integer::sum);
                }
            }
        }

        // Recurse into subprojects
        List<Project> children = childrenByParent.getOrDefault(projectId, List.of());
        subprojectCount = children.size();
        for (Project child : children) {
            ProjectAnalysisResult childResult = computeRecursive(child.id(),
                childrenByParent, issuesByProject, tasklistsByIssue, tasksByTasklist);
            childResult.issueCounts.forEach((code, count) -> issueCounts.merge(code, count, Integer::sum));
            childResult.taskCounts.forEach((code, count) -> taskCounts.merge(code, count, Integer::sum));
            subprojectCount += childResult.subprojectCount;
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

        BubbledPriority bp = computeBubbledPriority(issueCounts, highestPriority);
        return new ProjectAnalysisResult(issueCounts, taskCounts, subprojectCount, bp);
    }

    /**
     * Bubbled priority resolution, driven by status categories:
     * 1. no issues at all            → NONE
     * 2. any ACTIVE issue            → highest active priority (LOW/NORMAL/HIGH)
     * 3. no active, some FINISHED    → DONE
     * 4. no active, only PARKED      → INACTIVE
     */
    private BubbledPriority computeBubbledPriority(Map<String, Integer> issueCounts,
                                                   Priority highestPriority) {
        int active = 0, finished = 0, parked = 0;
        for (Map.Entry<String, Integer> entry : issueCounts.entrySet()) {
            if (entry.getValue() == null || entry.getValue() == 0) continue;
            switch (statusRegistry.category(StatusLevel.ISSUE, entry.getKey())) {
                case ACTIVE -> active += entry.getValue();
                case FINISHED -> finished += entry.getValue();
                case PARKED -> parked += entry.getValue();
            }
        }

        if (active == 0 && finished == 0 && parked == 0) return BubbledPriority.NONE;
        if (active > 0) {
            if (highestPriority == null) return BubbledPriority.NONE;
            return switch (highestPriority) {
                case HIGH -> BubbledPriority.HIGH;
                case NORMAL -> BubbledPriority.NORMAL;
                case LOW -> BubbledPriority.LOW;
            };
        }
        if (finished > 0) return BubbledPriority.DONE;
        return BubbledPriority.INACTIVE;
    }
}
