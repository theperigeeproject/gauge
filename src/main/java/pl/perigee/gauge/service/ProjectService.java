package pl.perigee.gauge.service;

import pl.perigee.gauge.domain.Project;
import pl.perigee.gauge.domain.StatusLevel;
import pl.perigee.gauge.repository.IssueRepository;
import pl.perigee.gauge.repository.ProjectRepository;
import pl.perigee.gauge.repository.TasklistRepository;
import pl.perigee.gauge.repository.TaskRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ProjectService {

    @Inject
    ProjectRepository repository;

    @Inject
    IssueRepository issueRepository;

    @Inject
    TasklistRepository tasklistRepository;

    @Inject
    TaskRepository taskRepository;

    @Inject
    StatusService statusService;

    @Transactional
    public Project create(String name, String description, String parentId) {
        // A fresh project gets the level's default status (lowest precedence, OPENED today).
        // Before increment 4 it was left null and read as NEEDS_MIGRATION until an admin ran the
        // migration, which was legal but wrong-feeling.
        Project project = Project.create(name, description, parentId,
            statusService.defaultStatusId(StatusLevel.PROJECT));
        return repository.save(project);
    }

    public Optional<Project> findById(String id) {
        return repository.findById(id);
    }

    public List<Project> findAll() {
        return repository.findAll();
    }

    public List<Project> findByParentId(String parentId) {
        return repository.findByParentId(parentId);
    }

    public List<Project> findRootProjects() {
        return repository.findRootProjects();
    }

    public List<Project> findByIds(List<String> ids) {
        return repository.findByIds(ids);
    }

    public List<Project> search(String q) {
        String lower = q.toLowerCase();
        return repository.findAll().stream()
            .filter(p -> (p.name() != null && p.name().toLowerCase().contains(lower))
                       || (p.description() != null && p.description().toLowerCase().contains(lower)))
            .toList();
    }

    public List<Project> getAncestors(String id) {
        List<Project> ancestors = new ArrayList<>();
        Optional<Project> current = repository.findById(id);
        while (current.isPresent()) {
            ancestors.add(current.get());
            String parentId = current.get().parentId();
            if (parentId == null) {
                break;
            }
            current = repository.findById(parentId);
        }
        return ancestors;
    }

    @Transactional
    public Optional<Project> patch(String id, String name, String description, String removalLock) {
        return repository.findById(id).map(existing -> {
            if (name != null) {
                existing.name = name;
            }
            if (description != null) {
                existing.description = description;
            }
            if (removalLock != null) {
                existing.removalLock = removalLock;
            }
            return repository.save(existing);
        });
    }

    @Transactional
    public Optional<Project> reparent(String id, String newParentId) {
        if (repository.findById(id).isEmpty()) {
            return Optional.empty();
        }
        // Cycle detection: newParentId must not be the project itself
        // or any of its descendants.
        if (newParentId != null) {
            if (newParentId.equals(id)) {
                throw new IllegalArgumentException(
                    "Cannot reparent a project under itself — that would create a cycle.");
            }
            if (isDescendantOf(newParentId, id)) {
                throw new IllegalArgumentException(
                    "Cannot reparent a project under one of its own descendants — that would create a cycle.");
            }
        }
        return repository.findById(id).map(existing -> {
            existing.parentId = newParentId;
            return repository.save(existing);
        });
    }

    /**
     * Returns true if {@code candidateAncestor} is a descendant of {@code projectId}.
     * Walks up the parent chain from candidateAncestor.
     */
    private boolean isDescendantOf(String candidateAncestor, String projectId) {
        String current = candidateAncestor;
        while (current != null) {
            Optional<Project> p = repository.findById(current);
            if (p.isEmpty()) {
                return false;
            }
            String parentId = p.get().parentId();
            if (parentId == null) {
                return false;
            }
            if (parentId.equals(projectId)) {
                return true;
            }
            current = parentId;
        }
        return false;
    }

    public boolean hasChildren(String id) {
        return !repository.findByParentId(id).isEmpty()
            || !issueRepository.findByProjectId(id).isEmpty();
    }

    public boolean isLocked(String id) {
        return repository.findById(id)
            .map(p -> "locked".equals(p.removalLock()))
            .orElse(false);
    }

    @Transactional
    public boolean cascadeDelete(String id) {
        Optional<Project> opt = repository.findById(id);
        if (opt.isEmpty()) {
            return false;
        }
        if ("locked".equals(opt.get().removalLock())) {
            return false;
        }
        for (Project subproject : repository.findByParentId(id)) {
            if (anyDescendantLocked(subproject.id())) {
                return false;
            }
        }
        // Actually delete
        for (Project subproject : repository.findByParentId(id)) {
            cascadeDelete(subproject.id());
        }
        for (var issue : issueRepository.findByProjectId(id)) {
            for (var tasklist : tasklistRepository.findByIssueId(issue.id())) {
                for (var task : taskRepository.findByTasklistId(tasklist.id())) {
                    taskRepository.deleteById(task.id());
                }
                tasklistRepository.deleteById(tasklist.id());
            }
            issueRepository.deleteById(issue.id());
        }
        repository.deleteById(id);
        return true;
    }

    private boolean anyDescendantLocked(String id) {
        Optional<Project> opt = repository.findById(id);
        if (opt.isEmpty()) return false;
        if ("locked".equals(opt.get().removalLock())) return true;
        for (Project sub : repository.findByParentId(id)) {
            if (anyDescendantLocked(sub.id())) return true;
        }
        return false;
    }

    @Transactional
    public boolean deleteById(String id) {
        Optional<Project> opt = repository.findById(id);
        if (opt.isEmpty()) {
            return false;
        }
        if ("locked".equals(opt.get().removalLock())) {
            return false;
        }
        repository.deleteById(id);
        return true;
    }
}
