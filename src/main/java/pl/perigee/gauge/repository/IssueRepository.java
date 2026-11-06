package pl.perigee.gauge.repository;

import pl.perigee.gauge.domain.Issue;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class IssueRepository {

    @Transactional
    public Issue save(Issue issue) {
        Optional<Issue> existing = Issue.findByIdOptional(issue.id);
        if (existing.isPresent()) {
            Issue managed = existing.get();
            managed.projectId = issue.projectId;
            managed.title = issue.title;
            managed.description = issue.description;
            managed.status = issue.status;
            managed.priority = issue.priority;
            managed.persist();
            return managed;
        }
        issue.persist();
        return issue;
    }

    public Optional<Issue> findById(String id) {
        return Issue.findByIdOptional(id);
    }

    public List<Issue> findAll() {
        return Issue.listAll();
    }

    public List<Issue> findByProjectId(String projectId) {
        return Issue.list("projectId", projectId);
    }

    @Transactional
    public void deleteById(String id) {
        Issue.deleteById(id);
    }
}
