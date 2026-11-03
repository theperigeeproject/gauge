package com.sheahorn.gauge.repository;

import com.sheahorn.gauge.domain.Tasklist;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class TasklistRepository {

    @Transactional
    public Tasklist save(Tasklist tasklist) {
        Optional<Tasklist> existing = Tasklist.findByIdOptional(tasklist.id);
        if (existing.isPresent()) {
            Tasklist managed = existing.get();
            managed.issueId = tasklist.issueId;
            managed.title = tasklist.title;
            managed.status = tasklist.status;
            managed.decomposesTaskId = tasklist.decomposesTaskId;
            managed.persist();
            return managed;
        }
        tasklist.persist();
        return tasklist;
    }

    public Optional<Tasklist> findById(String id) {
        return Tasklist.findByIdOptional(id);
    }

    public List<Tasklist> findAll() {
        return Tasklist.listAll();
    }

    public List<Tasklist> findByIssueId(String issueId) {
        return Tasklist.list("issueId", issueId);
    }

    @Transactional
    public void deleteById(String id) {
        Tasklist.deleteById(id);
    }

    public long countByStatus(String status) {
        return Tasklist.count("status", status);
    }

    /** Rewrites every tasklist using {@code from} to {@code to}. Used by status rename/delete. */
    @Transactional
    public int replaceStatus(String from, String to) {
        return Tasklist.update("status = ?1 where status = ?2", to, from);
    }
}
