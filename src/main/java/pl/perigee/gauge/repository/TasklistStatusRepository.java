package pl.perigee.gauge.repository;

import pl.perigee.gauge.domain.TasklistStatusDef;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

/** Database access for tasklist statuses. See {@link IssueStatusRepository} for the rationale. */
@ApplicationScoped
public class TasklistStatusRepository {

    @Transactional
    public TasklistStatusDef save(TasklistStatusDef status) {
        Optional<TasklistStatusDef> existing = TasklistStatusDef.findByIdOptional(status.id);
        if (existing.isPresent()) {
            TasklistStatusDef managed = existing.get();
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

    public Optional<TasklistStatusDef> findById(Integer id) {
        return TasklistStatusDef.findByIdOptional(id);
    }

    public Optional<TasklistStatusDef> findByCode(String code) {
        return TasklistStatusDef.find("code", code).firstResultOptional();
    }

    public List<TasklistStatusDef> listAll() {
        return TasklistStatusDef.list("ORDER BY precedenceValue ASC");
    }

    public long count() {
        return TasklistStatusDef.count();
    }

    @Transactional
    public void delete(TasklistStatusDef status) {
        TasklistStatusDef.deleteById(status.id);
    }
}
