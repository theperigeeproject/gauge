package pl.perigee.gauge.domain;

import pl.perigee.gauge.config.StatusSeedStartup;
import pl.perigee.gauge.repository.AttitudeTypeRepository;
import pl.perigee.gauge.repository.IssueStatusRepository;
import pl.perigee.gauge.repository.ProgressLevelRepository;
import pl.perigee.gauge.repository.ProjectStatusRepository;
import pl.perigee.gauge.repository.TaskStatusRepository;
import pl.perigee.gauge.repository.TasklistStatusRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers increment 1 of 0000186F: the six tables exist, are seeded, and the seed is idempotent.
 *
 * <p>Runs in the default profile, which is {@code drop-and-create} in-memory — the seeder has
 * therefore just run once at startup, and no auth identity is involved, so no {@code @TestProfile}.
 */
@QuarkusTest
class StatusSeedTest {

    @Inject
    StatusSeedStartup seeder;

    @Inject
    ProjectStatusRepository projectStatusRepository;

    @Inject
    IssueStatusRepository issueStatusRepository;

    @Inject
    TasklistStatusRepository tasklistStatusRepository;

    @Inject
    TaskStatusRepository taskStatusRepository;

    @Inject
    ProgressLevelRepository progressLevelRepository;

    @Inject
    AttitudeTypeRepository attitudeTypeRepository;

    @Inject
    EntityManager em;

    // ── 1. Row counts ────────────────────────────────────────────────

    @Test
    void everyTableIsSeededWithTheExpectedNumberOfRows() {
        assertEquals(2, projectStatusRepository.count(), "project statuses");
        assertEquals(4, issueStatusRepository.count(), "issue statuses");
        assertEquals(4, tasklistStatusRepository.count(), "tasklist statuses");
        assertEquals(5, taskStatusRepository.count(), "task statuses");
        assertEquals(6, progressLevelRepository.count(), "progress levels");
        assertEquals(4, attitudeTypeRepository.count(), "attitudes");
    }

    // ── 2. Codes per level ───────────────────────────────────────────

    @Test
    void codesPerLevelMatchTheDocumentedSeeds() {
        assertEquals(Set.of("OPENED", "CLOSED"), codes(projectStatusRepository.listAll()));
        assertEquals(Set.of("TODO", "DOING", "DONE", "INACTIVE"), codes(issueStatusRepository.listAll()));
        assertEquals(Set.of("TODO", "DOING", "DONE", "CANCELED"), codes(tasklistStatusRepository.listAll()));
        assertEquals(Set.of("TODO", "DOING", "DONE", "FAILED", "CANCELED"),
            codes(taskStatusRepository.listAll()));
    }

    private static Set<String> codes(List<? extends StatusBase> statuses) {
        return statuses.stream().map(s -> s.code).collect(Collectors.toSet());
    }

    // ── 3. precedenceValue contiguous from 0, unique ─────────────────

    @Test
    void precedenceIsContiguousFromZeroAndUniquePerTable() {
        assertContiguous(projectStatusRepository.listAll());
        assertContiguous(issueStatusRepository.listAll());
        assertContiguous(tasklistStatusRepository.listAll());
        assertContiguous(taskStatusRepository.listAll());
    }

    private static void assertContiguous(List<? extends StatusBase> statuses) {
        List<Integer> values = statuses.stream().map(s -> s.precedenceValue).sorted().toList();
        for (int i = 0; i < values.size(); i++) {
            assertEquals(i, values.get(i), "precedenceValue must run 0..n-1 without gaps");
        }
        assertEquals(values.size(), new HashSet<>(values).size(), "precedenceValue must be unique");
    }

    // ── 4. Every status names an existing lookup row ─────────────────

    @Test
    void everyStatusReferencesAnExistingProgressLevelAndAttitude() {
        // No foreign key exists, so this is the only thing that catches a seed typo.
        Set<String> levels = progressLevelRepository.listAll().stream()
            .map(l -> l.name).collect(Collectors.toSet());
        Set<String> attitudes = attitudeTypeRepository.listAll().stream()
            .map(a -> a.name).collect(Collectors.toSet());

        List<StatusBase> allStatuses = new java.util.ArrayList<>();
        allStatuses.addAll(projectStatusRepository.listAll());
        allStatuses.addAll(issueStatusRepository.listAll());
        allStatuses.addAll(tasklistStatusRepository.listAll());
        allStatuses.addAll(taskStatusRepository.listAll());

        assertEquals(15, allStatuses.size(), "2 + 4 + 4 + 5 statuses seeded");
        for (StatusBase s : allStatuses) {
            assertTrue(levels.contains(s.progressLevel),
                s.code + " names unknown progress level '" + s.progressLevel + "'");
            assertTrue(attitudes.contains(s.attitudeType),
                s.code + " names unknown attitude '" + s.attitudeType + "'");
        }
    }

