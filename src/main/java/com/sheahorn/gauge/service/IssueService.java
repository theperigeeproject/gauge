package pl.perigee.gauge.service;

import pl.perigee.gauge.domain.Issue;
import pl.perigee.gauge.domain.IssueStatus;
import pl.perigee.gauge.domain.Priority;
import pl.perigee.gauge.domain.SortOption;
import pl.perigee.gauge.repository.IssueRepository;
import pl.perigee.gauge.repository.TasklistRepository;
import pl.perigee.gauge.repository.TaskRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class IssueService {

    @Inject
    IssueRepository repository;

    @Inject
    TasklistRepository tasklistRepository;

    @Inject
    TaskRepository taskRepository;

    @Transactional
    public Issue create(String projectId, String title, String description, Priority priority) {
        Issue issue = Issue.create(projectId, title, description, priority);
        return repository.save(issue);
    }

    public Optional<Issue> findById(String id) {
        return repository.findById(id);
    }

    public List<Issue> findAll() {
        return repository.findAll();
    }

    public List<Issue> findByProjectId(String projectId) {
        return repository.findByProjectId(projectId);
    }

    public List<Issue> findByProjectIdSortedAndFiltered(
        String projectId,
        SortOption sort,
        Set<Priority> priorities,
        Set<IssueStatus> statuses
    ) {
        List<Issue> issues = repository.findByProjectId(projectId);

        // Filter
        if (priorities != null && !priorities.isEmpty()) {
            issues = issues.stream()
                .filter(i -> priorities.contains(i.priority()))
                .collect(Collectors.toList());
        }
        if (statuses != null && !statuses.isEmpty()) {
            issues = issues.stream()
                .filter(i -> statuses.contains(i.status()))
                .collect(Collectors.toList());
        }

        // Sort
        SortOption effectiveSort = sort != null ? sort : SortOption.PRIORITY_STATUS_NAME;
        Comparator<Issue> cmp = buildComparator(effectiveSort);
        issues.sort(cmp);
        return issues;
    }

    private Comparator<Issue> buildComparator(SortOption sort) {
        Comparator<Issue> cmp;
        switch (sort) {
            case NAME:
                cmp = Comparator.comparing(
                    i -> i.title() != null ? i.title().toLowerCase() : "",
                    String::compareTo
                );
                break;
            case PRIORITY_STATUS_NAME:
                cmp = Comparator.comparingInt((Issue i) -> priorityOrder(i.priority()))
                    .thenComparingInt(i -> statusOrder(i.status()))
                    .thenComparing(i -> i.title() != null ? i.title().toLowerCase() : "", String::compareTo);
                break;
            case PRIORITY_NAME_STATUS:
                cmp = Comparator.comparingInt((Issue i) -> priorityOrder(i.priority()))
                    .thenComparing(i -> i.title() != null ? i.title().toLowerCase() : "", String::compareTo)
                    .thenComparingInt(i -> statusOrder(i.status()));
                break;
            case STATUS_PRIORITY_NAME:
                cmp = Comparator.comparingInt((Issue i) -> statusOrder(i.status()))
                    .thenComparingInt(i -> priorityOrder(i.priority()))
                    .thenComparing(i -> i.title() != null ? i.title().toLowerCase() : "", String::compareTo);
                break;
            case STATUS_NAME_PRIORITY:
                cmp = Comparator.comparingInt((Issue i) -> statusOrder(i.status()))
                    .thenComparing(i -> i.title() != null ? i.title().toLowerCase() : "", String::compareTo)
                    .thenComparingInt(i -> priorityOrder(i.priority()));
                break;
            default:
                cmp = Comparator.comparing(i -> i.title() != null ? i.title().toLowerCase() : "", String::compareTo);
        }
        return cmp.thenComparing(Issue::id);
    }

    private int priorityOrder(Priority p) {
        // HIGH severity=2 → sort first (0), LOW severity=0 → sort last (2)
        return 2 - p.severity;
    }

    private int statusOrder(IssueStatus s) {
        return switch (s) {
            case TODO -> 0;
            case DOING -> 1;
            case DONE -> 2;
            case INACTIVE -> 3;
        };
    }

    public List<Issue> search(String q) {
        String lower = q.toLowerCase();
        return repository.findAll().stream()
            .filter(i -> (i.title() != null && i.title().toLowerCase().contains(lower))
                       || (i.description() != null && i.description().toLowerCase().contains(lower)))
            .toList();
    }

    @Transactional
    public Optional<Issue> patch(String id, String title, String description) {
        return repository.findById(id).map(existing -> {
            Issue updated = new Issue(
                existing.id(),
                existing.projectId(),
                title != null ? title : existing.title(),
                description != null ? description : existing.description(),
                existing.status(),
                existing.priority()
            );
            return repository.save(updated);
        });
    }

    @Transactional
    public Optional<Issue> updateStatus(String id, IssueStatus status) {
        return repository.findById(id).map(existing -> {
            Issue updated = new Issue(
                existing.id(),
                existing.projectId(),
                existing.title(),
                existing.description(),
                status,
                existing.priority()
            );
            return repository.save(updated);
        });
    }

    @Transactional
    public Optional<Issue> updatePriority(String id, Priority priority) {
        return repository.findById(id).map(existing -> {
            Issue updated = new Issue(
                existing.id(),
                existing.projectId(),
                existing.title(),
                existing.description(),
                existing.status(),
                priority
            );
            return repository.save(updated);
        });
    }

    @Transactional
    public Optional<Issue> moveToProject(String id, String newProjectId) {
        return repository.findById(id).map(existing -> {
            Issue updated = new Issue(
                existing.id(),
                newProjectId,
                existing.title(),
                existing.description(),
                existing.status(),
                existing.priority()
            );
            return repository.save(updated);
        });
    }

    public boolean hasChildren(String id) {
        return !tasklistRepository.findByIssueId(id).isEmpty();
    }

    @Transactional
    public boolean cascadeDelete(String id) {
        if (repository.findById(id).isEmpty()) {
            return false;
        }
        for (var tasklist : tasklistRepository.findByIssueId(id)) {
            for (var task : taskRepository.findByTasklistId(tasklist.id())) {
                taskRepository.deleteById(task.id());
            }
            tasklistRepository.deleteById(tasklist.id());
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
}
