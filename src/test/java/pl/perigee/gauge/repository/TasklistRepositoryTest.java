package pl.perigee.gauge.repository;

import pl.perigee.gauge.domain.Tasklist;
import pl.perigee.gauge.domain.TasklistStatus;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class TasklistRepositoryTest {

    @Inject
    TasklistRepository repository;

    @BeforeEach
    @Transactional
    void cleanUp() {
        repository.deleteById("TL000001");
        repository.deleteById("TL000002");
        repository.deleteById("TL000003");
    }

    @Test
    @Transactional
    void testSaveNewTasklist() {
        Tasklist tl = new Tasklist("TL000001", "I0000001", "Test", TasklistStatus.TODO, null);
        Tasklist saved = repository.save(tl);
        assertNotNull(saved);
        assertEquals("TL000001", saved.id);
    }

    @Test
    @Transactional
    void testSaveUpdatesExisting() {
        Tasklist tl = new Tasklist("TL000001", "I0000001", "Original", TasklistStatus.TODO, null);
        repository.save(tl);

        Tasklist updated = new Tasklist("TL000001", "I0000002", "Updated", TasklistStatus.DOING, "T0000001");
        Tasklist saved = repository.save(updated);

        assertEquals("Updated", saved.title);
        assertEquals("I0000002", saved.issueId);
        assertEquals(TasklistStatus.DOING.name(), saved.status);
        assertEquals("T0000001", saved.decomposesTaskId);
    }

    @Test
    @Transactional
    void testFindById() {
        Tasklist tl = new Tasklist("TL000001", "I0000001", "Test", TasklistStatus.TODO, null);
        repository.save(tl);

        Optional<Tasklist> found = repository.findById("TL000001");
        assertTrue(found.isPresent());
        assertEquals("Test", found.get().title());
    }

    @Test
    @Transactional
    void testFindByIdNotFound() {
        Optional<Tasklist> found = repository.findById("NONEXISTENT");
        assertFalse(found.isPresent());
    }

    @Test
    @Transactional
    void testFindAll() {
        repository.save(new Tasklist("TL000001", "I0000001", "TL1", TasklistStatus.TODO, null));
        repository.save(new Tasklist("TL000002", "I0000001", "TL2", TasklistStatus.TODO, null));

        List<Tasklist> all = repository.findAll();
        assertTrue(all.size() >= 2);
    }

    @Test
    @Transactional
    void testFindByIssueId() {
        repository.save(new Tasklist("TL000001", "I0000001", "In I1", TasklistStatus.TODO, null));
        repository.save(new Tasklist("TL000002", "I0000002", "In I2", TasklistStatus.TODO, null));
        repository.save(new Tasklist("TL000003", "I0000001", "Also I1", TasklistStatus.TODO, null));

        List<Tasklist> i1Tasklists = repository.findByIssueId("I0000001");
        assertEquals(2, i1Tasklists.size());
    }

    @Test
    @Transactional
    void testDeleteById() {
        repository.save(new Tasklist("TL000001", "I0000001", "Test", TasklistStatus.TODO, null));
        repository.deleteById("TL000001");
        assertFalse(repository.findById("TL000001").isPresent());
    }
}