    // ── 5. Idempotency: a second run changes nothing, and never overwrites ──

    @Test
    @Transactional
    void reseedingInsertsNothingAndPreservesOperatorEdits() {
        IssueStatusDef doing = issueStatusRepository.findByCode("DOING").orElseThrow();
        doing.color = "#010203"; // pretend the operator recoloured it
        em.flush();

        long before = issueStatusRepository.count();

        seeder.seed();

        assertEquals(before, issueStatusRepository.count(), "a second run must insert nothing");

        IssueStatusDef after = issueStatusRepository.findByCode("DOING").orElseThrow();
        assertEquals("#010203", after.color,
            "the seeder must never update an existing row — the operator owns it after first boot");
        assertEquals(1, after.precedenceValue, "precedence must also survive a redeploy");
    }

    // ── 6. Lookup ordinals are the fixed values ──────────────────────

    @Test
    void lookupOrdinalsAreTheFixedValues() {
        Map<String, Integer> levels = progressLevelRepository.listAll().stream()
            .collect(Collectors.toMap(l -> l.name, l -> l.ordinal));
        assertEquals(Map.of("NASCENT", 0, "CREATED", 1, "ONGOING", 2,
            "HALTED", 3, "FINISHED", 4, "FINALIZED", 5), levels);

        Map<String, Integer> attitudes = attitudeTypeRepository.listAll().stream()
            .collect(Collectors.toMap(a -> a.name, a -> a.ordinal));
        assertEquals(Map.of("REGULAR", 0, "EXCLUDED", 1, "ARCHIVE", 2, "TRASH", 3), attitudes);
    }

    // ── 7. Internal int identity, not IdProvider hex-8 ───────────────

    @Test
    void statusIdsAreSmallInternalIntegersNotHex8Ids() {
        // The point is that two identity systems exist and do not mix (ADR 0020 §2, ADR 0005).
        // Deliberately NOT asserting a specific business id: IdProvider's counter is shared with
        // every other test in this suite, so that assertion would depend on execution order.
        for (IssueStatusDef s : issueStatusRepository.listAll()) {
            assertNotNull(s.id, "id is assigned by the database");
            assertTrue(s.id > 0, "auto-increment starts at 1");
            assertTrue(s.id < 100, "status ids are a tiny internal sequence");
            assertNotEquals(8, String.valueOf(s.id).length(),
                "status ids are not zero-padded hex-8 — they never reach the surface");
        }

        // And the business side is still hex-8, unaffected by the status rows.
        Issue issue = Issue.create("00000001", "t", "d", Priority.NORMAL);
        assertEquals(8, issue.id().length());
        assertTrue(issue.id().matches("[0-9A-F]{8}"));
    }

    // ── 8. Uniqueness is enforced by the schema ──────────────────────

    @Test
    @Transactional
    void duplicateCodeIsRejectedByTheSchema() {
        // IDENTITY generation means the INSERT runs at persist(), not at flush() — so the
        // constraint fires there and the assertion has to cover both calls.
        assertThrows(PersistenceException.class, () -> {
            em.persist(new IssueStatusDef("DOING", 99, "#ffffff", StatusSeedStartup.ONGOING,
                StatusSeedStartup.REGULAR));
            em.flush();
        }, "code is unique per status table — a duplicate must not be storable");
    }

    @Test
    @Transactional
    void duplicatePrecedenceIsRejectedByTheSchema() {
        assertThrows(PersistenceException.class, () -> {
            em.persist(new IssueStatusDef("UNIQUE_CODE", 1, "#ffffff", StatusSeedStartup.ONGOING,
                StatusSeedStartup.REGULAR));
            em.flush();
        }, "precedenceValue is unique per status table — ordering must be unambiguous");
    }
}
