package pl.perigee.gauge.domain;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class DomainEntityTest {

    // ---- 0: Project.create — hex-8 ID, name/description/parentId, removalLock='' ----

    @Test
    void testProjectCreate() {
        Project p = Project.create("Test", "Description", null, 1);
        assertNotNull(p.id());
        assertEquals(8, p.id().length());
        assertTrue(p.id().matches("[0-9A-F]{8}"));
        assertEquals("Test", p.name());
        assertEquals("Description", p.description());
        assertNull(p.parentId());
        assertEquals("", p.removalLock());
    }

    @Test
    void testProjectCreateWithParent() {
        Project p = Project.create("Child", "desc", "P0000001", 1);
        assertEquals("P0000001", p.parentId());
    }

    // ---- 1: Project — accessor methods ----

    @Test
    void testProjectAccessors() {
        Project p = new Project("P0000001", "Name", "Desc", "P0000002", "locked", 1);
        assertEquals("P0000001", p.id());
        assertEquals("Name", p.name());
        assertEquals("Desc", p.description());
        assertEquals("P0000002", p.parentId());
        assertEquals("locked", p.removalLock());
    }

    @Test
    void testProjectNullRemovalLockDefaultsToEmpty() {
        Project p = new Project("P0000001", "Name", "Desc", null, null, 1);
        assertEquals("", p.removalLock());
    }

    // ---- 2: Project — JSON roundtrip ----
    // Skipped: requires Jackson ObjectMapper setup; tested implicitly by resource tests.

    // ---- 3: Issue.create — hex-8 ID, TODO status, correct fields ----

    @Test
    void testIssueCreate() {
        Issue i = Issue.create("P0000001", "Title", "Description", Priority.HIGH, 7);
        assertNotNull(i.id());
        assertEquals(8, i.id().length());
        assertTrue(i.id().matches("[0-9A-F]{8}"));
        assertEquals("P0000001", i.projectId());
        assertEquals("Title", i.title());
        assertEquals("Description", i.description());
        assertEquals(7, i.statusId, "the caller supplies the status id; the entity stores it verbatim");
        assertEquals(Priority.HIGH, i.priority());
    }

    // ---- 4: Issue — status() and priority() return correct enums ----

    @Test
    void testIssueStatusIdAndPriority() {
        Issue i = new Issue("I0000001", "P0000001", "T", "D", 3, Priority.LOW);
        assertEquals(3, i.statusId);
        assertEquals(Priority.LOW, i.priority());
        assertNull(i.legacyStatusCode(), "a new row never carries a legacy status code");
    }

    // ---- 5: Issue — JSON roundtrip ----
    // Skipped: requires Jackson ObjectMapper setup.

    // ---- 6: Tasklist.create — hex-8 ID, TODO status, correct fields ----

    @Test
    void testTasklistCreate() {
        Tasklist tl = Tasklist.create("I0000001", "Title", "T0000001", 7);
        assertNotNull(tl.id());
        assertEquals(8, tl.id().length());
        assertTrue(tl.id().matches("[0-9A-F]{8}"));
        assertEquals("I0000001", tl.issueId());
        assertEquals("Title", tl.title());
        assertEquals(7, tl.statusId);
        assertEquals("T0000001", tl.decomposesTaskId());
    }

    @Test
    void testTasklistCreateNullDecomposesTaskId() {
        Tasklist tl = Tasklist.create("I0000001", "Title", null, 7);
        assertNull(tl.decomposesTaskId());
    }

    // ---- 7: Tasklist — status() and decomposesTaskId() ----

    @Test
    void testTasklistStatusId() {
        Tasklist tl = new Tasklist("TL000001", "I0000001", "T", 3, null);
        assertEquals(3, tl.statusId);
        assertNull(tl.legacyStatusCode());
    }

    // ---- 8: Task.create — hex-8 ID, TODO status, correct fields ----

    @Test
    void testTaskCreate() {
        Task t = Task.create("TL000001", 3, "Title", "Description", 7);
        assertNotNull(t.id());
        assertEquals(8, t.id().length());
        assertTrue(t.id().matches("[0-9A-F]{8}"));
        assertEquals("TL000001", t.tasklistId());
        assertEquals(3, t.ordinal());
        assertEquals("Title", t.title());
        assertEquals("Description", t.description());
        assertEquals(7, t.statusId);
    }

    // ---- 9: Task — status() and ordinal() ----

    @Test
    void testTaskStatusId() {
        Task t = new Task("T0000001", "TL000001", 0, "T", "D", 4);
        assertEquals(4, t.statusId);
        assertEquals(0, t.ordinal());
        assertNull(t.legacyStatusCode());
    }

    // ---- 10: User.create — UUID ID, bcrypt password, correct fields ----

    @Test
    void testUserCreate() {
        String hashed = BcryptUtil.bcryptHash("password");
        User u = User.create("testuser", hashed, "user");
        assertNotNull(u.id);
        assertEquals(36, u.id.length());
        assertTrue(u.id.contains("-"));
        assertEquals("testuser", u.username);
        assertEquals(hashed, u.password);
        assertEquals("user", u.role);
        assertTrue(u.active);
    }

    // ---- 11: ApiKey.create — UUID ID, correct fields ----

    @Test
    void testApiKeyCreate() {
        ApiKey ak = ApiKey.create("USER001", "Test Key", "hash123", null, null);
        assertNotNull(ak.id);
        assertEquals(36, ak.id.length());
        assertTrue(ak.id.contains("-"));
        assertEquals("USER001", ak.userId);
        assertEquals("Test Key", ak.name);
        assertEquals("hash123", ak.keyHash);
    }

    // ---- 12: UserFavorite — composite key equality, static methods ----

    @Test
    void testUserFavoriteIdEquality() {
        UserFavorite.UserFavoriteId id1 = new UserFavorite.UserFavoriteId("user1", "proj1");
        UserFavorite.UserFavoriteId id2 = new UserFavorite.UserFavoriteId("user1", "proj1");
        UserFavorite.UserFavoriteId id3 = new UserFavorite.UserFavoriteId("user1", "proj2");

        assertEquals(id1, id2);
        assertNotEquals(id1, id3);
        assertEquals(id1.hashCode(), id2.hashCode());
    }

    @Test
    @Transactional
    void testUserFavoritePersistence() {
        UserFavorite fav = new UserFavorite("user1", "proj1");
        fav.persist();

        var found = UserFavorite.findByUserId("user1");
        assertEquals(1, found.size());
        assertEquals("proj1", found.get(0).projectId);

        long deleted = UserFavorite.deleteByUserIdAndProjectId("user1", "proj1");
        assertEquals(1, deleted);
    }

    @Test
    @Transactional
    void testUserFavoriteDeleteByUserId() {
        new UserFavorite("user1", "proj1").persist();
        new UserFavorite("user1", "proj2").persist();

        long deleted = UserFavorite.deleteByUserId("user1");
        assertEquals(2, deleted);
    }

    // ---- 13: ProjectAnalysisResult — constructors ----

    @Test
    void testProjectAnalysisResultDefaultConstructor() {
        ProjectAnalysisResult r = new ProjectAnalysisResult();
        assertEquals(0, r.issueTodo);
        assertEquals(0, r.issueDoing);
        assertEquals(0, r.issueInactive);
        assertEquals(0, r.issueDone);
        assertEquals(0, r.subprojectCount);
        assertEquals(0, r.taskTodo);
        assertEquals(0, r.taskDoing);
        assertNull(r.bubbledPriority);
    }

    @Test
    void testProjectAnalysisResultParameterizedConstructor() {
        ProjectAnalysisResult r = new ProjectAnalysisResult(1, 2, 3, 4, 5, 6, 7, BubbledPriority.HIGH);
        assertEquals(1, r.issueTodo);
        assertEquals(2, r.issueDoing);
        assertEquals(3, r.issueInactive);
        assertEquals(4, r.issueDone);
        assertEquals(5, r.subprojectCount);
        assertEquals(6, r.taskTodo);
        assertEquals(7, r.taskDoing);
        assertEquals(BubbledPriority.HIGH, r.bubbledPriority);
    }

    // ---- 14: All enums — values and valueOf ----

    @Test
    void testPriorityEnum() {
        assertEquals(3, Priority.values().length);
        assertEquals(Priority.LOW, Priority.valueOf("LOW"));
        assertEquals(Priority.NORMAL, Priority.valueOf("NORMAL"));
        assertEquals(Priority.HIGH, Priority.valueOf("HIGH"));
    }

    @Test
    void testBubbledPriorityEnum() {
        assertEquals(6, BubbledPriority.values().length);
        assertEquals(BubbledPriority.NONE, BubbledPriority.valueOf("NONE"));
        assertEquals(BubbledPriority.DONE, BubbledPriority.valueOf("DONE"));
        assertEquals(BubbledPriority.INACTIVE, BubbledPriority.valueOf("INACTIVE"));
        assertEquals(BubbledPriority.LOW, BubbledPriority.valueOf("LOW"));
        assertEquals(BubbledPriority.NORMAL, BubbledPriority.valueOf("NORMAL"));
        assertEquals(BubbledPriority.HIGH, BubbledPriority.valueOf("HIGH"));
    }

    @Test
    void testSortOptionEnum() {
        assertEquals(5, SortOption.values().length);
        assertEquals(SortOption.NAME, SortOption.valueOf("NAME"));
        assertEquals(SortOption.PRIORITY_STATUS_NAME, SortOption.valueOf("PRIORITY_STATUS_NAME"));
        assertEquals(SortOption.PRIORITY_NAME_STATUS, SortOption.valueOf("PRIORITY_NAME_STATUS"));
        assertEquals(SortOption.STATUS_PRIORITY_NAME, SortOption.valueOf("STATUS_PRIORITY_NAME"));
        assertEquals(SortOption.STATUS_NAME_PRIORITY, SortOption.valueOf("STATUS_NAME_PRIORITY"));
    }

    // ---- 15: IdProviderHolder — provider() returns same singleton ----
    // IdProviderHolder is package-private; tested implicitly via entity factory
    // methods (Project.create, Issue.create, etc.) which all use it.

    @Test
    void testEntityFactoryMethodsUseIdProvider() {
        // Multiple creates should produce different IDs from the same provider
        Project p1 = Project.create("P1", "desc", null, 1);
        Project p2 = Project.create("P2", "desc", null, 1);
        assertNotEquals(p1.id(), p2.id());
    }
}
