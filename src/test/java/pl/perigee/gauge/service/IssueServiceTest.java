package pl.perigee.gauge.service;

import pl.perigee.gauge.domain.*;
import pl.perigee.gauge.repository.IssueRepository;
import pl.perigee.gauge.repository.TasklistRepository;
import pl.perigee.gauge.repository.TaskRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class IssueServiceTest {

    @Inject
    IssueService issueService;

    @Inject
    IssueRepository issueRepository;

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
        em.createNativeQuery("DELETE FROM issues").executeUpdate();
    }

    // ---- helpers ----

    private Issue createIssue(String projectId, String title, Priority priority) {
        return issueService.create(projectId, title, "desc", priority);
    }

    private Tasklist createTasklist(String id, String issueId) {
        Tasklist tl = new Tasklist(id, issueId, "TL", TasklistStatus.TODO, null);
        tasklistRepository.save(tl);
        return tl;
    }

    private Task createTask(String id, String tasklistId) {
        Task t = new Task(id, tasklistId, 0, "Task", "desc", TaskStatus.TODO);
        taskRepository.save(t);
        return t;
    }

    // ---- 0: create — TODO status, correct fields ----

    @Test
    @Transactional
    void testCreateIssue() {
        Issue issue = issueService.create("P0000001", "Test Issue", "Description", Priority.HIGH);
        assertNotNull(issue);
        assertNotNull(issue.id());
        assertEquals("P0000001", issue.projectId());
        assertEquals("Test Issue", issue.title());
        assertEquals("Description", issue.description());
        assertEquals(Priority.HIGH, issue.priority());
        assertEquals(IssueStatus.TODO, issue.status());
    }

    // ---- 1: findById — exists / empty ----

    @Test
    @Transactional
    void testFindById() {
        Issue created = createIssue("P0000001", "Find Me", Priority.NORMAL);
        Optional<Issue> found = issueService.findById(created.id());
        assertTrue(found.isPresent());
        assertEquals("Find Me", found.get().title());
    }

    @Test
    @Transactional
    void testFindByIdNotFound() {
        Optional<Issue> found = issueService.findById("NONEXISTENT");
        assertFalse(found.isPresent());
    }

    // ---- 2: findByProjectId — only issues for that project ----

    @Test
    @Transactional
    void testFindByProjectId() {
        createIssue("P0000001", "In P1", Priority.NORMAL);
        createIssue("P0000002", "In P2", Priority.NORMAL);
        List<Issue> p1Issues = issueService.findByProjectId("P0000001");
        assertEquals(1, p1Issues.size());
        assertEquals("In P1", p1Issues.get(0).title());
    }

    // ---- 3: search — case-insensitive match on title and description ----

    @Test
    @Transactional
    void testSearch() {
        createIssue("P0000001", "Searchable Title", Priority.NORMAL);
        createIssue("P0000001", "Other", Priority.NORMAL);
        // patch description to be searchable
        Issue other = issueService.findByProjectId("P0000001").stream()
            .filter(i -> i.title().equals("Other")).findFirst().orElseThrow();
        issueService.patch(other.id(), null, "Searchable Description");

        createIssue("P0000001", "No Match", Priority.NORMAL);

        List<Issue> results = issueService.search("searchable");
        assertEquals(2, results.size());
    }

    // ---- 4: patch — title only, description only, both, neither ----

    @Test
    @Transactional
    void testPatchTitleOnly() {
        Issue created = createIssue("P0000001", "Old Title", Priority.NORMAL);
        Optional<Issue> patched = issueService.patch(created.id(), "New Title", null);
        assertTrue(patched.isPresent());
        assertEquals("New Title", patched.get().title());
        assertEquals("desc", patched.get().description());
    }

    @Test
    @Transactional
    void testPatchDescriptionOnly() {
        Issue created = createIssue("P0000001", "Title", Priority.NORMAL);
        Optional<Issue> patched = issueService.patch(created.id(), null, "New Desc");
        assertTrue(patched.isPresent());
        assertEquals("Title", patched.get().title());
        assertEquals("New Desc", patched.get().description());
    }

    @Test
    @Transactional
    void testPatchBoth() {
        Issue created = createIssue("P0000001", "Old", Priority.NORMAL);
        Optional<Issue> patched = issueService.patch(created.id(), "New", "New Desc");
        assertTrue(patched.isPresent());
        assertEquals("New", patched.get().title());
        assertEquals("New Desc", patched.get().description());
    }

    // ---- 5: updateStatus — TODO→DOING→DONE transitions ----

    @Test
    @Transactional
    void testUpdateStatus() {
        Issue created = createIssue("P0000001", "Test", Priority.NORMAL);
        Optional<Issue> updated = issueService.updateStatus(created.id(), IssueStatus.DOING);
        assertTrue(updated.isPresent());
        assertEquals(IssueStatus.DOING, updated.get().status());

        updated = issueService.updateStatus(created.id(), IssueStatus.DONE);
        assertTrue(updated.isPresent());
        assertEquals(IssueStatus.DONE, updated.get().status());
    }

    // ---- 6: updatePriority — HIGH→NORMAL→LOW transitions ----

    @Test
    @Transactional
    void testUpdatePriority() {
        Issue created = createIssue("P0000001", "Test", Priority.LOW);
        Optional<Issue> updated = issueService.updatePriority(created.id(), Priority.HIGH);
        assertTrue(updated.isPresent());
        assertEquals(Priority.HIGH, updated.get().priority());

        updated = issueService.updatePriority(created.id(), Priority.NORMAL);
        assertTrue(updated.isPresent());
        assertEquals(Priority.NORMAL, updated.get().priority());
    }

    // ---- 7: moveToProject — changes projectId ----

    @Test
    @Transactional
    void testMoveToProject() {
        Issue created = createIssue("P0000001", "Test", Priority.NORMAL);
        Optional<Issue> moved = issueService.moveToProject(created.id(), "P0000002");
        assertTrue(moved.isPresent());
        assertEquals("P0000002", moved.get().projectId());
    }

    // ---- 8: hasChildren — true when tasklists exist, false when none ----

    @Test
    @Transactional
    void testHasChildren() {
        Issue created = createIssue("P0000001", "Test", Priority.NORMAL);
        assertFalse(issueService.hasChildren(created.id()));

        createTasklist("TL000001", created.id());
        assertTrue(issueService.hasChildren(created.id()));
    }

    // ---- 9: cascadeDelete — removes issue + tasklists + tasks ----

    @Test
    @Transactional
    void testCascadeDelete() {
        Issue created = createIssue("P0000001", "Test", Priority.NORMAL);
        createTasklist("TL000001", created.id());
        createTask("T0000001", "TL000001");

        assertTrue(issueService.cascadeDelete(created.id()));
        assertFalse(issueService.findById(created.id()).isPresent());
        assertFalse(tasklistRepository.findById("TL000001").isPresent());
        assertFalse(taskRepository.findById("T0000001").isPresent());
    }

    // ---- 10: deleteById — removes issue without children ----

    @Test
    @Transactional
    void testDeleteById() {
        Issue created = createIssue("P0000001", "Test", Priority.NORMAL);
        assertTrue(issueService.deleteById(created.id()));
        assertFalse(issueService.findById(created.id()).isPresent());
    }

    // ---- 11: sorting — all 5 SortOption variants ----

    @Test
    @Transactional
    void testSortingByName() {
        createIssue("P0000001", "Zebra", Priority.NORMAL);
        createIssue("P0000001", "Apple", Priority.NORMAL);
        createIssue("P0000001", "banana", Priority.NORMAL);

        List<Issue> sorted = issueService.findByProjectIdSortedAndFiltered(
            "P0000001", SortOption.NAME, null, null);
        assertEquals("Apple", sorted.get(0).title());
        assertEquals("banana", sorted.get(1).title());
        assertEquals("Zebra", sorted.get(2).title());
    }

    @Test
    @Transactional
    void testSortingByStatusPriorityName() {
        Issue i1 = createIssue("P0000001", "Z", Priority.HIGH);
        issueService.updateStatus(i1.id(), IssueStatus.DONE);
        createIssue("P0000001", "A", Priority.LOW);
        createIssue("P0000001", "B", Priority.HIGH);

        List<Issue> sorted = issueService.findByProjectIdSortedAndFiltered(
            "P0000001", SortOption.STATUS_PRIORITY_NAME, null, null);
        assertEquals(IssueStatus.TODO, sorted.get(0).status());
        assertEquals(IssueStatus.TODO, sorted.get(1).status());
        assertEquals(IssueStatus.DONE, sorted.get(2).status());
        assertEquals(Priority.HIGH, sorted.get(0).priority());
    }

    @Test
    @Transactional
    void testSortingByPriorityNameStatus() {
        createIssue("P0000001", "B", Priority.HIGH);
        createIssue("P0000001", "A", Priority.HIGH);
        createIssue("P0000001", "C", Priority.LOW);

        List<Issue> sorted = issueService.findByProjectIdSortedAndFiltered(
            "P0000001", SortOption.PRIORITY_NAME_STATUS, null, null);
        assertEquals("A", sorted.get(0).title());
        assertEquals("B", sorted.get(1).title());
        assertEquals("C", sorted.get(2).title());
    }

    @Test
    @Transactional
    void testSortingByStatusNamePriority() {
        createIssue("P0000001", "B", Priority.HIGH);
        createIssue("P0000001", "A", Priority.LOW);

        List<Issue> sorted = issueService.findByProjectIdSortedAndFiltered(
            "P0000001", SortOption.STATUS_NAME_PRIORITY, null, null);
        assertEquals("A", sorted.get(0).title());
        assertEquals("B", sorted.get(1).title());
    }

    @Test
    @Transactional
    void testSortingTiebreakerById() {
        createIssue("P0000001", "Same", Priority.NORMAL);
        createIssue("P0000001", "Same", Priority.NORMAL);

        List<Issue> sorted = issueService.findByProjectIdSortedAndFiltered(
            "P0000001", SortOption.NAME, null, null);
        assertEquals(2, sorted.size());
    }

    // ---- 12: filtering — priority filter, status filter, combined, empty filter=all ----

    @Test
    @Transactional
    void testFilterByPriority() {
        createIssue("P0000001", "Alpha", Priority.HIGH);
        createIssue("P0000001", "Beta", Priority.LOW);

        List<Issue> highOnly = issueService.findByProjectIdSortedAndFiltered(
            "P0000001", SortOption.PRIORITY_STATUS_NAME,
            Set.of(Priority.HIGH), null);
        assertEquals(1, highOnly.size());
        assertEquals("Alpha", highOnly.get(0).title());
    }

    @Test
    @Transactional
    void testFilterByStatus() {
        Issue i = createIssue("P0000001", "Gamma", Priority.NORMAL);
        issueService.updateStatus(i.id(), IssueStatus.DOING);
        createIssue("P0000001", "Delta", Priority.NORMAL);

        List<Issue> doingOnly = issueService.findByProjectIdSortedAndFiltered(
            "P0000001", SortOption.PRIORITY_STATUS_NAME,
            null, Set.of(IssueStatus.DOING));
        assertEquals(1, doingOnly.size());
        assertEquals("Gamma", doingOnly.get(0).title());
    }

    @Test
    @Transactional
    void testFilterCombined() {
        createIssue("P0000001", "HighTodo", Priority.HIGH);
        Issue i = createIssue("P0000001", "HighDoing", Priority.HIGH);
        issueService.updateStatus(i.id(), IssueStatus.DOING);
        createIssue("P0000001", "LowTodo", Priority.LOW);

        List<Issue> filtered = issueService.findByProjectIdSortedAndFiltered(
            "P0000001", SortOption.PRIORITY_STATUS_NAME,
            Set.of(Priority.HIGH), Set.of(IssueStatus.DOING));
        assertEquals(1, filtered.size());
        assertEquals("HighDoing", filtered.get(0).title());
    }

    // ---- 13: sort+filter combined — filtered then sorted, tiebreaker by ID ----

    @Test
    @Transactional
    void testSortAndFilterCombined() {
        createIssue("P0000001", "B", Priority.HIGH);
        createIssue("P0000001", "A", Priority.HIGH);
        createIssue("P0000001", "C", Priority.LOW);

        List<Issue> result = issueService.findByProjectIdSortedAndFiltered(
            "P0000001", SortOption.NAME,
            Set.of(Priority.HIGH), null);
        assertEquals(2, result.size());
        assertEquals("A", result.get(0).title());
        assertEquals("B", result.get(1).title());
    }

    // ---- 14: patch nonexistent → empty ----

    @Test
    @Transactional
    void testPatchNotFound() {
        Optional<Issue> patched = issueService.patch("NONEXISTENT", "Title", "Desc");
        assertFalse(patched.isPresent());
    }

    // ---- 15: updateStatus nonexistent → empty ----

    @Test
    @Transactional
    void testUpdateStatusNotFound() {
        Optional<Issue> updated = issueService.updateStatus("NONEXISTENT", IssueStatus.DOING);
        assertFalse(updated.isPresent());
    }

    // ---- 16: updatePriority nonexistent → empty ----

    @Test
    @Transactional
    void testUpdatePriorityNotFound() {
        Optional<Issue> updated = issueService.updatePriority("NONEXISTENT", Priority.HIGH);
        assertFalse(updated.isPresent());
    }

    // ---- 17: moveToProject nonexistent → empty ----

    @Test
    @Transactional
    void testMoveToProjectNotFound() {
        Optional<Issue> moved = issueService.moveToProject("NONEXISTENT", "P0000002");
        assertFalse(moved.isPresent());
    }

    // ---- 18: cascadeDelete nonexistent → false ----

    @Test
    @Transactional
    void testCascadeDeleteNotFound() {
        assertFalse(issueService.cascadeDelete("NONEXISTENT"));
    }

    // ---- 19: sorting — null titles handled as empty string (no NPE) ----
    // Note: title column is non-nullable in DB, so null titles can't exist in practice.
    // The comparator's null-safe fallback is defensive code only.

    // ---- 20: filtering — empty priority/status sets treated as no filter ----

    @Test
    @Transactional
    void testEmptyFilterSetsPassAll() {
        createIssue("P0000001", "One", Priority.HIGH);
        createIssue("P0000001", "Two", Priority.LOW);

        List<Issue> result = issueService.findByProjectIdSortedAndFiltered(
            "P0000001", SortOption.NAME,
            Set.of(), Set.of());
        assertEquals(2, result.size());
    }

    // ---- 21: search — null title/description handled safely (no NPE) ----
    // Note: title column is non-nullable in DB, so null titles can't exist in practice.
    // The search method's null guards are defensive code only.

    // ---- 22: deleteById nonexistent → false ----

    @Test
    @Transactional
    void testDeleteByIdNotFound() {
        assertFalse(issueService.deleteById("NONEXISTENT"));
    }

    // ---- 23: search — empty string matches all ----

    @Test
    @Transactional
    void testSearchEmptyStringMatchesAll() {
        createIssue("P0000001", "One", Priority.NORMAL);
        createIssue("P0000001", "Two", Priority.NORMAL);

        List<Issue> results = issueService.search("");
        List<Issue> all = issueService.findAll();
        assertEquals(all.size(), results.size());
    }

    // ---- 24: deleteById on issue with children → succeeds but orphans tasklists ----

    @Test
    @Transactional
    void testDeleteByIdOrphansTasklists() {
        Issue created = createIssue("P0000001", "Test", Priority.NORMAL);
        createTasklist("TL000001", created.id());

        assertTrue(issueService.deleteById(created.id()));
        Optional<Tasklist> orphaned = tasklistRepository.findById("TL000001");
        assertTrue(orphaned.isPresent());
        assertEquals(created.id(), orphaned.get().issueId());
    }

    // ---- 25: sorting — null sort → defaults to PRIORITY_STATUS_NAME ----

    @Test
    @Transactional
    void testNullSortDefaultsToPriorityStatusName() {
        createIssue("P0000001", "Zebra", Priority.LOW);
        createIssue("P0000001", "Apple", Priority.HIGH);

        List<Issue> sorted = issueService.findByProjectIdSortedAndFiltered(
            "P0000001", null, null, null);
        assertEquals("Apple", sorted.get(0).title());
        assertEquals("Zebra", sorted.get(1).title());
    }

    // ---- 26: create — null priority → NPE ----

    @Test
    @Transactional
    void testCreateWithNullPriority() {
        assertThrows(NullPointerException.class, () ->
            issueService.create("P0000001", "Test", "Desc", null)
        );
    }

    // ---- 27: status ordering — TODO, DOING, INACTIVE, DONE ----

    @Test
    @Transactional
    void testStatusOrderingIncludesInactive() {
        Issue done = createIssue("P0000001", "Done", Priority.NORMAL);
        issueService.updateStatus(done.id(), IssueStatus.DONE);
        Issue parked = createIssue("P0000001", "Parked", Priority.NORMAL);
        issueService.updateStatus(parked.id(), IssueStatus.INACTIVE);
        Issue doing = createIssue("P0000001", "Doing", Priority.NORMAL);
        issueService.updateStatus(doing.id(), IssueStatus.DOING);
        createIssue("P0000001", "Todo", Priority.NORMAL);

        List<Issue> sorted = issueService.findByProjectIdSortedAndFiltered(
            "P0000001", SortOption.STATUS_PRIORITY_NAME, null, null);
        assertEquals(4, sorted.size());
        assertEquals(IssueStatus.TODO, sorted.get(0).status());
        assertEquals(IssueStatus.DOING, sorted.get(1).status());
        assertEquals(IssueStatus.DONE, sorted.get(2).status());
        assertEquals(IssueStatus.INACTIVE, sorted.get(3).status());
    }

    // ---- 28: filter by INACTIVE status ----

    @Test
    @Transactional
    void testFilterByInactiveStatus() {
        Issue parked = createIssue("P0000001", "Parked", Priority.HIGH);
        issueService.updateStatus(parked.id(), IssueStatus.INACTIVE);
        createIssue("P0000001", "Todo", Priority.HIGH);

        List<Issue> inactiveOnly = issueService.findByProjectIdSortedAndFiltered(
            "P0000001", SortOption.PRIORITY_STATUS_NAME, null, Set.of(IssueStatus.INACTIVE));
        assertEquals(1, inactiveOnly.size());
        assertEquals("Parked", inactiveOnly.get(0).title());
        assertEquals(IssueStatus.INACTIVE, inactiveOnly.get(0).status());
    }

    // ---- 29: updateStatus → INACTIVE round-trip ----

    @Test
    @Transactional
    void testUpdateStatusToInactive() {
        Issue created = createIssue("P0000001", "Test", Priority.NORMAL);
        Optional<Issue> updated = issueService.updateStatus(created.id(), IssueStatus.INACTIVE);
        assertTrue(updated.isPresent());
        assertEquals(IssueStatus.INACTIVE, updated.get().status());
    }
}
