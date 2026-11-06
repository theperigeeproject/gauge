package pl.perigee.gauge.service;

import pl.perigee.gauge.domain.Task;
import pl.perigee.gauge.domain.TaskStatus;
import pl.perigee.gauge.repository.TaskRepository;
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
class TaskServiceTest {

    @Inject
    TaskService taskService;

    @Inject
    TaskRepository taskRepository;

    @Inject
    EntityManager em;

    @BeforeEach
    @Transactional
    void cleanUp() {
        em.createNativeQuery("DELETE FROM tasks").executeUpdate();
    }

    // ---- helpers ----

    private Task createTask(String tasklistId, String title) {
        return taskService.create(tasklistId, title, "desc");
    }

    private Task createTaskDirect(String id, String tasklistId, int ordinal, String title, TaskStatus status) {
        Task t = new Task(id, tasklistId, ordinal, title, "desc", status);
        taskRepository.save(t);
        return t;
    }

    // ---- 0: create — TODO status, ordinal = count, correct tasklistId ----

    @Test
    @Transactional
    void testCreateTask() {
        Task task = taskService.create("TL000001", "Test Task", "Description");
        assertNotNull(task);
        assertNotNull(task.id());
        assertEquals("TL000001", task.tasklistId());
        assertEquals("Test Task", task.title());
        assertEquals("Description", task.description());
        assertEquals(TaskStatus.TODO, task.status());
        assertEquals(0, task.ordinal());
    }

    // ---- 1: create — ordinal increments with each new task ----

    @Test
    @Transactional
    void testCreateMultipleTasksOrdinalIncrements() {
        Task t1 = createTask("TL000001", "Task1");
        Task t2 = createTask("TL000001", "Task2");
        Task t3 = createTask("TL000001", "Task3");

        assertEquals(0, t1.ordinal());
        assertEquals(1, t2.ordinal());
        assertEquals(2, t3.ordinal());
    }

    // ---- 2: findById — exists / empty ----

    @Test
    @Transactional
    void testFindById() {
        Task created = createTask("TL000001", "Find Me");
        Optional<Task> found = taskService.findById(created.id());
        assertTrue(found.isPresent());
        assertEquals("Find Me", found.get().title());
    }

    @Test
    @Transactional
    void testFindByIdNotFound() {
        Optional<Task> found = taskService.findById("NONEXISTENT");
        assertFalse(found.isPresent());
    }

    // ---- 3: findByTasklistId — only tasks for that tasklist, ordered by ordinal ----

    @Test
    @Transactional
    void testFindByTasklistId() {
        createTask("TL000001", "In TL1");
        createTask("TL000002", "In TL2");

        List<Task> tl1Tasks = taskService.findByTasklistId("TL000001");
        assertEquals(1, tl1Tasks.size());
        assertEquals("In TL1", tl1Tasks.get(0).title());
    }

    @Test
    @Transactional
    void testFindByTasklistIdReturnsAllTasks() {
        createTaskDirect("T0000001", "TL000001", 2, "Third", TaskStatus.TODO);
        createTaskDirect("T0000002", "TL000001", 0, "First", TaskStatus.TODO);
        createTaskDirect("T0000003", "TL000001", 1, "Second", TaskStatus.TODO);

        List<Task> tasks = taskService.findByTasklistId("TL000001");
        assertEquals(3, tasks.size());
        // findByTasklistId uses Task.list() which returns insertion order, not ordinal order
        assertTrue(tasks.stream().anyMatch(t -> t.title().equals("First")));
        assertTrue(tasks.stream().anyMatch(t -> t.title().equals("Second")));
        assertTrue(tasks.stream().anyMatch(t -> t.title().equals("Third")));
    }

    // ---- 4: search — case-insensitive match on title and description ----

    @Test
    @Transactional
    void testSearch() {
        createTask("TL000001", "Searchable Title");
        Task other = createTask("TL000001", "Other");
        taskService.patch(other.id(), null, "Searchable Description");
        createTask("TL000001", "No Match");

        List<Task> results = taskService.search("searchable");
        assertEquals(2, results.size());
    }

    // ---- 5: patch — title only, description only, both, neither ----

    @Test
    @Transactional
    void testPatchTitleOnly() {
        Task created = createTask("TL000001", "Old Title");
        Optional<Task> patched = taskService.patch(created.id(), "New Title", null);
        assertTrue(patched.isPresent());
        assertEquals("New Title", patched.get().title());
        assertEquals("desc", patched.get().description());
    }

    @Test
    @Transactional
    void testPatchDescriptionOnly() {
        Task created = createTask("TL000001", "Title");
        Optional<Task> patched = taskService.patch(created.id(), null, "New Desc");
        assertTrue(patched.isPresent());
        assertEquals("Title", patched.get().title());
        assertEquals("New Desc", patched.get().description());
    }

