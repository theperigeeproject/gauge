package pl.perigee.gauge.repository;

import pl.perigee.gauge.domain.Project;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ProjectRepository {

    @Transactional
    public Project save(Project project) {
        Optional<Project> existing = Project.findByIdOptional(project.id);
        if (existing.isPresent()) {
            Project managed = existing.get();
            managed.name = project.name;
            managed.description = project.description;
            managed.parentId = project.parentId;
            managed.removalLock = project.removalLock;
            managed.persist();
            return managed;
        }
        project.persist();
        return project;
    }

    public Optional<Project> findById(String id) {
        return Project.findByIdOptional(id);
    }

    public List<Project> findAll() {
        return Project.listAll();
    }

    public List<Project> findByParentId(String parentId) {
        return Project.list("parentId", parentId);
    }

    public List<Project> findRootProjects() {
        return Project.list("parentId is null");
    }

    public List<Project> findByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return Project.list("id in ?1", ids);
    }

    @Transactional
    public void deleteById(String id) {
        Project.deleteById(id);
    }
}
