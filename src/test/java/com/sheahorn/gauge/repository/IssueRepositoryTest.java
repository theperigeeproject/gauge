package pl.perigee.gauge.repository;

import pl.perigee.gauge.domain.Issue;
import pl.perigee.gauge.domain.IssueStatus;
import pl.perigee.gauge.domain.Priority;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class IssueRepositoryTest {

    @Inject
    IssueRepository repository;

    @BeforeEach
    @Transactional
    void cleanUp() {
        repository.deleteById("I0000001");
        repository.deleteById("I0000002");
        repository.deleteById("I0000003");
    }

    @Test
    @Transactional
    void testSaveNewIssue() {
        Issue i = new Issue("I0000001", "P0000001", "Test", "Desc", IssueStatus.TODO, Priority.NORMAL);
        Issue saved = repository.save(i);
        assertNotNull(saved);
        assertEquals("I0000001", saved.id);
    }

    @Test
    @Transactional
    void testSaveUpdatesExisting() {
        Issue i = new Issue("I0000001", "P0000001", "Original", "Desc", IssueStatus.TODO, Priority.LOW);
        repository.save(i);

        Issue updated = new Issue("I0000001", "P0000002", "Updated", "New Desc", IssueStatus.DOING, Priority.HIGH);
        Issue saved = repository.save(updated);

        assertEquals("Updated", saved.title);
        assertEquals("New Desc", saved.description);
        assertEquals("P0000002", saved.projectId);
        assertEquals(IssueStatus.DOING.name(), saved.status);
        assertEquals(Priority.HIGH.name(), saved.priority);
    }

    @Test
    @Transactional
    void testSaveUpdateWithNullTitle() {
        Issue i = new Issue("I0000001", "P0000001", "Original", "Desc", IssueStatus.TODO, Priority.NORMAL);
        repository.save(i);

        Issue updated = new Issue("I0000001", "P0000001", null, "Desc", IssueStatus.TODO, Priority.NORMAL);
        // Repository sets title=null on the managed entity. Hibernate validates
        // at flush time (transaction commit), not at persist time.
        repository.save(updated);

        // Flush triggers PropertyValueException for not-null column
        assertThrows(Exception.class, () -> {
            io.quarkus.hibernate.orm.panache.Panache.getEntityManager().flush();
        });
    }

    @Test
    @Transactional
    void testFindById() {
        Issue i = new Issue("I0000001", "P0000001", "Test", "Desc", IssueStatus.TODO, Priority.NORMAL);
        repository.save(i);

        Optional<Issue> found = repository.findById("I0000001");
        assertTrue(found.isPresent());
        assertEquals("Test", found.get().title());
    }

    @Test
    @Transactional
    void testFindByIdNotFound() {
        Optional<Issue> found = repository.findById("NONEXISTENT");
        assertFalse(found.isPresent());
    }

    @Test
    @Transactional
    void testFindAll() {
        repository.save(new Issue("I0000001", "P0000001", "I1", "Desc", IssueStatus.TODO, Priority.NORMAL));
        repository.save(new Issue("I0000002", "P0000001", "I2", "Desc", IssueStatus.TODO, Priority.LOW));

        List<Issue> all = repository.findAll();
        assertTrue(all.size() >= 2);
    }

    @Test
    @Transactional
    void testFindByProjectId() {
        repository.save(new Issue("I0000001", "P0000001", "In P1", "Desc", IssueStatus.TODO, Priority.NORMAL));
        repository.save(new Issue("I0000002", "P0000002", "In P2", "Desc", IssueStatus.TODO, Priority.NORMAL));
        repository.save(new Issue("I0000003", "P0000001", "Also P1", "Desc", IssueStatus.TODO, Priority.NORMAL));

        List<Issue> p1Issues = repository.findByProjectId("P0000001");
        assertEquals(2, p1Issues.size());
    }

    @Test
    @Transactional
    void testDeleteById() {
        repository.save(new Issue("I0000001", "P0000001", "Test", "Desc", IssueStatus.TODO, Priority.NORMAL));
        repository.deleteById("I0000001");
        assertFalse(repository.findById("I0000001").isPresent());
    }
}
