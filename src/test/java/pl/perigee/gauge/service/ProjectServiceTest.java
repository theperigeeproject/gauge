package pl.perigee.gauge.service;

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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ProjectServiceTest {

    @Inject
    ProjectService projectService;

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
    }

    // ---- helpers ----

    private Project createProject(String name, String parentId) {
        return projectService.create(name, "desc", parentId);
    }

    private Issue createIssue(String projectId, String title) {
        Issue i = Issue.create(projectId, title, "desc", Priority.NORMAL);
        issueRepository.save(i);
        return i;
    }

    // ---- 0: create — name, description, parentId, ID assigned ----

    @Test
    @Transactional
    void testCreateProject() {
        Project p = projectService.create("Test Project", "Description", null);
        assertNotNull(p);
        assertNotNull(p.id());
        assertEquals("Test Project", p.name());
        assertEquals("Description", p.description());
        assertNull(p.parentId());
    }

    // ---- 1: create — root project (parentId=null) ----

    @Test
    @Transactional
    void testCreateRootProject() {
        Project p = createProject("Root", null);
        assertNull(p.parentId());
    }

    // ---- 2: findById — exists / empty ----

    @Test
    @Transactional
    void testFindById() {
        Project created = createProject("Find Me", null);
        Optional<Project> found = projectService.findById(created.id());
        assertTrue(found.isPresent());
        assertEquals("Find Me", found.get().name());
    }

    @Test
    @Transactional
    void testFindByIdNotFound() {
        Optional<Project> found = projectService.findById("NONEXISTENT");
        assertFalse(found.isPresent());
    }

    // ---- 3: findByParentId — children / empty for leaf ----

    @Test
    @Transactional
    void testFindByParentId() {
        Project parent = createProject("Parent", null);
        createProject("Child1", parent.id());
        createProject("Child2", parent.id());

        List<Project> children = projectService.findByParentId(parent.id());
        assertEquals(2, children.size());
    }

    @Test
    @Transactional
    void testFindByParentIdLeafReturnsEmpty() {
        Project leaf = createProject("Leaf", null);
        List<Project> children = projectService.findByParentId(leaf.id());
        assertTrue(children.isEmpty());
    }

    // ---- 4: findRootProjects — only null-parent projects ----

    @Test
    @Transactional
    void testFindRootProjects() {
        createProject("Root1", null);
        Project parent = createProject("Parent", null);
        createProject("Child", parent.id());

        List<Project> roots = projectService.findRootProjects();
        assertEquals(2, roots.size());
        assertTrue(roots.stream().allMatch(p -> p.parentId() == null));
    }

    // ---- 5: search — case-insensitive match on name and description ----

    @Test
    @Transactional
    void testSearch() {
        createProject("Searchable Name", null);
        Project other = createProject("Other", null);
        projectService.patch(other.id(), null, "Searchable Description", null);
        createProject("No Match", null);

        List<Project> results = projectService.search("searchable");
        assertEquals(2, results.size());
    }

    // ---- 6: getAncestors — chain from leaf to root ----

    @Test
    @Transactional
    void testGetAncestors() {
        Project root = createProject("Root", null);
        Project child = createProject("Child", root.id());
        Project grandchild = createProject("Grandchild", child.id());

        List<Project> ancestors = projectService.getAncestors(grandchild.id());
        assertEquals(3, ancestors.size());
        assertEquals(grandchild.id(), ancestors.get(0).id());
        assertEquals(child.id(), ancestors.get(1).id());
        assertEquals(root.id(), ancestors.get(2).id());
    }

    @Test
    @Transactional
    void testGetAncestorsRootHasItselfOnly() {
        Project root = createProject("Root", null);
        List<Project> ancestors = projectService.getAncestors(root.id());
        assertEquals(1, ancestors.size());
        assertEquals(root.id(), ancestors.get(0).id());
    }

    // ---- 7: patch — name only, description only, removalLock only, all, none ----

    @Test
    @Transactional
    void testPatchNameOnly() {
        Project created = createProject("Old", null);
        Optional<Project> patched = projectService.patch(created.id(), "New", null, null);
        assertTrue(patched.isPresent());
        assertEquals("New", patched.get().name());
        assertEquals("desc", patched.get().description());
    }

    @Test
    @Transactional
    void testPatchDescriptionOnly() {
        Project created = createProject("Name", null);
        Optional<Project> patched = projectService.patch(created.id(), null, "New Desc", null);
        assertTrue(patched.isPresent());
        assertEquals("Name", patched.get().name());
        assertEquals("New Desc", patched.get().description());
    }

    @Test
    @Transactional
    void testPatchRemovalLock() {
        Project created = createProject("Test", null);
        Optional<Project> patched = projectService.patch(created.id(), null, null, "locked");
        assertTrue(patched.isPresent());
        assertEquals("locked", patched.get().removalLock());
    }

    @Test
    @Transactional
    void testPatchAll() {
        Project created = createProject("Old", null);
        Optional<Project> patched = projectService.patch(created.id(), "New", "New Desc", "locked");
        assertTrue(patched.isPresent());
        assertEquals("New", patched.get().name());
        assertEquals("New Desc", patched.get().description());
        assertEquals("locked", patched.get().removalLock());
    }

    @Test
    @Transactional
    void testPatchNone() {
        Project created = createProject("Name", null);
        Optional<Project> patched = projectService.patch(created.id(), null, null, null);
        assertTrue(patched.isPresent());
        assertEquals("Name", patched.get().name());
        assertEquals("desc", patched.get().description());
    }

    // ---- 8: reparent — move to new parent, make root ----

    @Test
    @Transactional
    void testReparent() {
        Project parent = createProject("Parent", null);
        Project child = createProject("Child", null);

        Optional<Project> reparented = projectService.reparent(child.id(), parent.id());
        assertTrue(reparented.isPresent());
        assertEquals(parent.id(), reparented.get().parentId());
    }

    @Test
    @Transactional
    void testReparentToRoot() {
        Project p = projectService.create("Project", "desc", "P0000002");
        Optional<Project> reparented = projectService.reparent(p.id(), null);
        assertTrue(reparented.isPresent());
        assertNull(reparented.get().parentId());
    }

    // ---- 9: hasChildren — subprojects or issues ----

    @Test
    @Transactional
    void testHasChildrenWithSubprojects() {
        Project parent = createProject("Parent", null);
        assertFalse(projectService.hasChildren(parent.id()));

        createProject("Child", parent.id());
        assertTrue(projectService.hasChildren(parent.id()));
    }

    @Test
    @Transactional
    void testHasChildrenWithIssues() {
        Project p = createProject("Parent", null);
        assertFalse(projectService.hasChildren(p.id()));

        createIssue(p.id(), "Issue");
        assertTrue(projectService.hasChildren(p.id()));
    }

    @Test
    @Transactional
    void testHasChildrenEmpty() {
        Project p = createProject("Empty", null);
        assertFalse(projectService.hasChildren(p.id()));
    }

    // ---- 10: isLocked — true when removalLock='locked' ----

    @Test
    @Transactional
    void testIsLocked() {
        Project p = createProject("Locked", null);
        projectService.patch(p.id(), null, null, "locked");
        assertTrue(projectService.isLocked(p.id()));
    }

    @Test
    @Transactional
    void testIsLockedFalse() {
        Project p = createProject("Unlocked", null);
        assertFalse(projectService.isLocked(p.id()));
    }

    // ---- 11: deleteById — removes project ----

    @Test
    @Transactional
    void testDeleteById() {
        Project p = createProject("Delete", null);
        assertTrue(projectService.deleteById(p.id()));
        assertFalse(projectService.findById(p.id()).isPresent());
    }

    // ---- 12: deleteById — refuses locked project ----

    @Test
    @Transactional
    void testDeleteByIdRefusesLocked() {
        Project p = createProject("Locked", null);
        projectService.patch(p.id(), null, null, "locked");
        assertFalse(projectService.deleteById(p.id()));
        assertTrue(projectService.findById(p.id()).isPresent());
    }

    // ---- 13: cascadeDelete — removes project + subprojects + issues + tasklists + tasks ----

    @Test
    @Transactional
    void testCascadeDelete() {
        Project parent = createProject("Parent", null);
        Project child = createProject("Child", parent.id());

        Issue issue = createIssue(parent.id(), "Issue");
        Tasklist tl = new Tasklist("TL000001", issue.id(), "Tasklist", TasklistStatus.TODO, null);
        tasklistRepository.save(tl);
        Task t = new Task("T0000001", tl.id(), 0, "Task", "desc", TaskStatus.TODO);
        taskRepository.save(t);

        assertTrue(projectService.cascadeDelete(parent.id()));
        assertFalse(projectService.findById(parent.id()).isPresent());
        assertFalse(projectService.findById(child.id()).isPresent());
        assertFalse(issueRepository.findById(issue.id()).isPresent());
        assertFalse(tasklistRepository.findById(tl.id()).isPresent());
        assertFalse(taskRepository.findById(t.id()).isPresent());
    }

    // ---- 14: cascadeDelete — refuses if any descendant is locked ----

    @Test
    @Transactional
    void testCascadeDeleteWithLockedDescendant() {
        Project parent = createProject("Parent", null);
        Project child = createProject("Child", parent.id());
        projectService.patch(child.id(), null, null, "locked");

        assertFalse(projectService.cascadeDelete(parent.id()));
    }

    // ---- 15: cascadeDelete — refuses if project itself is locked ----

    @Test
    @Transactional
    void testCascadeDeleteLocked() {
        Project p = createProject("Locked", null);
        projectService.patch(p.id(), null, null, "locked");
        assertFalse(projectService.cascadeDelete(p.id()));
    }

    // ---- 16: patch nonexistent → empty ----

    @Test
    @Transactional
    void testPatchNotFound() {
        Optional<Project> patched = projectService.patch("NONEXISTENT", "Name", "Desc", null);
        assertFalse(patched.isPresent());
    }

    // ---- 17: reparent nonexistent → empty ----

    @Test
    @Transactional
    void testReparentNotFound() {
        Optional<Project> reparented = projectService.reparent("NONEXISTENT", "P0000001");
        assertFalse(reparented.isPresent());
    }

    // ---- 18: reparent — cycle detection (project cannot be its own descendant) ----

    @Test
    @Transactional
    void testReparentUnderOwnChildThrows() {
        Project parent = createProject("Parent", null);
        Project child = createProject("Child", parent.id());

        // Reparent parent under child — creates a cycle, must be rejected
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            projectService.reparent(parent.id(), child.id()));
        assertTrue(ex.getMessage().contains("cycle"));
    }

    @Test
    @Transactional
    void testReparentUnderOwnGrandchildThrows() {
        Project root = createProject("Root", null);
        Project child = createProject("Child", root.id());
        Project grandchild = createProject("Grandchild", child.id());

        // Reparent root under grandchild — deeper cycle
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            projectService.reparent(root.id(), grandchild.id()));
        assertTrue(ex.getMessage().contains("cycle"));
    }

    @Test
    @Transactional
    void testReparentToSelfThrows() {
        Project p = createProject("Self", null);

        // Reparent project to itself
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            projectService.reparent(p.id(), p.id()));
        assertTrue(ex.getMessage().contains("cycle"));
    }

    @Test
    @Transactional
    void testReparentToNullIsAlwaysValid() {
        Project child = createProject("Child", "P0000001");
        Optional<Project> reparented = projectService.reparent(child.id(), null);
        assertTrue(reparented.isPresent());
        assertNull(reparented.get().parentId());
    }

    // ---- 19: getAncestors nonexistent → empty list ----

    @Test
    @Transactional
    void testGetAncestorsNotFound() {
        List<Project> ancestors = projectService.getAncestors("NONEXISTENT");
        assertTrue(ancestors.isEmpty());
    }

    // ---- 20: search — null name/description don't cause NPE ----
    // name is non-nullable in DB, but description can be null

    @Test
    @Transactional
    void testSearchNullDescriptionNoNpe() {
        Project p = new Project("P0000001", "Match", null, null, "");
        projectRepository.save(p);

        List<Project> results = projectService.search("match");
        assertEquals(1, results.size());
    }

    // ---- 21: cascadeDelete nonexistent → false ----

    @Test
    @Transactional
    void testCascadeDeleteNotFound() {
        assertFalse(projectService.cascadeDelete("NONEXISTENT"));
    }

    // ---- 22: deleteById nonexistent → false ----

    @Test
    @Transactional
    void testDeleteByIdNotFound() {
        assertFalse(projectService.deleteById("NONEXISTENT"));
    }

    // ---- 23: isLocked nonexistent → false ----

    @Test
    @Transactional
    void testIsLockedNotFound() {
        assertFalse(projectService.isLocked("NONEXISTENT"));
    }

    // ---- 24: hasChildren nonexistent → false ----

    @Test
    @Transactional
    void testHasChildrenNotFound() {
        assertFalse(projectService.hasChildren("NONEXISTENT"));
    }

    // ---- 25: getAncestors — dangling parent → partial chain ----

    @Test
    @Transactional
    void testGetAncestorsDanglingParent() {
        Project p = new Project("P0000001", "Orphan", "desc", "NONEXISTENT_PARENT", "");
        projectRepository.save(p);

        List<Project> ancestors = projectService.getAncestors("P0000001");
        assertEquals(1, ancestors.size());
    }

    // ---- 26: reparent to nonexistent parentId → succeeds ----

    @Test
    @Transactional
    void testReparentToNonexistentParent() {
        Project p = createProject("Project", null);
        Optional<Project> reparented = projectService.reparent(p.id(), "NONEXISTENT");
        assertTrue(reparented.isPresent());
        assertEquals("NONEXISTENT", reparented.get().parentId());
    }

    // ---- 27: deleteById on project with children → succeeds but orphans subprojects ----

    @Test
    @Transactional
    void testDeleteByIdOrphansSubprojects() {
        Project parent = createProject("Parent", null);
        Project child = createProject("Child", parent.id());

        assertTrue(projectService.deleteById(parent.id()));
        Optional<Project> orphaned = projectService.findById(child.id());
        assertTrue(orphaned.isPresent());
        assertEquals(parent.id(), orphaned.get().parentId());
    }

    // ---- 28: create — null name → DB constraint violation ----

    @Test
    @Transactional
    void testCreateWithNullName() {
        assertThrows(Exception.class, () -> {
            Project p = projectService.create(null, "Desc", null);
            projectRepository.save(p);
            em.flush();
        });
    }

    // ---- 29: findByIds — duplicate IDs in input list ----

    @Test
    @Transactional
    void testFindByIdsWithDuplicates() {
        Project p1 = createProject("P1", null);
        List<Project> found = projectService.findByIds(List.of(p1.id(), p1.id(), p1.id()));
        assertEquals(1, found.size());
    }

    @Test
    @Transactional
    void testFindByIds() {
        Project p1 = createProject("P1", null);
        Project p2 = createProject("P2", null);
        createProject("P3", null);

        List<Project> found = projectService.findByIds(List.of(p1.id(), p2.id()));
        assertEquals(2, found.size());
    }

    @Test
    @Transactional
    void testFindByIdsEmptyList() {
        List<Project> found = projectService.findByIds(List.of());
        assertTrue(found.isEmpty());
    }
}
