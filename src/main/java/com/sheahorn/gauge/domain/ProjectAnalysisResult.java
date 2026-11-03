package com.sheahorn.gauge.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Result of project analysis — computed stats for a single project
 * including all descendant subprojects (recursive aggregation).
 *
 * <p>Counts are keyed by status code, because statuses are configurable
 * ({@code status_definitions}). The {@code issueTodo/issueDoing/...} fields are a
 * compatibility shim for the built-in codes; the maps are authoritative.
 */
public class ProjectAnalysisResult {

    public Map<String, Integer> issueCounts = new LinkedHashMap<>();
    public Map<String, Integer> taskCounts = new LinkedHashMap<>();
    public int subprojectCount;
    public BubbledPriority bubbledPriority;

    // ---- legacy flat fields (built-in codes only) ----
    public int issueTodo;
    public int issueDoing;
    public int issueInactive;
    public int issueDone;
    public int taskTodo;
    public int taskDoing;

    public ProjectAnalysisResult() {}

    public ProjectAnalysisResult(Map<String, Integer> issueCounts,
                                 Map<String, Integer> taskCounts,
                                 int subprojectCount,
                                 BubbledPriority bubbledPriority) {
        this.issueCounts = issueCounts != null ? issueCounts : new LinkedHashMap<>();
        this.taskCounts = taskCounts != null ? taskCounts : new LinkedHashMap<>();
        this.subprojectCount = subprojectCount;
        this.bubbledPriority = bubbledPriority;
        deriveLegacyCounts();
    }

    /** Legacy constructor — kept so existing callers and tests keep working. */
    public ProjectAnalysisResult(int issueTodo, int issueDoing, int issueInactive, int issueDone,
                                 int subprojectCount, int taskTodo, int taskDoing,
                                 BubbledPriority bubbledPriority) {
        this.issueTodo = issueTodo;
        this.issueDoing = issueDoing;
        this.issueInactive = issueInactive;
        this.issueDone = issueDone;
        this.subprojectCount = subprojectCount;
        this.taskTodo = taskTodo;
        this.taskDoing = taskDoing;
        this.bubbledPriority = bubbledPriority;
        if (issueTodo != 0) this.issueCounts.put("TODO", issueTodo);
        if (issueDoing != 0) this.issueCounts.put("DOING", issueDoing);
        if (issueDone != 0) this.issueCounts.put("DONE", issueDone);
        if (issueInactive != 0) this.issueCounts.put("INACTIVE", issueInactive);
        if (taskTodo != 0) this.taskCounts.put("TODO", taskTodo);
        if (taskDoing != 0) this.taskCounts.put("DOING", taskDoing);
    }

    private void deriveLegacyCounts() {
        issueTodo = issueCounts.getOrDefault("TODO", 0);
        issueDoing = issueCounts.getOrDefault("DOING", 0);
        issueDone = issueCounts.getOrDefault("DONE", 0);
        issueInactive = issueCounts.getOrDefault("INACTIVE", 0);
        taskTodo = taskCounts.getOrDefault("TODO", 0);
        taskDoing = taskCounts.getOrDefault("DOING", 0);
    }

    public Map<String, Integer> issueCounts() { return issueCounts; }
    public Map<String, Integer> taskCounts() { return taskCounts; }
    public int subprojectCount() { return subprojectCount; }
    public BubbledPriority bubbledPriority() { return bubbledPriority; }
}
