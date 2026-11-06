package pl.perigee.gauge.repository;

import pl.perigee.gauge.domain.Project;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ProjectRepositoryTest {

    @Inject
    ProjectRepository repository;

    @BeforeEach
    @Transactional
    void cleanUp() {
        repository.deleteById("P0000001");
        repository.deleteById("P0000002");
        repository.deleteById("P0000003");
    }

    @Test
    @Transactional
    void testSaveNewProject() {
        Project p = new Project("P0000001", "Test", "Desc", null, "");
        Project saved = repository.save(p);
        assertNotNull(saved);
        assertEquals("P0000001", saved.id);
    }

    @Test
    @Transactional
    void testSaveUpdatesExisting() {
        Project p = new Project("P0000001", "Original", "Desc", null, "");
        repository.save(p);

        Project updated = new Project("P0000001", "Updated", "New Desc", null, "locked");
        Project saved = repository.save(updated);

        assertEquals("Updated", saved.name);
        assertEquals("New Desc", saved.description);
        assertEquals("locked", saved.removalLock);
    }

    @Test
    @Transactional
    void testSaveUpdateWithNullName() {
        Project p = new Project("P0000001", "Original", "Desc", null, "");
        repository.save(p);

        Project updated = new Project("P0000001", null, "Desc", null, "");
        // Repository sets name=null on the managed entity. Hibernate validates
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
        Project p = new Project("P0000001", "Test", "Desc", null, "");
        repository.save(p);

        Optional<Project> found = repository.findById("P0000001");
        assertTrue(found.isPresent());
        assertEquals("Test", found.get().name);
    }

    @Test
    @Transactional
    void testFindByIdNotFound() {
        Optional<Project> found = repository.findById("NONEXISTENT");
        assertFalse(found.isPresent());
    }

    @Test
    @Transactional
    void testFindAll() {
        repository.save(new Project("P0000001", "P1", "Desc", null, ""));
        repository.save(new Project("P0000002", "P2", "Desc", null, ""));

        List<Project> all = repository.findAll();
        assertTrue(all.size() >= 2);
    }

    @Test
    @Transactional
    void testFindByParentId() {
        repository.save(new Project("P0000001", "Parent", "Desc", null, ""));
        repository.save(new Project("P0000002", "Child1", "Desc", "P0000001", ""));
        repository.save(new Project("P0000003", "Child2", "Desc", "P0000001", ""));

        List<Project> children = repository.findByParentId("P0000001");
        assertEquals(2, children.size());
    }

    @Test
    @Transactional
    void testFindRootProjects() {
        repository.save(new Project("P0000001", "Root1", "Desc", null, ""));
        repository.save(new Project("P0000002", "Root2", "Desc", null, ""));
        repository.save(new Project("P0000003", "Child", "Desc", "P0000001", ""));

        List<Project> roots = repository.findRootProjects();
        assertTrue(roots.stream().allMatch(p -> p.parentId == null));
        assertTrue(roots.stream().anyMatch(p -> p.name.equals("Root1")));
        assertTrue(roots.stream().anyMatch(p -> p.name.equals("Root2")));
    }

    @Test
    @Transactional
    void testFindByIds() {
        repository.save(new Project("P0000001", "P1", "Desc", null, ""));
        repository.save(new Project("P0000002", "P2", "Desc", null, ""));
        repository.save(new Project("P0000003", "P3", "Desc", null, ""));

        List<Project> found = repository.findByIds(List.of("P0000001", "P0000002"));
        assertEquals(2, found.size());
    }

    @Test
    @Transactional
    void testFindByIdsEmpty() {
        List<Project> found = repository.findByIds(List.of());
        assertTrue(found.isEmpty());
    }

    @Test
    @Transactional
    void testFindByIdsNull() {
        List<Project> found = repository.findByIds(null);
        assertTrue(found.isEmpty());
    }

    @Test
    @Transactional
    void testDeleteById() {
        repository.save(new Project("P0000001", "Test", "Desc", null, ""));
        repository.deleteById("P0000001");
        assertFalse(repository.findById("P0000001").isPresent());
    }
}
