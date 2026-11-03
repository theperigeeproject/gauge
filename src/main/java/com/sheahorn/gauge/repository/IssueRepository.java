package com.sheahorn.gauge.repository;

import com.sheahorn.gauge.domain.Issue;
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

    public long countByStatus(String status) {
        return Issue.count("status", status);
    }

    /** Rewrites every issue using {@code from} to {@code to}. Used by status rename/delete. */
    @Transactional
    public int replaceStatus(String from, String to) {
        return Issue.update("status = ?1 where status = ?2", to, from);
    }
}
