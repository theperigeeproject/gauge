package com.sheahorn.gauge.repository;

import com.sheahorn.gauge.domain.Task;
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

    public long countByStatus(String status) {
        return Task.count("status", status);
    }

    /** Rewrites every task using {@code from} to {@code to}. Used by status rename/delete. */
    @Transactional
    public int replaceStatus(String from, String to) {
        return Task.update("status = ?1 where status = ?2", to, from);
    }
}
