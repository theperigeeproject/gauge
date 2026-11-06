package pl.perigee.gauge.repository;

import pl.perigee.gauge.domain.Task;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class TaskRepository {

    @Transactional
    public Task save(Task task) {
        Optional<Task> existing = Task.findByIdOptional(task.id);
        if (existing.isPresent()) {
            Task managed = existing.get();
            managed.tasklistId = task.tasklistId;
            managed.ordinal = task.ordinal;
            managed.title = task.title;
            managed.description = task.description;
            managed.status = task.status;
            managed.persist();
            return managed;
        }
        task.persist();
        return task;
    }

    public Optional<Task> findById(String id) {
        return Task.findByIdOptional(id);
    }

    public List<Task> findAll() {
        return Task.listAll();
    }

    public List<Task> findByTasklistId(String tasklistId) {
        return Task.list("tasklistId", tasklistId);
    }

    @Transactional
    public void deleteById(String id) {
        Task.deleteById(id);
    }
}
