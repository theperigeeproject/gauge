package pl.perigee.gauge.service;

import pl.perigee.gauge.domain.Task;
import pl.perigee.gauge.domain.Tasklist;
import pl.perigee.gauge.domain.TasklistStatus;
import pl.perigee.gauge.repository.TasklistRepository;
import pl.perigee.gauge.repository.TaskRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class TasklistService {

    @Inject
    TasklistRepository repository;

    @Inject
    TaskRepository taskRepository;

    @Transactional
    public Tasklist create(String issueId, String title, String decomposesTaskId) {
        validateDecomposesTask(issueId, null, decomposesTaskId);
        Tasklist tasklist = Tasklist.create(issueId, title, decomposesTaskId);
        return repository.save(tasklist);
    }

    public Optional<Tasklist> findById(String id) {
        return repository.findById(id);
    }

    public List<Tasklist> findAll() {
        return repository.findAll();
    }

    public List<Tasklist> findByIssueId(String issueId) {
        return repository.findByIssueId(issueId);
    }

    public List<Tasklist> search(String q) {
        String lower = q.toLowerCase();
        return repository.findAll().stream()
            .filter(tl -> tl.title() != null && tl.title().toLowerCase().contains(lower))
            .toList();
    }

    @Transactional
    public Optional<Tasklist> patch(String id, String title) {
        return repository.findById(id).map(existing -> {
            Tasklist updated = new Tasklist(
                existing.id(),
                existing.issueId(),
                title != null ? title : existing.title(),
                existing.status(),
                existing.decomposesTaskId()
            );
            return repository.save(updated);
        });
    }

    @Transactional
    public Optional<Tasklist> updateStatus(String id, TasklistStatus status) {
        return repository.findById(id).map(existing -> {
            Tasklist updated = new Tasklist(
                existing.id(),
                existing.issueId(),
                existing.title(),
                status,
                existing.decomposesTaskId()
            );
            return repository.save(updated);
        });
    }

    @Transactional
    public Optional<Tasklist> updateDecomposesTask(String id, String decomposesTaskId) {
        return repository.findById(id).map(existing -> {
            validateDecomposesTask(existing.issueId(), id, decomposesTaskId);
            Tasklist updated = new Tasklist(
                existing.id(),
                existing.issueId(),
                existing.title(),
                existing.status(),
                decomposesTaskId
            );
            return repository.save(updated);
        });
    }

    public boolean hasChildren(String id) {
        return !taskRepository.findByTasklistId(id).isEmpty();
    }

    @Transactional
    public boolean cascadeDelete(String id) {
        if (repository.findById(id).isEmpty()) {
            return false;
        }
        for (var task : taskRepository.findByTasklistId(id)) {
            taskRepository.deleteById(task.id());
        }
        repository.deleteById(id);
        return true;
    }

    @Transactional
    public boolean deleteById(String id) {
        if (repository.findById(id).isPresent()) {
            repository.deleteById(id);
            return true;
        }
        return false;
    }

    // ---- decomposesTaskId validation (ADR 13) ----

    /**
     * Validates that {@code decomposesTaskId} references a task that:
     * <ol>
     *   <li>exists,</li>
     *   <li>belongs to the same issue as this tasklist, and</li>
     *   <li>lives in a different tasklist (when {@code thisTasklistId} is non-null).</li>
     * </ol>
     * {@code null} is always valid (unsetting the link).
     *
     * @param issueId          the issue this tasklist belongs to
     * @param thisTasklistId   the tasklist being created/updated (null during create)
     * @param decomposesTaskId the task ID to validate
     * @throws IllegalArgumentException if the reference is invalid
     */
    private void validateDecomposesTask(String issueId, String thisTasklistId, String decomposesTaskId) {
        if (decomposesTaskId == null) {
            return; // unsetting is always valid
        }

        // 1. Task must exist
        Optional<Task> taskOpt = taskRepository.findById(decomposesTaskId);
        if (taskOpt.isEmpty()) {
            throw new IllegalArgumentException(
                "decomposesTaskId references non-existent task: " + decomposesTaskId);
        }

        Task task = taskOpt.get();

        // 2. Task's parent tasklist must exist
        Optional<Tasklist> parentTasklistOpt = repository.findById(task.tasklistId());
        if (parentTasklistOpt.isEmpty()) {
            throw new IllegalArgumentException(
                "decomposesTaskId references task in non-existent tasklist: " + task.tasklistId());
        }

        Tasklist parentTasklist = parentTasklistOpt.get();

        // 3. Task must be in the same issue
        if (!parentTasklist.issueId().equals(issueId)) {
            throw new IllegalArgumentException(
                "decomposesTaskId references task in a different issue: " + parentTasklist.issueId()
                + " (expected: " + issueId + ")");
        }

        // 4. Task must be in a different tasklist (skip during create — new tasklist can't collide)
        if (thisTasklistId != null && parentTasklist.id().equals(thisTasklistId)) {
            throw new IllegalArgumentException(
                "decomposesTaskId cannot reference a task in the same tasklist");
        }
    }
}
