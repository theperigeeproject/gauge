package pl.perigee.gauge.domain;

/**
 * Result of project analysis — computed stats for a single project
 * including all descendant subprojects (recursive aggregation).
 */
public class ProjectAnalysisResult {
    public int issueTodo;
    public int issueDoing;
    public int issueInactive;
    public int issueDone;
    public int subprojectCount;
    public int taskTodo;
    public int taskDoing;
    public BubbledPriority bubbledPriority;

    public ProjectAnalysisResult() {}

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
    }
}
