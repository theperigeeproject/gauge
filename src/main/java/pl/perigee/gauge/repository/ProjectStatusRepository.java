package pl.perigee.gauge.repository;

import pl.perigee.gauge.domain.ProjectStatusDef;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

/** Database access for project statuses. See {@link IssueStatusRepository} for the rationale. */
@ApplicationScoped
public class ProjectStatusRepository {

    @Transactional
    public ProjectStatusDef save(ProjectStatusDef status) {
        Optional<ProjectStatusDef> existing = ProjectStatusDef.findByIdOptional(status.id);
        if (existing.isPresent()) {
            ProjectStatusDef managed = existing.get();
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

    public Optional<ProjectStatusDef> findById(Integer id) {
        return ProjectStatusDef.findByIdOptional(id);
    }

    public Optional<ProjectStatusDef> findByCode(String code) {
        return ProjectStatusDef.find("code", code).firstResultOptional();
    }

    public List<ProjectStatusDef> listAll() {
        return ProjectStatusDef.list("ORDER BY precedenceValue ASC");
    }

    public long count() {
        return ProjectStatusDef.count();
    }

    @Transactional
    public void delete(ProjectStatusDef status) {
        ProjectStatusDef.deleteById(status.id);
    }
}
