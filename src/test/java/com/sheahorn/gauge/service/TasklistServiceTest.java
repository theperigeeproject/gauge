package pl.perigee.gauge.service;

import pl.perigee.gauge.domain.*;
import pl.perigee.gauge.repository.TaskRepository;
import pl.perigee.gauge.repository.TasklistRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class TasklistServiceTest {

    @Inject
    TasklistService tasklistService;

    @Inject
    TasklistRepository tasklistRepository;

    @Inject
    TaskRepository taskRepository;

    @Inject
    EntityManager em;

    @BeforeEach
    @Transactional
    void cleanUp() {
        em.createNativeQuery("DELETE FROM tasks").executeUpdate();
        em.createNativeQuery("DELETE FROM tasklists").executeUpdate();
    }

    // ---- helpers ----

    private Tasklist createTasklist(String issueId, String title) {
        return tasklistService.create(issueId, title, null);
    }

    private Task createTask(String id, String tasklistId) {
        Task t = new Task(id, tasklistId, 0, "Task", "desc", TaskStatus.TODO);
        taskRepository.save(t);
        return t;
    }

    /**
     * Creates a tasklist with a single task in it. Returns both.
     */
    private record TasklistWithTask(Tasklist tasklist, Task task) {}

    private TasklistWithTask createTasklistWithTask(String issueId, String tasklistTitle, String taskId) {
        Tasklist tl = createTasklist(issueId, tasklistTitle);
        Task t = createTask(taskId, tl.id());
        return new TasklistWithTask(tl, t);
    }

    // ========================================================================
    // 0: create — basic
    // ========================================================================

    @Test
    @Transactional
    void testCreateTasklist() {
        Tasklist tl = tasklistService.create("I0000001", "Test Tasklist", null);
        assertNotNull(tl);
        assertNotNull(tl.id());
        assertEquals("I0000001", tl.issueId());
        assertEquals("Test Tasklist", tl.title());
        assertEquals(TasklistStatus.TODO, tl.status());
        assertNull(tl.decomposesTaskId());
    }

    // ========================================================================
    // 1: create with decomposesTaskId — valid (different tasklist, same issue)
    // ========================================================================

    @Test
    @Transactional
    void testCreateWithDecomposesTaskIdValid() {
        // Setup: tasklist A with task T1 in issue I0000001
        TasklistWithTask other = createTasklistWithTask("I0000001", "Other Tasklist", "T0000001");

        // Create tasklist B that decomposes T1
        Tasklist tl = tasklistService.create("I0000001", "Decomposed", "T0000001");
        assertEquals("T0000001", tl.decomposesTaskId());
    }

    // ========================================================================
    // 2: create with decomposesTaskId — non-existent task
    // ========================================================================

    @Test
    @Transactional
    void testCreateWithDecomposesTaskIdNonExistent() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            tasklistService.create("I0000001", "Bad", "NONEXISTENT"));
        assertTrue(ex.getMessage().contains("non-existent task"));
    }

    // ========================================================================
    // 3: create with decomposesTaskId — different issue
    // ========================================================================

    @Test
    @Transactional
    void testCreateWithDecomposesTaskIdDifferentIssue() {
        // Task T1 lives in issue I0000002
        TasklistWithTask other = createTasklistWithTask("I0000002", "Other", "T0000001");

        // Try to create tasklist in I0000001 decomposing T1 (which is in I0000002)
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            tasklistService.create("I0000001", "Bad", "T0000001"));
        assertTrue(ex.getMessage().contains("different issue"));
    }

    // ========================================================================
    // 4: create with decomposesTaskId — null (valid)
    // ========================================================================

    @Test
    @Transactional
    void testCreateWithDecomposesTaskIdNull() {
        Tasklist tl = tasklistService.create("I0000001", "No Decompose", null);
        assertNull(tl.decomposesTaskId());
    }

    // ========================================================================
    // 5: findById — exists / empty
    // ========================================================================

    @Test
    @Transactional
    void testFindById() {
        Tasklist created = createTasklist("I0000001", "Find Me");
        Optional<Tasklist> found = tasklistService.findById(created.id());
        assertTrue(found.isPresent());
        assertEquals("Find Me", found.get().title());
    }

    @Test
    @Transactional
    void testFindByIdNotFound() {
        Optional<Tasklist> found = tasklistService.findById("NONEXISTENT");
        assertFalse(found.isPresent());
    }

    // ========================================================================
    // 6: findByIssueId
    // ========================================================================

    @Test
    @Transactional
    void testFindByIssueId() {
        createTasklist("I0000001", "In I1");
        createTasklist("I0000002", "In I2");

        List<Tasklist> i1Tasklists = tasklistService.findByIssueId("I0000001");
        assertEquals(1, i1Tasklists.size());
        assertEquals("In I1", i1Tasklists.get(0).title());
    }

    // ========================================================================
    // 7: search
    // ========================================================================

    @Test
    @Transactional
    void testSearch() {
        createTasklist("I0000001", "Searchable Title");
        createTasklist("I0000001", "Other");
        createTasklist("I0000001", "No Match");

        List<Tasklist> results = tasklistService.search("searchable");
        assertEquals(1, results.size());
    }

    @Test
    @Transactional
    void testSearchEmptyStringMatchesAll() {
        createTasklist("I0000001", "One");
        createTasklist("I0000001", "Two");

        List<Tasklist> results = tasklistService.search("");
        List<Tasklist> all = tasklistService.findAll();
        assertEquals(all.size(), results.size());
    }

    // ========================================================================
    // 8: patch
    // ========================================================================

    @Test
    @Transactional
    void testPatch() {
        Tasklist created = createTasklist("I0000001", "Old Title");
        Optional<Tasklist> patched = tasklistService.patch(created.id(), "New Title");
        assertTrue(patched.isPresent());
        assertEquals("New Title", patched.get().title());
    }

    @Test
    @Transactional
    void testPatchNullKeepsExisting() {
        Tasklist created = createTasklist("I0000001", "Title");
        Optional<Tasklist> patched = tasklistService.patch(created.id(), null);
        assertTrue(patched.isPresent());
        assertEquals("Title", patched.get().title());
    }

    @Test
    @Transactional
    void testPatchNotFound() {
        Optional<Tasklist> patched = tasklistService.patch("NONEXISTENT", "Title");
        assertFalse(patched.isPresent());
    }

    // ========================================================================
    // 9: updateStatus
    // ========================================================================

    @Test
    @Transactional
    void testUpdateStatusToDoing() {
        Tasklist created = createTasklist("I0000001", "Test");
        Optional<Tasklist> updated = tasklistService.updateStatus(created.id(), TasklistStatus.DOING);
        assertTrue(updated.isPresent());
        assertEquals(TasklistStatus.DOING, updated.get().status());
    }

    @Test
    @Transactional
    void testUpdateStatusToDone() {
        Tasklist created = createTasklist("I0000001", "Test");
        Optional<Tasklist> updated = tasklistService.updateStatus(created.id(), TasklistStatus.DONE);
        assertTrue(updated.isPresent());
        assertEquals(TasklistStatus.DONE, updated.get().status());
    }

    @Test
    @Transactional
    void testUpdateStatusToCanceled() {
        Tasklist created = createTasklist("I0000001", "Test");
        Optional<Tasklist> updated = tasklistService.updateStatus(created.id(), TasklistStatus.CANCELED);
        assertTrue(updated.isPresent());
        assertEquals(TasklistStatus.CANCELED, updated.get().status());
    }

    @Test
    @Transactional
    void testUpdateStatusNotFound() {
        Optional<Tasklist> updated = tasklistService.updateStatus("NONEXISTENT", TasklistStatus.DOING);
        assertFalse(updated.isPresent());
    }

    // ========================================================================
    // 10: updateDecomposesTask — valid transitions
    // ========================================================================

    @Test
    @Transactional
    void testUpdateDecomposesTaskSetValid() {
        // Tasklist A with task T1 in issue I0000001
        TasklistWithTask other = createTasklistWithTask("I0000001", "Other", "T0000001");
        // Tasklist B (no decomposesTaskId yet)
        Tasklist tl = createTasklist("I0000001", "Target");

        Optional<Tasklist> updated = tasklistService.updateDecomposesTask(tl.id(), "T0000001");
        assertTrue(updated.isPresent());
        assertEquals("T0000001", updated.get().decomposesTaskId());
    }

    @Test
    @Transactional
    void testUpdateDecomposesTaskUnset() {
        // Tasklist A with task T1
        TasklistWithTask other = createTasklistWithTask("I0000001", "Other", "T0000001");
        // Tasklist B that decomposes T1
        Tasklist tl = tasklistService.create("I0000001", "Target", "T0000001");

        Optional<Tasklist> updated = tasklistService.updateDecomposesTask(tl.id(), null);
        assertTrue(updated.isPresent());
        assertNull(updated.get().decomposesTaskId());
    }

    @Test
    @Transactional
    void testUpdateDecomposesTaskChangeValid() {
        // Tasklist A with tasks T1 and T2
        TasklistWithTask other = createTasklistWithTask("I0000001", "Other", "T0000001");
        createTask("T0000002", other.tasklist().id());
        // Tasklist B decomposes T1
        Tasklist tl = tasklistService.create("I0000001", "Target", "T0000001");

        // Change to T2 (same issue, different tasklist)
        Optional<Tasklist> updated = tasklistService.updateDecomposesTask(tl.id(), "T0000002");
        assertTrue(updated.isPresent());
        assertEquals("T0000002", updated.get().decomposesTaskId());
    }

    // ========================================================================
    // 11: updateDecomposesTask — non-existent task
    // ========================================================================

    @Test
    @Transactional
    void testUpdateDecomposesTaskNonExistent() {
        Tasklist tl = createTasklist("I0000001", "Target");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            tasklistService.updateDecomposesTask(tl.id(), "NONEXISTENT"));
        assertTrue(ex.getMessage().contains("non-existent task"));
    }

    // ========================================================================
    // 12: updateDecomposesTask — different issue
    // ========================================================================

    @Test
    @Transactional
    void testUpdateDecomposesTaskDifferentIssue() {
        // Task T1 in issue I0000002
        TasklistWithTask other = createTasklistWithTask("I0000002", "Other", "T0000001");
        // Tasklist in issue I0000001
        Tasklist tl = createTasklist("I0000001", "Target");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            tasklistService.updateDecomposesTask(tl.id(), "T0000001"));
        assertTrue(ex.getMessage().contains("different issue"));
    }

    // ========================================================================
    // 13: updateDecomposesTask — same tasklist
    // ========================================================================

    @Test
    @Transactional
    void testUpdateDecomposesTaskSameTasklist() {
        // Tasklist A with task T1
        TasklistWithTask pair = createTasklistWithTask("I0000001", "Only", "T0000001");

        // Try to make tasklist A decompose its own task T1
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            tasklistService.updateDecomposesTask(pair.tasklist().id(), "T0000001"));
        assertTrue(ex.getMessage().contains("same tasklist"));
    }

    // ========================================================================
    // 14: updateDecomposesTask — non-existent tasklist
    // ========================================================================

    @Test
    @Transactional
    void testUpdateDecomposesTaskNotFound() {
        Optional<Tasklist> updated = tasklistService.updateDecomposesTask("NONEXISTENT", "T0000001");
        assertFalse(updated.isPresent());
    }

    // ========================================================================
    // 15: updateDecomposesTask — task in non-existent tasklist (edge case)
    // ========================================================================

    @Test
    @Transactional
    void testUpdateDecomposesTaskOrphanedTask() {
        // Create a task whose tasklistId points to a non-existent tasklist
        Task orphan = new Task("T0000001", "NONEXISTENT_TL", 0, "Orphan", "desc", TaskStatus.TODO);
        taskRepository.save(orphan);
        Tasklist tl = createTasklist("I0000001", "Target");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            tasklistService.updateDecomposesTask(tl.id(), "T0000001"));
        assertTrue(ex.getMessage().contains("non-existent tasklist"));
    }

    // ========================================================================
    // 16: hasChildren
    // ========================================================================

    @Test
    @Transactional
    void testHasChildren() {
        Tasklist created = createTasklist("I0000001", "Test");
        assertFalse(tasklistService.hasChildren(created.id()));

        createTask("T0000001", created.id());
        assertTrue(tasklistService.hasChildren(created.id()));
    }

    @Test
    @Transactional
    void testHasChildrenNotFound() {
        assertFalse(tasklistService.hasChildren("NONEXISTENT"));
    }

    // ========================================================================
    // 17: cascadeDelete
    // ========================================================================

    @Test
    @Transactional
    void testCascadeDelete() {
        Tasklist created = createTasklist("I0000001", "Test");
        createTask("T0000001", created.id());

        assertTrue(tasklistService.cascadeDelete(created.id()));
        assertFalse(tasklistService.findById(created.id()).isPresent());
        assertFalse(taskRepository.findById("T0000001").isPresent());
    }

    @Test
    @Transactional
    void testCascadeDeleteNotFound() {
        assertFalse(tasklistService.cascadeDelete("NONEXISTENT"));
    }

    // ========================================================================
    // 18: deleteById
    // ========================================================================

    @Test
    @Transactional
    void testDeleteById() {
        Tasklist created = createTasklist("I0000001", "Delete");
        assertTrue(tasklistService.deleteById(created.id()));
        assertFalse(tasklistService.findById(created.id()).isPresent());
    }

    @Test
    @Transactional
    void testDeleteByIdNotFound() {
        assertFalse(tasklistService.deleteById("NONEXISTENT"));
    }

    @Test
    @Transactional
    void testDeleteByIdOrphansTasks() {
        Tasklist created = createTasklist("I0000001", "Test");
        createTask("T0000001", created.id());

        assertTrue(tasklistService.deleteById(created.id()));
        Optional<Task> orphaned = taskRepository.findById("T0000001");
        assertTrue(orphaned.isPresent());
        assertEquals(created.id(), orphaned.get().tasklistId());
    }

    // ========================================================================
    // 19: decomposesTaskId survives patch and status updates
    // ========================================================================

    @Test
    @Transactional
    void testDecomposesTaskIdSurvivesPatch() {
        TasklistWithTask other = createTasklistWithTask("I0000001", "Other", "T0000001");
        Tasklist tl = tasklistService.create("I0000001", "Target", "T0000001");

        // Patch title — decomposesTaskId should be preserved
        Optional<Tasklist> patched = tasklistService.patch(tl.id(), "New Title");
        assertTrue(patched.isPresent());
        assertEquals("T0000001", patched.get().decomposesTaskId());
    }

    @Test
    @Transactional
    void testDecomposesTaskIdSurvivesStatusUpdate() {
        TasklistWithTask other = createTasklistWithTask("I0000001", "Other", "T0000001");
        Tasklist tl = tasklistService.create("I0000001", "Target", "T0000001");

        // Update status — decomposesTaskId should be preserved
        Optional<Tasklist> updated = tasklistService.updateStatus(tl.id(), TasklistStatus.DOING);
        assertTrue(updated.isPresent());
        assertEquals("T0000001", updated.get().decomposesTaskId());
    }

    // ========================================================================
    // 20: create with decomposesTaskId — task in non-existent tasklist
    // ========================================================================

    @Test
    @Transactional
    void testCreateWithDecomposesTaskIdOrphanedTask() {
        // Create a task whose tasklistId points nowhere
        Task orphan = new Task("T0000001", "NONEXISTENT_TL", 0, "Orphan", "desc", TaskStatus.TODO);
        taskRepository.save(orphan);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            tasklistService.create("I0000001", "Bad", "T0000001"));
        assertTrue(ex.getMessage().contains("non-existent tasklist"));
    }
}
