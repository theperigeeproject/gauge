package pl.perigee.gauge.repository;

import pl.perigee.gauge.domain.TaskStatusDef;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

/** Database access for task statuses. See {@link IssueStatusRepository} for the rationale. */
@ApplicationScoped
public class TaskStatusRepository {

    @Transactional
    public TaskStatusDef save(TaskStatusDef status) {
        Optional<TaskStatusDef> existing = TaskStatusDef.findByIdOptional(status.id);
        if (existing.isPresent()) {
            TaskStatusDef managed = existing.get();
            managed.code = status.code;
            managed.precedenceValue = status.precedenceValue;
            managed.color = status.color;
            managed.progressLevel = status.progressLevel;
            managed.attitudeType = status.attitudeType;
            managed.persist();
            return managed;
        }
        status.persist();
        return status;
    }

    public Optional<TaskStatusDef> findById(Integer id) {
        return TaskStatusDef.findByIdOptional(id);
    }

    public Optional<TaskStatusDef> findByCode(String code) {
        return TaskStatusDef.find("code", code).firstResultOptional();
    }

    public List<TaskStatusDef> listAll() {
        return TaskStatusDef.list("ORDER BY precedenceValue ASC");
    }

    public long count() {
        return TaskStatusDef.count();
    }

    @Transactional
    public void delete(TaskStatusDef status) {
        TaskStatusDef.deleteById(status.id);
    }
}
