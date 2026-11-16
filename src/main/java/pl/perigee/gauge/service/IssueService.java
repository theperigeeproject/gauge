package pl.perigee.gauge.service;

import pl.perigee.gauge.config.IssueDescriptionPolicy;
import pl.perigee.gauge.domain.Issue;
import pl.perigee.gauge.domain.Priority;
import pl.perigee.gauge.domain.SortOption;
import pl.perigee.gauge.domain.StatusBase;
import pl.perigee.gauge.domain.StatusLevel;
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

/**
 * Issues. Statuses are <b>ids</b> on the entity and <b>codes</b> on the wire, and the translation
 * happens only in {@link StatusService} (ADR 0020 §2/§4):
 *
 * <ul>
 *   <li>writes resolve a caller's code through {@code resolveForWrite} and persist the id;</li>
 *   <li>reads never look at a code — ordering asks the service for the precedence, filtering asks it
 *       to resolve the requested codes once.</li>
 * </ul>
 *
 * <p>Nothing here names a status. A rename is free, and a custom status sorts and filters correctly
 * without a code change.
 */
@ApplicationScoped
public class IssueService {

    @Inject
    IssueRepository repository;

    @Inject
    TasklistRepository tasklistRepository;

    @Inject
    TaskRepository taskRepository;

    @Inject
    IssueDescriptionPolicy descriptionPolicy;

    @Inject
    StatusService statusService;

    @Transactional
    public Issue create(String projectId, String title, String description, Priority priority) {
        // Both write paths funnel through the policy, so the length contract cannot diverge.
        descriptionPolicy.validate(description);
        Issue issue = Issue.create(projectId, title, description, priority,
            statusService.defaultStatusId(StatusLevel.ISSUE));
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

    /**
     * @param statuses resolved status rows, already validated against the level's available set by
     *                 {@code InputParser}; null or empty means "no filter"
     */
    public List<Issue> findByProjectIdSortedAndFiltered(
        String projectId,
        SortOption sort,
        Set<Priority> priorities,
        Set<StatusBase> statuses
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
                .filter(i -> statusService.matchesFilter(StatusLevel.ISSUE, i.statusId(), statuses))
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
                    .thenComparingInt(this::statusOrder)
                    .thenComparing(i -> i.title() != null ? i.title().toLowerCase() : "", String::compareTo);
                break;
            case PRIORITY_NAME_STATUS:
                cmp = Comparator.comparingInt((Issue i) -> priorityOrder(i.priority()))
                    .thenComparing(i -> i.title() != null ? i.title().toLowerCase() : "", String::compareTo)
                    .thenComparingInt(this::statusOrder);
                break;
            case STATUS_PRIORITY_NAME:
                cmp = Comparator.comparingInt(this::statusOrder)
                    .thenComparingInt(i -> priorityOrder(i.priority()))
                    .thenComparing(i -> i.title() != null ? i.title().toLowerCase() : "", String::compareTo);
                break;
            case STATUS_NAME_PRIORITY:
                cmp = Comparator.comparingInt(this::statusOrder)
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

    /**
     * The status sort key, straight from {@code precedenceValue} (ADR 0020 §6).
     *
     * <p>This reproduces the locked ADR 0015 order (TODO, DOING, DONE, INACTIVE) as long as the
     * seeds keep their precedences — and, unlike a switch on an enum, it stays correct when an admin
     * adds a status in the middle. An unmigrated row sorts by the migration status's precedence,
     * i.e. last.
     */
    private int statusOrder(Issue issue) {
        return statusService.precedenceOf(StatusLevel.ISSUE, issue.statusId());
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
        // Validated before the lookup on purpose: a rejected payload never reaches the
        // database. Consequence: an over-long description on a non-existent issue answers
        // 400, not 404. Documented in ADR 0018.
        descriptionPolicy.validate(description);
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

    /**
     * @param code a status code for the ISSUE level; validated by {@code StatusService} so an
     *             unknown value is a 400 naming the valid set, never a silent default
     */
    @Transactional
    public Optional<Issue> updateStatus(String id, String code) {
        Integer statusId = statusService.resolveForWrite(StatusLevel.ISSUE, code);
        return repository.findById(id).map(existing -> {
            existing.setStatusId(statusId);
            return repository.save(existing);
        });
    }

    @Transactional
    public Optional<Issue> updatePriority(String id, Priority priority) {
        return repository.findById(id).map(existing -> {
            existing.priority = priority.name();
            return repository.save(existing);
        });
    }

    @Transactional
    public Optional<Issue> moveToProject(String id, String newProjectId) {
        return repository.findById(id).map(existing -> {
            existing.projectId = newProjectId;
            return repository.save(existing);
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
