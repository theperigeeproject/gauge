package pl.perigee.gauge.service;

import pl.perigee.gauge.domain.ConflictException;
import pl.perigee.gauge.domain.Project;
import pl.perigee.gauge.domain.StatusBase;
import pl.perigee.gauge.domain.StatusLevel;
import pl.perigee.gauge.domain.ValidationException;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Projects reference a status by <b>id</b> and have no legacy code column, so the reference scanner
 * must count them by id. It returned zero for {@link StatusLevel#PROJECT} for as long as projects
 * had no status column at all — correct then, corrupting now: every project status would look
 * unused, deletion would proceed, and every project pointing at it would resolve to a dangling id.
 */
@QuarkusTest
class ProjectStatusReferenceTest {

    @Inject StatusService statusService;
    @Inject StatusReferenceScanner scanner;
    @Inject EntityManager em;

    private StatusBase opened() {
        return statusService.find(StatusLevel.PROJECT, "OPENED").orElseThrow();
    }

    private StatusBase closed() {
        return statusService.find(StatusLevel.PROJECT, "CLOSED").orElseThrow();
    }

    // ── The scan actually sees projects ─────────────────────────────

    @Test
    @TestTransaction
    void projectsAreCountedByStatusId() {
        assertEquals(0, scanner.count(StatusLevel.PROJECT, opened()),
            "a fresh database has no projects, so nothing references OPENED");

        persistProject("PROJREF1", opened().id);
        em.flush();

        assertEquals(1, scanner.count(StatusLevel.PROJECT, opened()));
        assertEquals(0, scanner.count(StatusLevel.PROJECT, closed()));
    }

    // ── Consequence 1: an in-use project status is a 409, not a silent delete ──

    @Test
    @TestTransaction
    void anInUseProjectStatusIsRefusedWithoutAPolicy() {
        persistProject("PROJREF2", opened().id);
        em.flush();

        ConflictException e = assertThrows(ConflictException.class,
            () -> statusService.delete(StatusLevel.PROJECT, "OPENED", null, null));

        assertEquals("STATUS_IN_USE", e.error());
        assertTrue(e.getMessage().contains("migrate"),
            "the message must name a policy that works here: " + e.getMessage());
        assertFalse(e.getMessage().contains("remove"),
            "policy=remove can never succeed for a project status, so it must not be advertised: "
                + e.getMessage());
        assertTrue(statusService.find(StatusLevel.PROJECT, "OPENED").isPresent(),
            "nothing may be deleted when the request is refused");
    }

    @Test
    @TestTransaction
    void preventPolicyRefusesAnInUseProjectStatus() {
        persistProject("PROJREF3", closed().id);
        em.flush();

        assertEquals("STATUS_IN_USE",
            assertThrows(ConflictException.class, () -> statusService.delete(
                StatusLevel.PROJECT, "CLOSED", StatusService.RemovalPolicy.PREVENT, null)).error());
    }

    // ── Consequence 2: remove is refused, because it would bypass removalLock ──

    @Test
    @TestTransaction
    void removePolicyIsRefusedForProjectsEvenWhenTheScanFoundThem() {
        // A referencing project on purpose: with zero references the policy is never consulted and
        // the delete simply proceeds, which is correct. The refusal is about what deleting a
        // project row would mean, and it is reached exactly when there is something to delete.
        persistProject("PROJREF3", closed().id);
        em.flush();

        ValidationException e = assertThrows(ValidationException.class, () -> statusService.delete(
            StatusLevel.PROJECT, "CLOSED", StatusService.RemovalPolicy.REMOVE, null));

        assertEquals("PROJECT_REMOVAL_REQUIRES_MIGRATE", e.error());
        assertTrue(e.getMessage().contains("removalLock"),
            "the refusal must explain the actual hazard: " + e.getMessage());
        assertTrue(statusService.find(StatusLevel.PROJECT, "CLOSED").isPresent());
        em.flush();
        em.clear();
        assertNotNull(Project.<Project>findById("PROJREF3"),
            "the project must survive a refused removal");
    }

    // ── Consequence 3: migrate works, by id, and leaves no dangling referent ──

    @Test
    @TestTransaction
    void migratePolicyRewritesProjectsByStatusIdThenDeletesTheStatus() {
        persistProject("PROJREF4", closed().id);
        Integer openedId = opened().id;
        em.flush();

        statusService.delete(StatusLevel.PROJECT, "CLOSED",
            StatusService.RemovalPolicy.MIGRATE, "OPENED");

        assertTrue(statusService.find(StatusLevel.PROJECT, "CLOSED").isEmpty());
        em.flush();
        em.clear();
        assertEquals(openedId, Project.<Project>findById("PROJREF4").statusId,
            "the project must now point at the surviving status, not at a deleted row");
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private void persistProject(String id, Integer statusId) {
        Project project = new Project(id, "ref test", "d", null, "");
        project.statusId = statusId;
        em.persist(project);
    }
}
