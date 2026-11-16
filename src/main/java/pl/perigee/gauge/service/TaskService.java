package pl.perigee.gauge.service;

import pl.perigee.gauge.domain.StatusLevel;
import pl.perigee.gauge.domain.Task;
import pl.perigee.gauge.repository.TaskRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;

/**
 * Tasks. Same status rule as everywhere: writes resolve a code to an id, reads never see one
 * (ADR 0020 §2/§4).
 */
@ApplicationScoped
public class TaskService {

    @Inject
    TaskRepository repository;

    @Inject
    StatusService statusService;

    @Transactional
    public Task create(String tasklistId, String title, String description) {
        int ordinal = repository.findByTasklistId(tasklistId).size();
        Task task = Task.create(tasklistId, ordinal, title, description,
            statusService.defaultStatusId(StatusLevel.TASK));
        return repository.save(task);
    }

    public Optional<Task> findById(String id) {
        return repository.findById(id);
    }

    public List<Task> findAll() {
        return repository.findAll();
    }

    public List<Task> findByTasklistId(String tasklistId) {
        return repository.findByTasklistId(tasklistId);
    }

    public List<Task> search(String q) {
        String lower = q.toLowerCase();
        return repository.findAll().stream()
            .filter(t -> (t.title() != null && t.title().toLowerCase().contains(lower))
                       || (t.description() != null && t.description().toLowerCase().contains(lower)))
            .toList();
    }

    @Transactional
    public Optional<Task> patch(String id, String title, String description) {
        return repository.findById(id).map(existing -> {
            if (title != null) {
                existing.title = title;
            }
            if (description != null) {
                existing.description = description;
            }
            return repository.save(existing);
        });
    }

    /** @param code a status code for the TASK level; an unknown value is a 400, never a default */
    @Transactional
    public Optional<Task> updateStatus(String id, String code) {
        Integer statusId = statusService.resolveForWrite(StatusLevel.TASK, code);
        return repository.findById(id).map(existing -> {
            existing.setStatusId(statusId);
            return repository.save(existing);
        });
    }

    @Transactional
    public void reorder(String tasklistId, List<String> taskIds) {
        for (int i = 0; i < taskIds.size(); i++) {
            final int ordinal = i;
            String taskId = taskIds.get(i);
            Optional<Task> opt = repository.findById(taskId);
            if (opt.isEmpty()) {
                continue; // skip nonexistent IDs silently
            }
            Task existing = opt.get();
            if (!existing.tasklistId().equals(tasklistId)) {
                throw new IllegalArgumentException(
                    "Task " + taskId + " does not belong to tasklist " + tasklistId
                    + " (it belongs to " + existing.tasklistId() + ")");
            }
            existing.ordinal = ordinal;
            repository.save(existing);
        }
    }

    @Transactional
    public boolean deleteById(String id) {
        if (repository.findById(id).isPresent()) {
            repository.deleteById(id);
            return true;
        }
        return false;
    }
}