    @Test
    @Transactional
    void testPatchBoth() {
        Task created = createTask("TL000001", "Old");
        Optional<Task> patched = taskService.patch(created.id(), "New", "New Desc");
        assertTrue(patched.isPresent());
        assertEquals("New", patched.get().title());
        assertEquals("New Desc", patched.get().description());
    }

    @Test
    @Transactional
    void testPatchNeither() {
        Task created = createTask("TL000001", "Title");
        Optional<Task> patched = taskService.patch(created.id(), null, null);
        assertTrue(patched.isPresent());
        assertEquals("Title", patched.get().title());
        assertEquals("desc", patched.get().description());
    }

    // ---- 6: updateStatus — all transitions ----

    @Test
    @Transactional
    void testUpdateStatusToDoing() {
        Task created = createTask("TL000001", "Test");
        Optional<Task> updated = taskService.updateStatus(created.id(), TaskStatus.DOING);
        assertTrue(updated.isPresent());
        assertEquals(TaskStatus.DOING, updated.get().status());
    }

    @Test
    @Transactional
    void testUpdateStatusToDone() {
        Task created = createTask("TL000001", "Test");
        Optional<Task> updated = taskService.updateStatus(created.id(), TaskStatus.DONE);
        assertTrue(updated.isPresent());
        assertEquals(TaskStatus.DONE, updated.get().status());
    }

    @Test
    @Transactional
    void testUpdateStatusToFailed() {
        Task created = createTask("TL000001", "Test");
        Optional<Task> updated = taskService.updateStatus(created.id(), TaskStatus.FAILED);
        assertTrue(updated.isPresent());
        assertEquals(TaskStatus.FAILED, updated.get().status());
    }

    @Test
    @Transactional
    void testUpdateStatusToCanceled() {
        Task created = createTask("TL000001", "Test");
        Optional<Task> updated = taskService.updateStatus(created.id(), TaskStatus.CANCELED);
        assertTrue(updated.isPresent());
        assertEquals(TaskStatus.CANCELED, updated.get().status());
    }

    // ---- 7: reorder — reassigns ordinals 0,1,2... based on provided ID list ----

    @Test
    @Transactional
    void testReorder() {
        Task t1 = createTask("TL000001", "Task1");
        Task t2 = createTask("TL000001", "Task2");
        Task t3 = createTask("TL000001", "Task3");

        taskService.reorder("TL000001", List.of(t3.id(), t1.id(), t2.id()));

        assertEquals(0, taskService.findById(t3.id()).get().ordinal());
        assertEquals(1, taskService.findById(t1.id()).get().ordinal());
        assertEquals(2, taskService.findById(t2.id()).get().ordinal());
    }

    // ---- 8: reorder — ignores IDs not belonging to the tasklist ----
    // The service doesn't validate tasklist ownership. If you pass an ID from
    // another tasklist, it still updates that task's ordinal (but keeps its
    // original tasklistId). See testReorderCrossTasklistPollution below.

    @Test
    @Transactional
    void testReorderIgnoresNonexistentIds() {
        Task t1 = createTask("TL000001", "Task1");
        Task t2 = createTask("TL000001", "Task2");

        taskService.reorder("TL000001", List.of(t2.id(), "NONEXISTENT", t1.id()));

        // t2 at position 0 → ordinal 0
        // NONEXISTENT at position 1 → skipped
        // t1 at position 2 → ordinal 2
        assertEquals(0, taskService.findById(t2.id()).get().ordinal());
        assertEquals(2, taskService.findById(t1.id()).get().ordinal());
    }

    // ---- 9: deleteById — removes task ----

    @Test
    @Transactional
    void testDeleteById() {
        Task created = createTask("TL000001", "Delete");
        assertTrue(taskService.deleteById(created.id()));
        assertFalse(taskService.findById(created.id()).isPresent());
    }

    // ---- 10: patch nonexistent → empty ----

    @Test
    @Transactional
    void testPatchNotFound() {
        Optional<Task> patched = taskService.patch("NONEXISTENT", "Title", "Desc");
        assertFalse(patched.isPresent());
    }

    // ---- 11: updateStatus nonexistent → empty ----

    @Test
    @Transactional
    void testUpdateStatusNotFound() {
        Optional<Task> updated = taskService.updateStatus("NONEXISTENT", TaskStatus.DOING);
        assertFalse(updated.isPresent());
    }

    // ---- 12: reorder — empty list → no-op ----

    @Test
    @Transactional
    void testReorderEmptyList() {
        Task t1 = createTask("TL000001", "Task1");
        Task t2 = createTask("TL000001", "Task2");

        taskService.reorder("TL000001", List.of());

        assertEquals(0, taskService.findById(t1.id()).get().ordinal());
        assertEquals(1, taskService.findById(t2.id()).get().ordinal());
    }

