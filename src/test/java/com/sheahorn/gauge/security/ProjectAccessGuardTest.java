package pl.perigee.gauge.security;

import pl.perigee.gauge.domain.*;
import pl.perigee.gauge.repository.IssueRepository;
import pl.perigee.gauge.repository.ProjectRepository;
import pl.perigee.gauge.repository.TasklistRepository;
import pl.perigee.gauge.repository.TaskRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ProjectAccessGuardTest {

    @Inject
    ProjectAccessGuard accessGuard;

    @Inject
    CurrentApiKey currentApiKey;

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

    @BeforeEach
    @Transactional
    void cleanUp() {
        em.createNativeQuery("DELETE FROM tasks").executeUpdate();
        em.createNativeQuery("DELETE FROM tasklists").executeUpdate();
        em.createNativeQuery("DELETE FROM issues").executeUpdate();
        em.createNativeQuery("DELETE FROM projects").executeUpdate();
        currentApiKey.set(null);
    }

    // ---- helpers ----

    private Project createProject(String id, String name, String parentId) {
        Project p = new Project(id, name, "desc", parentId, "");
        projectRepository.save(p);
        return p;
    }

    private Issue createIssue(String id, String projectId) {
        Issue i = new Issue(id, projectId, "Issue " + id, "desc", IssueStatus.TODO, Priority.NORMAL);
        issueRepository.save(i);
        return i;
    }

    private Tasklist createTasklist(String id, String issueId) {
        Tasklist tl = new Tasklist(id, issueId, "TL " + id, TasklistStatus.TODO, null);
        tasklistRepository.save(tl);
        return tl;
    }

    private Task createTask(String id, String tasklistId) {
        Task t = new Task(id, tasklistId, 0, "Task " + id, "desc", TaskStatus.TODO);
        taskRepository.save(t);
        return t;
    }

    private void setKey(String restrictedProjectId, String accessLevel) {
        currentApiKey.set(new ApiKey("user1", "testuser", "user", restrictedProjectId, accessLevel));
    }

    // ---- canAccessProject ----

    @Test
    @Transactional
    void testCanAccessProjectNullKey() {
        currentApiKey.set(null);
        Project root = createProject("ROOT0001", "Root", null);
        assertTrue(accessGuard.canAccessProject(root.id()));
    }

    @Test
    @Transactional
    void testCanAccessProjectUnrestrictedKey() {
        setKey(null, null);
        Project root = createProject("ROOT0001", "Root", null);
        assertTrue(accessGuard.canAccessProject(root.id()));
    }

    @Test
    @Transactional
    void testCanAccessProjectRestrictedExactMatch() {
        setKey("PROJ0001", "READ");
        Project p = createProject("PROJ0001", "Target", null);
        assertTrue(accessGuard.canAccessProject(p.id()));
    }

    @Test
    @Transactional
    void testCanAccessProjectRestrictedDisallowed() {
        setKey("PROJ0001", "READ");
        Project p = createProject("PROJ0002", "Other", null);
        assertFalse(accessGuard.canAccessProject(p.id()));
    }

    @Test
    @Transactional
    void testCanAccessProjectRestrictedDirectChild() {
        setKey("PROJ0001", "READ");
        Project parent = createProject("PROJ0001", "Parent", null);
        Project child = createProject("CHILD001", "Child", parent.id());
        assertTrue(accessGuard.canAccessProject(child.id()));
    }

    @Test
    @Transactional
    void testCanAccessProjectRestrictedGrandchild() {
        setKey("PROJ0001", "READ");
        Project root = createProject("PROJ0001", "Root", null);
        Project child = createProject("CHILD001", "Child", root.id());
        Project grandchild = createProject("GRAND001", "Grandchild", child.id());
        assertTrue(accessGuard.canAccessProject(grandchild.id()));
    }

    @Test
    @Transactional
    void testCanAccessProjectRestrictedChildOfOtherRoot() {
        setKey("PROJ0001", "READ");
        Project otherRoot = createProject("PROJ0002", "Other Root", null);
        Project child = createProject("CHILD001", "Child", otherRoot.id());
        assertFalse(accessGuard.canAccessProject(child.id()));
    }

    @Test
    @Transactional
    void testCanAccessProjectRestrictedSiblingNotAllowed() {
        // Key restricted to PROJ0001 — a sibling under the same parent is NOT allowed
        setKey("PROJ0001", "READ");
        Project parent = createProject("ROOT0001", "Root", null);
        createProject("PROJ0001", "Target", parent.id());
        Project sibling = createProject("PROJ0002", "Sibling", parent.id());
        assertFalse(accessGuard.canAccessProject(sibling.id()));
    }

    // ---- canWriteProject ----

    @Test
    @Transactional
    void testCanWriteProjectNullKey() {
        currentApiKey.set(null);
        Project p = createProject("PROJ0001", "P", null);
        assertTrue(accessGuard.canWriteProject(p.id()));
    }

    @Test
    @Transactional
    void testCanWriteProjectUnrestrictedKey() {
        setKey(null, null);
        Project p = createProject("PROJ0001", "P", null);
        assertTrue(accessGuard.canWriteProject(p.id()));
    }

    @Test
    @Transactional
    void testCanWriteProjectReadKeyDenied() {
        setKey("PROJ0001", "READ");
        Project p = createProject("PROJ0001", "P", null);
        assertFalse(accessGuard.canWriteProject(p.id()));
    }

    @Test
    @Transactional
    void testCanWriteProjectWriteKeyAllowed() {
        setKey("PROJ0001", "WRITE");
        Project p = createProject("PROJ0001", "P", null);
        assertTrue(accessGuard.canWriteProject(p.id()));
    }

    @Test
    @Transactional
    void testCanWriteProjectWriteKeyDisallowedProject() {
        setKey("PROJ0001", "WRITE");
        Project p = createProject("PROJ0002", "P", null);
        assertFalse(accessGuard.canWriteProject(p.id()));
    }

    // ---- canAccessIssue ----

    @Test
    @Transactional
    void testCanAccessIssueNullKey() {
        currentApiKey.set(null);
        Project root = createProject("PROJ0001", "Root", null);
        Issue issue = createIssue("ISS00001", root.id());
        assertTrue(accessGuard.canAccessIssue(issue.id()));
    }

    @Test
    @Transactional
    void testCanAccessIssueRestrictedAllowed() {
        setKey("PROJ0001", "READ");
        Project root = createProject("PROJ0001", "Root", null);
        Issue issue = createIssue("ISS00001", root.id());
        assertTrue(accessGuard.canAccessIssue(issue.id()));
    }

    @Test
    @Transactional
    void testCanAccessIssueRestrictedDisallowed() {
        setKey("PROJ0001", "READ");
        Project root = createProject("PROJ0002", "Other", null);
        Issue issue = createIssue("ISS00001", root.id());
        assertFalse(accessGuard.canAccessIssue(issue.id()));
    }

    @Test
    @Transactional
    void testCanAccessIssueNonexistent() {
        setKey("PROJ0001", "READ");
        assertFalse(accessGuard.canAccessIssue("NONEXISTENT"));
    }

    // ---- canWriteIssue ----

    @Test
    @Transactional
    void testCanWriteIssueReadKeyDenied() {
        setKey("PROJ0001", "READ");
        Project root = createProject("PROJ0001", "Root", null);
        Issue issue = createIssue("ISS00001", root.id());
        assertFalse(accessGuard.canWriteIssue(issue.id()));
    }

    @Test
    @Transactional
    void testCanWriteIssueWriteKeyAllowed() {
        setKey("PROJ0001", "WRITE");
        Project root = createProject("PROJ0001", "Root", null);
        Issue issue = createIssue("ISS00001", root.id());
        assertTrue(accessGuard.canWriteIssue(issue.id()));
    }

    // ---- canAccessTasklist ----

    @Test
    @Transactional
    void testCanAccessTasklistNullKey() {
        currentApiKey.set(null);
        Project root = createProject("PROJ0001", "Root", null);
        Issue issue = createIssue("ISS00001", root.id());
        Tasklist tl = createTasklist("TL000001", issue.id());
        assertTrue(accessGuard.canAccessTasklist(tl.id()));
    }

    @Test
    @Transactional
    void testCanAccessTasklistRestrictedAllowed() {
        setKey("PROJ0001", "READ");
        Project root = createProject("PROJ0001", "Root", null);
        Issue issue = createIssue("ISS00001", root.id());
        Tasklist tl = createTasklist("TL000001", issue.id());
        assertTrue(accessGuard.canAccessTasklist(tl.id()));
    }

    @Test
    @Transactional
    void testCanAccessTasklistRestrictedDisallowed() {
        setKey("PROJ0001", "READ");
        Project root = createProject("PROJ0002", "Other", null);
        Issue issue = createIssue("ISS00001", root.id());
        Tasklist tl = createTasklist("TL000001", issue.id());
        assertFalse(accessGuard.canAccessTasklist(tl.id()));
    }

    // ---- canWriteTasklist ----

    @Test
    @Transactional
    void testCanWriteTasklistReadKeyDenied() {
        setKey("PROJ0001", "READ");
        Project root = createProject("PROJ0001", "Root", null);
        Issue issue = createIssue("ISS00001", root.id());
        Tasklist tl = createTasklist("TL000001", issue.id());
        assertFalse(accessGuard.canWriteTasklist(tl.id()));
    }

    @Test
    @Transactional
    void testCanWriteTasklistWriteKeyAllowed() {
        setKey("PROJ0001", "WRITE");
        Project root = createProject("PROJ0001", "Root", null);
        Issue issue = createIssue("ISS00001", root.id());
        Tasklist tl = createTasklist("TL000001", issue.id());
        assertTrue(accessGuard.canWriteTasklist(tl.id()));
    }

    // ---- canAccessTask ----

    @Test
    @Transactional
    void testCanAccessTaskNullKey() {
        currentApiKey.set(null);
        Project root = createProject("PROJ0001", "Root", null);
        Issue issue = createIssue("ISS00001", root.id());
        Tasklist tl = createTasklist("TL000001", issue.id());
        Task t = createTask("T0000001", tl.id());
        assertTrue(accessGuard.canAccessTask(t.id()));
    }

    @Test
    @Transactional
    void testCanAccessTaskRestrictedAllowed() {
        setKey("PROJ0001", "READ");
        Project root = createProject("PROJ0001", "Root", null);
        Issue issue = createIssue("ISS00001", root.id());
        Tasklist tl = createTasklist("TL000001", issue.id());
        Task t = createTask("T0000001", tl.id());
        assertTrue(accessGuard.canAccessTask(t.id()));
    }

    @Test
    @Transactional
    void testCanAccessTaskRestrictedDisallowed() {
        setKey("PROJ0001", "READ");
        Project root = createProject("PROJ0002", "Other", null);
        Issue issue = createIssue("ISS00001", root.id());
        Tasklist tl = createTasklist("TL000001", issue.id());
        Task t = createTask("T0000001", tl.id());
        assertFalse(accessGuard.canAccessTask(t.id()));
    }

    // ---- canWriteTask ----

    @Test
    @Transactional
    void testCanWriteTaskReadKeyDenied() {
        setKey("PROJ0001", "READ");
        Project root = createProject("PROJ0001", "Root", null);
        Issue issue = createIssue("ISS00001", root.id());
        Tasklist tl = createTasklist("TL000001", issue.id());
        Task t = createTask("T0000001", tl.id());
        assertFalse(accessGuard.canWriteTask(t.id()));
    }

    @Test
    @Transactional
    void testCanWriteTaskWriteKeyAllowed() {
        setKey("PROJ0001", "WRITE");
        Project root = createProject("PROJ0001", "Root", null);
        Issue issue = createIssue("ISS00001", root.id());
        Tasklist tl = createTasklist("TL000001", issue.id());
        Task t = createTask("T0000001", tl.id());
        assertTrue(accessGuard.canWriteTask(t.id()));
    }

    // ---- deep chain ----

    @Test
    @Transactional
    void testCanAccessTaskDeepChainAllowed() {
        setKey("PROJ0001", "READ");
        Project root = createProject("PROJ0001", "Root", null);
        Project child = createProject("CHILD001", "Child", root.id());
        Issue issue = createIssue("ISS00001", child.id());
        Tasklist tl = createTasklist("TL000001", issue.id());
        Task t = createTask("T0000001", tl.id());
        assertTrue(accessGuard.canAccessTask(t.id()));
    }

    @Test
    @Transactional
    void testCanAccessTaskDeepChainDisallowed() {
        setKey("PROJ0001", "READ");
        Project otherRoot = createProject("PROJ0002", "Other Root", null);
        Project child = createProject("CHILD001", "Child", otherRoot.id());
        Issue issue = createIssue("ISS00001", child.id());
        Tasklist tl = createTasklist("TL000001", issue.id());
        Task t = createTask("T0000001", tl.id());
        assertFalse(accessGuard.canAccessTask(t.id()));
    }
}
