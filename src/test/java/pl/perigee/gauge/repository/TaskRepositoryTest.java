package pl.perigee.gauge.repository;

import pl.perigee.gauge.domain.Task;
import pl.perigee.gauge.domain.TaskStatus;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class TaskRepositoryTest {

    @Inject
    TaskRepository repository;

    @BeforeEach
    @Transactional
    void cleanUp() {
        repository.deleteById("T0000001");
        repository.deleteById("T0000002");
        repository.deleteById("T0000003");
    }

    @Test
    @Transactional
    void testSaveNewTask() {
        Task t = new Task("T0000001", "TL000001", 0, "Test", "Desc", TaskStatus.TODO);
        Task saved = repository.save(t);
        assertNotNull(saved);
        assertEquals("T0000001", saved.id);
    }

    @Test
    @Transactional
    void testSaveUpdatesExisting() {
        Task t = new Task("T0000001", "TL000001", 0, "Original", "Desc", TaskStatus.TODO);
        repository.save(t);

        Task updated = new Task("T0000001", "TL000002", 5, "Updated", "New Desc", TaskStatus.DOING);
        Task saved = repository.save(updated);

        assertEquals("Updated", saved.title);
        assertEquals("New Desc", saved.description);
        assertEquals("TL000002", saved.tasklistId);
        assertEquals(5, saved.ordinal);
        assertEquals(TaskStatus.DOING.name(), saved.status);
    }

    @Test
    @Transactional
    void testFindById() {
        Task t = new Task("T0000001", "TL000001", 0, "Test", "Desc", TaskStatus.TODO);
        repository.save(t);

        Optional<Task> found = repository.findById("T0000001");
        assertTrue(found.isPresent());
        assertEquals("Test", found.get().title());
    }

    @Test
    @Transactional
    void testFindByIdNotFound() {
        Optional<Task> found = repository.findById("NONEXISTENT");
        assertFalse(found.isPresent());
    }

    @Test
    @Transactional
    void testFindAll() {
        repository.save(new Task("T0000001", "TL000001", 0, "T1", "Desc", TaskStatus.TODO));
        repository.save(new Task("T0000002", "TL000001", 1, "T2", "Desc", TaskStatus.TODO));

        List<Task> all = repository.findAll();
        assertTrue(all.size() >= 2);
    }

    @Test
    @Transactional
    void testFindByTasklistId() {
        repository.save(new Task("T0000001", "TL000001", 0, "In TL1", "Desc", TaskStatus.TODO));
        repository.save(new Task("T0000002", "TL000002", 0, "In TL2", "Desc", TaskStatus.TODO));
        repository.save(new Task("T0000003", "TL000001", 1, "Also TL1", "Desc", TaskStatus.TODO));

        List<Task> tl1Tasks = repository.findByTasklistId("TL000001");
        assertEquals(2, tl1Tasks.size());
    }

    @Test
    @Transactional
    void testDeleteById() {
        repository.save(new Task("T0000001", "TL000001", 0, "Test", "Desc", TaskStatus.TODO));
        repository.deleteById("T0000001");
        assertFalse(repository.findById("T0000001").isPresent());
    }
}