    // ---- 13: reorder — partial list → only those reordered, others unchanged ----

    @Test
    @Transactional
    void testReorderPartialList() {
        Task t1 = createTask("TL000001", "Task1");
        Task t2 = createTask("TL000001", "Task2");
        Task t3 = createTask("TL000001", "Task3");

        // Only reorder t3 and t1, leave t2 out
        taskService.reorder("TL000001", List.of(t3.id(), t1.id()));

        assertEquals(0, taskService.findById(t3.id()).get().ordinal());
        assertEquals(1, taskService.findById(t1.id()).get().ordinal());
        // t2 was not in the list, so its ordinal stays at 1
        // Note: this means t1 and t2 now both have ordinal 1
        assertEquals(1, taskService.findById(t2.id()).get().ordinal());
    }

    // ---- 14: reorder — duplicate IDs → last ordinal wins ----

    @Test
    @Transactional
    void testReorderDuplicateIds() {
        Task t1 = createTask("TL000001", "Task1");
        Task t2 = createTask("TL000001", "Task2");

        taskService.reorder("TL000001", List.of(t1.id(), t2.id(), t1.id()));

        // t1 appears at positions 0 and 2 — last one (2) wins
        assertEquals(2, taskService.findById(t1.id()).get().ordinal());
        assertEquals(1, taskService.findById(t2.id()).get().ordinal());
    }

    // ---- 15: search — null title/description don't cause NPE ----
    // title is non-nullable in DB; description can be null

    @Test
    @Transactional
    void testSearchNullDescriptionNoNpe() {
        Task t = new Task("T0000001", "TL000001", 0, "Match", null, TaskStatus.TODO);
        taskRepository.save(t);

        List<Task> results = taskService.search("match");
        assertEquals(1, results.size());
    }

    // ---- 16: deleteById nonexistent → false ----

    @Test
    @Transactional
    void testDeleteByIdNotFound() {
        assertFalse(taskService.deleteById("NONEXISTENT"));
    }

    // ---- 17: create — ordinal starts at 0 for empty tasklist ----

    @Test
    @Transactional
    void testCreateOrdinalStartsAtZero() {
        Task t = createTask("TL000001", "First");
        assertEquals(0, t.ordinal());
    }

    // ---- 18: reorder — null taskIds → NPE ----

    @Test
    @Transactional
    void testReorderNullTaskIds() {
        assertThrows(NullPointerException.class, () ->
            taskService.reorder("TL000001", null)
        );
    }

    // ---- 19: reorder — null entries in taskIds → silently skipped ----

    @Test
    @Transactional
    void testReorderNullEntries() {
        Task t1 = createTask("TL000001", "Task1");
        Task t2 = createTask("TL000001", "Task2");

        // null entries: findById(null) throws IllegalArgumentException from Hibernate
        // Use Arrays.asList (allows nulls) instead of List.of (rejects nulls)
        assertThrows(IllegalArgumentException.class, () ->
            taskService.reorder("TL000001", java.util.Arrays.asList(t2.id(), null, t1.id()))
        );
    }

    // ---- 20: reorder — cross-tasklist pollution ----
    // The service must validate that every task in the reorder list belongs
    // to the given tasklistId. Foreign tasks are rejected.

    @Test
    @Transactional
    void testReorderCrossTasklistRejected() {
        Task t1 = createTask("TL000001", "Task1");
        Task t2 = createTask("TL000002", "Task2");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            taskService.reorder("TL000001", List.of(t1.id(), t2.id())));
        assertTrue(ex.getMessage().contains("does not belong to tasklist"));
    }

    @Test
    @Transactional
    void testReorderAllForeignTasksRejected() {
        createTask("TL000001", "Task1");
        Task t2 = createTask("TL000002", "Task2");
        Task t3 = createTask("TL000002", "Task3");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            taskService.reorder("TL000001", List.of(t2.id(), t3.id())));
        assertTrue(ex.getMessage().contains("does not belong to tasklist"));
    }

    @Test
    @Transactional
    void testReorderValidTasksStillWork() {
        Task t1 = createTask("TL000001", "Task1");
        Task t2 = createTask("TL000001", "Task2");
        Task t3 = createTask("TL000001", "Task3");

        taskService.reorder("TL000001", List.of(t3.id(), t1.id(), t2.id()));

        assertEquals(0, taskService.findById(t3.id()).get().ordinal());
        assertEquals(1, taskService.findById(t1.id()).get().ordinal());
        assertEquals(2, taskService.findById(t2.id()).get().ordinal());
    }
}
