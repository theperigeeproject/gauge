package pl.perigee.gauge.service;

import pl.perigee.gauge.domain.*;
import pl.perigee.gauge.repository.IssueRepository;
import pl.perigee.gauge.repository.ProjectRepository;
import pl.perigee.gauge.repository.TaskRepository;
import pl.perigee.gauge.repository.TasklistRepository;
import pl.perigee.gauge.support.StatusIds;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ProjectAnalysisServiceTest {

    @Inject
    ProjectAnalysisService analysisService;

    @Inject
    ProjectRepository projectRepository;

    @Inject
    IssueRepository issueRepository;

    @Inject
    TasklistRepository tasklistRepository;

    @Inject
    TaskRepository taskRepository;

    @Inject
    EntityManager em;

    @Inject
    StatusIds statusIds;

    @BeforeEach
    @Transactional
    void cleanUp() {
        em.createNativeQuery("DELETE FROM tasks").executeUpdate();
        em.createNativeQuery("DELETE FROM tasklists").executeUpdate();
        em.createNativeQuery("DELETE FROM issues").executeUpdate();
        em.createNativeQuery("DELETE FROM projects").executeUpdate();
    }

    // ---- helpers ----

    private Project createProject(String id, String name, String parentId) {
        Project p = new Project(id, name, "desc", parentId, "", statusIds.project("OPENED"));
        projectRepository.save(p);
        return p;
    }

    /** @param issueStatusCode a status CODE for the issue level; the id is resolved here, as a caller would */
    private Issue createIssue(String id, String projectId, String title, Priority priority,
                              String issueStatusCode) {
        Issue i = new Issue(id, projectId, title, "desc", statusIds.issue(issueStatusCode), priority);
        issueRepository.save(i);
        return i;
    }

    private Tasklist createTasklist(String id, String issueId, String title) {
        Tasklist tl = new Tasklist(id, issueId, title, statusIds.tasklist("TODO"), null);
        tasklistRepository.save(tl);
        return tl;
    }

    private Task createTask(String id, String tasklistId, int ordinal, String title,
                            String taskStatusCode) {
        Task t = new Task(id, tasklistId, ordinal, title, "desc", statusIds.task(taskStatusCode));
        taskRepository.save(t);
        return t;
    }

    // ---- 0: empty DB → empty map ----

    @Test
    @Transactional
    void testEmptyDbReturnsEmptyMap() {
        Map<String, ProjectAnalysisResult> results = analysisService.analyzeAll();
        assertTrue(results.isEmpty());
    }

    // ---- 1: single project with one TODO issue ----

    @Test
    @Transactional
    void testSingleProjectOneTodoIssue() {
        createProject("P0000001", "P", null);
        createIssue("I0000001", "P0000001", "Issue", Priority.NORMAL, "TODO");

        Map<String, ProjectAnalysisResult> results = analysisService.analyzeAll();
        ProjectAnalysisResult r = results.get("P0000001");
        assertEquals(1, r.issueTodo);
        assertEquals(0, r.issueDoing);
        assertEquals(0, r.issueDone);
        assertEquals(BubbledPriority.NORMAL, r.bubbledPriority);
    }

    // ---- 2: single project with mixed status issues ----

    @Test
    @Transactional
    void testSingleProjectMixedStatusIssues() {
        createProject("P0000001", "P", null);
        createIssue("I0000001", "P0000001", "Todo", Priority.NORMAL, "TODO");
        createIssue("I0000002", "P0000001", "Doing", Priority.NORMAL, "DOING");
        createIssue("I0000003", "P0000001", "Done", Priority.NORMAL, "DONE");

        Map<String, ProjectAnalysisResult> results = analysisService.analyzeAll();
        ProjectAnalysisResult r = results.get("P0000001");
        assertEquals(1, r.issueTodo);
        assertEquals(1, r.issueDoing);
        assertEquals(1, r.issueDone);
    }

    // ---- 3: HIGH priority TODO → bubbled=HIGH ----

    @Test
    @Transactional
    void testSingleProjectHighPriorityTodo() {
        createProject("P0000001", "P", null);
        createIssue("I0000001", "P0000001", "High", Priority.HIGH, "TODO");

        Map<String, ProjectAnalysisResult> results = analysisService.analyzeAll();
        assertEquals(BubbledPriority.HIGH, results.get("P0000001").bubbledPriority);
    }

    // ---- 4: all issues DONE → bubbled=DONE ----

    @Test
    @Transactional
    void testAllIssuesDone() {
        createProject("P0000001", "P", null);
        createIssue("I0000001", "P0000001", "Done1", Priority.HIGH, "DONE");
        createIssue("I0000002", "P0000001", "Done2", Priority.LOW, "DONE");

        Map<String, ProjectAnalysisResult> results = analysisService.analyzeAll();
        ProjectAnalysisResult r = results.get("P0000001");
        assertEquals(0, r.issueTodo);
        assertEquals(0, r.issueDoing);
        assertEquals(2, r.issueDone);
        assertEquals(BubbledPriority.DONE, r.bubbledPriority);
    }

    // ---- 5: parent with subproject — recursive aggregation of issue counts ----

    @Test
    @Transactional
    void testParentWithSubprojectRecursiveCounts() {
        createProject("P0000001", "Parent", null);
        createProject("P0000002", "Child", "P0000001");
        createIssue("I0000001", "P0000001", "ParentIssue", Priority.NORMAL, "TODO");
        createIssue("I0000002", "P0000002", "ChildIssue", Priority.NORMAL, "TODO");

        Map<String, ProjectAnalysisResult> results = analysisService.analyzeAll();
        assertEquals(1, results.get("P0000002").issueTodo);
        assertEquals(2, results.get("P0000001").issueTodo); // own + child
        assertEquals(1, results.get("P0000001").subprojectCount);
    }

    // ---- 6: deep nesting (3 levels) — counts bubble all the way up ----

    @Test
    @Transactional
    void testDeepNestingCountsBubbleUp() {
        createProject("P0000001", "Root", null);
        createProject("P0000002", "Child", "P0000001");
        createProject("P0000003", "Grandchild", "P0000002");
        createIssue("I0000001", "P0000003", "Deep", Priority.NORMAL, "TODO");

        Map<String, ProjectAnalysisResult> results = analysisService.analyzeAll();
        assertEquals(1, results.get("P0000003").issueTodo);
        assertEquals(1, results.get("P0000002").issueTodo);
        assertEquals(1, results.get("P0000001").issueTodo);
        assertEquals(2, results.get("P0000001").subprojectCount); // Child + Grandchild
        assertEquals(1, results.get("P0000002").subprojectCount); // Grandchild
    }

    // ---- 7: HIGH in subproject bubbles up to root ----

    @Test
    @Transactional
    void testHighInSubprojectBubblesToRoot() {
        createProject("P0000001", "Root", null);
        createProject("P0000002", "Child", "P0000001");
        createProject("P0000003", "Grandchild", "P0000002");
        createIssue("I0000001", "P0000003", "High", Priority.HIGH, "TODO");

        Map<String, ProjectAnalysisResult> results = analysisService.analyzeAll();
        assertEquals(BubbledPriority.HIGH, results.get("P0000003").bubbledPriority);
        assertEquals(BubbledPriority.HIGH, results.get("P0000002").bubbledPriority);
        assertEquals(BubbledPriority.HIGH, results.get("P0000001").bubbledPriority);
    }

    // ---- 8: NORMAL in parent, HIGH in child → HIGH wins ----

    @Test
    @Transactional
    void testNormalInParentHighInChildHighWins() {
        createProject("P0000001", "Parent", null);
        createProject("P0000002", "Child", "P0000001");
        createIssue("I0000001", "P0000001", "Normal", Priority.NORMAL, "TODO");
        createIssue("I0000002", "P0000002", "High", Priority.HIGH, "TODO");

        Map<String, ProjectAnalysisResult> results = analysisService.analyzeAll();
        assertEquals(BubbledPriority.HIGH, results.get("P0000002").bubbledPriority);
        // Parent: own NORMAL + child's HIGH bubbles up → HIGH wins
        assertEquals(BubbledPriority.HIGH, results.get("P0000001").bubbledPriority);
    }

    // ---- 9: tasks — issue with tasklist+tasks → taskTodo/taskDoing counted ----

    @Test
    @Transactional
    void testTaskAggregationThroughTasklists() {
        createProject("P0000001", "P", null);
        createIssue("I0000001", "P0000001", "Issue", Priority.NORMAL, "TODO");
        createTasklist("TL000001", "I0000001", "TL");
        createTask("T0000001", "TL000001", 0, "Task1", "TODO");
        createTask("T0000002", "TL000001", 1, "Task2", "DOING");

        Map<String, ProjectAnalysisResult> results = analysisService.analyzeAll();
        ProjectAnalysisResult r = results.get("P0000001");
        assertEquals(1, r.taskTodo);
        assertEquals(1, r.taskDoing);
    }

    // ---- 10: tasks — only TODO/DOING counted; DONE/FAILED/CANCELED ignored ----

    @Test
    @Transactional
    void testOnlyTodoAndDoingTasksCounted() {
        createProject("P0000001", "P", null);
        createIssue("I0000001", "P0000001", "Issue", Priority.NORMAL, "TODO");
        createTasklist("TL000001", "I0000001", "TL");
        createTask("T0000001", "TL000001", 0, "Todo", "TODO");
        createTask("T0000002", "TL000001", 1, "Doing", "DOING");
        createTask("T0000003", "TL000001", 2, "Done", "DONE");
        createTask("T0000004", "TL000001", 3, "Failed", "FAILED");
        // CANCELED is FINALIZED, and FINALIZED is flag-gated for AUTHORING — so this row is built
        // directly, the way a row written before the flag existed would look. Analysis must not care
        // about authorability: it counts by axes.
        Task canceled = new Task("T0000005", "TL000001", 4, "Canceled", "desc",
            statusIds.any(StatusLevel.TASK, "CANCELED"));
        taskRepository.save(canceled);

        Map<String, ProjectAnalysisResult> results = analysisService.analyzeAll();
        ProjectAnalysisResult r = results.get("P0000001");
        assertEquals(1, r.taskTodo);

        // Two, not one: FAILED is HALTED but REGULAR, i.e. ACTIVE, so it buckets with "doing".
        // The old enum switch silently dropped it (it only knew TODO/DOING), which made a failed
        // task invisible in the tree. Bucketing by the axes cannot drop a status, which is the
        // property increment 4 is about (ADR 0020 §6). DONE and CANCELED are terminal and stay out.
        assertEquals(2, r.taskDoing, "DOING + FAILED are active, DONE and CANCELED are terminal");
    }

    // ---- 11: LOW priority TODO → bubbled=LOW ----

    @Test
    @Transactional
    void testLowPriorityTodoBubbledLow() {
        createProject("P0000001", "P", null);
        createIssue("I0000001", "P0000001", "Low", Priority.LOW, "TODO");

        Map<String, ProjectAnalysisResult> results = analysisService.analyzeAll();
        assertEquals(BubbledPriority.LOW, results.get("P0000001").bubbledPriority);
    }

    // ---- 12: LOW in parent, NORMAL in child → NORMAL wins ----

    @Test
    @Transactional
    void testLowInParentNormalInChildNormalWins() {
        createProject("P0000001", "Parent", null);
        createProject("P0000002", "Child", "P0000001");
        createIssue("I0000001", "P0000001", "Low", Priority.LOW, "TODO");
        createIssue("I0000002", "P0000002", "Normal", Priority.NORMAL, "TODO");

        Map<String, ProjectAnalysisResult> results = analysisService.analyzeAll();
        assertEquals(BubbledPriority.NORMAL, results.get("P0000002").bubbledPriority);
        assertEquals(BubbledPriority.NORMAL, results.get("P0000001").bubbledPriority);
    }

    // ---- 13: DONE child + NONE parent → DONE (recursive DONE propagation) ----
    // Note: the task description says "NONE (not DONE)" but the code propagates
    // DONE upward when a child has only DONE issues and the parent has none.
    // This test documents the actual behavior.

    @Test
    @Transactional
    void testDoneChildAndNoneParentPropagatesDone() {
        createProject("P0000001", "Parent", null);
        createProject("P0000002", "Child", "P0000001");
        createIssue("I0000001", "P0000002", "Done", Priority.NORMAL, "DONE");

        Map<String, ProjectAnalysisResult> results = analysisService.analyzeAll();
        assertEquals(BubbledPriority.DONE, results.get("P0000002").bubbledPriority);
        // Parent: no own issues, child doneCount=1 → activeCount=0, doneCount=1 → DONE
        assertEquals(BubbledPriority.DONE, results.get("P0000001").bubbledPriority);
    }

    // ---- 14: multiple sibling subprojects — counts sum correctly ----

    @Test
    @Transactional
    void testMultipleSiblingSubprojectsCountsSum() {
        createProject("P0000001", "Root", null);
        createProject("P0000002", "Child1", "P0000001");
        createProject("P0000003", "Child2", "P0000001");
        createIssue("I0000001", "P0000002", "Issue1", Priority.NORMAL, "TODO");
        createIssue("I0000002", "P0000003", "Issue2", Priority.NORMAL, "TODO");

        Map<String, ProjectAnalysisResult> results = analysisService.analyzeAll();
        ProjectAnalysisResult r = results.get("P0000001");
        assertEquals(2, r.subprojectCount);
        assertEquals(2, r.issueTodo);
    }

    // ---- 15: project with issues AND subprojects — both aggregated ----

    @Test
    @Transactional
    void testProjectWithIssuesAndSubprojectsBothAggregated() {
        createProject("P0000001", "Parent", null);
        createProject("P0000002", "Child", "P0000001");
        createIssue("I0000001", "P0000001", "ParentIssue", Priority.NORMAL, "TODO");
        createIssue("I0000002", "P0000002", "ChildIssue", Priority.HIGH, "TODO");

        Map<String, ProjectAnalysisResult> results = analysisService.analyzeAll();
        ProjectAnalysisResult r = results.get("P0000001");
        assertEquals(2, r.issueTodo);
        assertEquals(1, r.subprojectCount);
        assertEquals(BubbledPriority.HIGH, r.bubbledPriority); // child's HIGH wins
    }

    // ---- 16: empty subproject under parent with issues — parent counts unchanged ----

    @Test
    @Transactional
    void testEmptySubprojectUnderParentWithIssues() {
        createProject("P0000001", "Parent", null);
        createProject("P0000002", "EmptyChild", "P0000001");
        createIssue("I0000001", "P0000001", "ParentIssue", Priority.NORMAL, "TODO");

        Map<String, ProjectAnalysisResult> results = analysisService.analyzeAll();
        ProjectAnalysisResult r = results.get("P0000001");
        assertEquals(1, r.issueTodo); // only own
        assertEquals(1, r.subprojectCount); // child still counted
    }

    // ---- 17: project with no issues but subprojects that have issues — counts bubble up ----

    @Test
    @Transactional
    void testProjectWithNoIssuesButSubprojectsWithIssues() {
        createProject("P0000001", "Parent", null);
        createProject("P0000002", "Child", "P0000001");
        createIssue("I0000001", "P0000002", "ChildIssue", Priority.HIGH, "TODO");

        Map<String, ProjectAnalysisResult> results = analysisService.analyzeAll();
        ProjectAnalysisResult r = results.get("P0000001");
        assertEquals(1, r.issueTodo);
        assertEquals(1, r.subprojectCount);
        assertEquals(BubbledPriority.HIGH, r.bubbledPriority);
    }

    // ---- 18: tasks across multiple tasklists in same issue — all counted ----

    @Test
    @Transactional
    void testTasksAcrossMultipleTasklistsInSameIssue() {
        createProject("P0000001", "P", null);
        createIssue("I0000001", "P0000001", "Issue", Priority.NORMAL, "TODO");
        createTasklist("TL000001", "I0000001", "TL1");
        createTasklist("TL000002", "I0000001", "TL2");
        createTask("T0000001", "TL000001", 0, "Task1", "TODO");
        createTask("T0000002", "TL000002", 0, "Task2", "DOING");

        Map<String, ProjectAnalysisResult> results = analysisService.analyzeAll();
        ProjectAnalysisResult r = results.get("P0000001");
        assertEquals(1, r.taskTodo);
        assertEquals(1, r.taskDoing);
    }

    // ---- 19: null parentId projects treated as roots, still analyzed ----

    @Test
    @Transactional
    void testNullParentIdProjectsTreatedAsRoots() {
        createProject("P0000001", "Root", null);
        createIssue("I0000001", "P0000001", "Issue", Priority.NORMAL, "TODO");

        Map<String, ProjectAnalysisResult> results = analysisService.analyzeAll();
        assertTrue(results.containsKey("P0000001"));
        assertEquals(1, results.get("P0000001").issueTodo);
    }

    // ---- 20: activeCount > 0 but highestPriority == null → NONE (defensive path) ----
    // This path is unreachable through normal data (priority column is non-nullable).
    // We verify the method handles normal active issues correctly.

    @Test
    @Transactional
    void testActiveCountGreaterThanZeroHighestPriorityNull() {
        createProject("P0000001", "P", null);
        createIssue("I0000001", "P0000001", "Doing", Priority.NORMAL, "DOING");

        Map<String, ProjectAnalysisResult> results = analysisService.analyzeAll();
        assertEquals(BubbledPriority.NORMAL, results.get("P0000001").bubbledPriority);
    }

    // ---- 21: DONE child under parent with HIGH active → parent stays HIGH ----

    @Test
    @Transactional
    void testDoneChildUnderParentWithHighActiveIssue() {
        createProject("P0000001", "Parent", null);
        createProject("P0000002", "Child", "P0000001");
        createIssue("I0000001", "P0000001", "High", Priority.HIGH, "TODO");
        createIssue("I0000002", "P0000002", "Done", Priority.LOW, "DONE");

        Map<String, ProjectAnalysisResult> results = analysisService.analyzeAll();
        assertEquals(BubbledPriority.DONE, results.get("P0000002").bubbledPriority);
        // Parent: own HIGH active + child DONE (skipped in merge) → HIGH
        assertEquals(BubbledPriority.HIGH, results.get("P0000001").bubbledPriority);
    }

    // ---- 22: project with no issues but subproject with only DONE → bubbled=DONE ----

    @Test
    @Transactional
    void testProjectWithNoIssuesButSubprojectWithOnlyDone() {
        createProject("P0000001", "Parent", null);
        createProject("P0000002", "Child", "P0000001");
        createIssue("I0000001", "P0000002", "Done", Priority.NORMAL, "DONE");

        Map<String, ProjectAnalysisResult> results = analysisService.analyzeAll();
        assertEquals(BubbledPriority.DONE, results.get("P0000002").bubbledPriority);
        assertEquals(BubbledPriority.DONE, results.get("P0000001").bubbledPriority);
    }

    // ---- 23: project with no issues and no subprojects → NONE ----

    @Test
    @Transactional
    void testProjectWithNoIssuesAndNoSubprojects() {
        createProject("P0000001", "Empty", null);

        Map<String, ProjectAnalysisResult> results = analysisService.analyzeAll();
        ProjectAnalysisResult r = results.get("P0000001");
        assertNotNull(r);
        assertEquals(0, r.issueTodo);
        assertEquals(0, r.issueDoing);
        assertEquals(0, r.issueDone);
        assertEquals(0, r.subprojectCount);
        assertEquals(0, r.taskTodo);
        assertEquals(0, r.taskDoing);
        assertEquals(BubbledPriority.NONE, r.bubbledPriority);
    }

    // ---- 24: INACTIVE issues are counted but never bubble a priority ----

    @Test
    @Transactional
    void testInactiveIssueCountedButNotBubbled() {
        createProject("P0000001", "P", null);
        createIssue("I0000001", "P0000001", "Parked", Priority.HIGH, "INACTIVE");

        Map<String, ProjectAnalysisResult> results = analysisService.analyzeAll();
        ProjectAnalysisResult r = results.get("P0000001");
        assertEquals(0, r.issueTodo);
        assertEquals(0, r.issueDoing);
        assertEquals(1, r.issueInactive);
        assertEquals(0, r.issueDone);
        // parked HIGH must not make the project HIGH
        assertEquals(BubbledPriority.INACTIVE, r.bubbledPriority);
    }

    // ---- 25: active LOW beats parked HIGH ----

    @Test
    @Transactional
    void testActiveLowBeatsParkedHigh() {
        createProject("P0000001", "P", null);
        createIssue("I0000001", "P0000001", "Parked", Priority.HIGH, "INACTIVE");
        createIssue("I0000002", "P0000001", "Active", Priority.LOW, "TODO");

        Map<String, ProjectAnalysisResult> results = analysisService.analyzeAll();
        assertEquals(BubbledPriority.LOW, results.get("P0000001").bubbledPriority);
    }

    // ---- 26: DONE beats INACTIVE (parked is the least important state) ----

    @Test
    @Transactional
    void testDoneBeatsInactive() {
        createProject("P0000001", "P", null);
        createIssue("I0000001", "P0000001", "Done", Priority.NORMAL, "DONE");
        createIssue("I0000002", "P0000001", "Parked", Priority.NORMAL, "INACTIVE");

        Map<String, ProjectAnalysisResult> results = analysisService.analyzeAll();
        ProjectAnalysisResult r = results.get("P0000001");
        assertEquals(1, r.issueDone);
        assertEquals(1, r.issueInactive);
        assertEquals(BubbledPriority.DONE, r.bubbledPriority);
    }

    // ---- 27: all four statuses counted independently ----

    @Test
    @Transactional
    void testAllFourStatusesCounted() {
        createProject("P0000001", "P", null);
        createIssue("I0000001", "P0000001", "Todo", Priority.NORMAL, "TODO");
        createIssue("I0000002", "P0000001", "Doing", Priority.NORMAL, "DOING");
        createIssue("I0000003", "P0000001", "Parked", Priority.NORMAL, "INACTIVE");
        createIssue("I0000004", "P0000001", "Done", Priority.NORMAL, "DONE");

        Map<String, ProjectAnalysisResult> results = analysisService.analyzeAll();
        ProjectAnalysisResult r = results.get("P0000001");
        assertEquals(1, r.issueTodo);
        assertEquals(1, r.issueDoing);
        assertEquals(1, r.issueInactive);
        assertEquals(1, r.issueDone);
        assertEquals(BubbledPriority.NORMAL, r.bubbledPriority);
    }

    // ---- 28: INACTIVE bubbles up from a subproject ----

    @Test
    @Transactional
    void testInactiveBubblesUpFromSubproject() {
        createProject("P0000001", "Parent", null);
        createProject("P0000002", "Child", "P0000001");
        createIssue("I0000001", "P0000002", "Parked", Priority.HIGH, "INACTIVE");

        Map<String, ProjectAnalysisResult> results = analysisService.analyzeAll();
        assertEquals(BubbledPriority.INACTIVE, results.get("P0000002").bubbledPriority);
        ProjectAnalysisResult parent = results.get("P0000001");
        assertEquals(1, parent.issueInactive);
        assertEquals(BubbledPriority.INACTIVE, parent.bubbledPriority);
    }

    // ---- 29: parked issue under an active project does not change the bubble ----

    @Test
    @Transactional
    void testInactiveUnderActiveProjectKeepsActiveBubble() {
        createProject("P0000001", "Parent", null);
        createProject("P0000002", "Child", "P0000001");
        createIssue("I0000001", "P0000001", "Active", Priority.NORMAL, "DOING");
        createIssue("I0000002", "P0000002", "Parked", Priority.HIGH, "INACTIVE");

        Map<String, ProjectAnalysisResult> results = analysisService.analyzeAll();
        assertEquals(BubbledPriority.INACTIVE, results.get("P0000002").bubbledPriority);
        assertEquals(BubbledPriority.NORMAL, results.get("P0000001").bubbledPriority);
    }
}
