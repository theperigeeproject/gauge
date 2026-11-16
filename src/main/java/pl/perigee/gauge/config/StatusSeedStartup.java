package pl.perigee.gauge.config;

import pl.perigee.gauge.domain.AttitudeTypeDef;
import pl.perigee.gauge.domain.IssueStatusDef;
import pl.perigee.gauge.domain.ProgressLevelDef;
import pl.perigee.gauge.domain.ProjectStatusDef;
import pl.perigee.gauge.domain.TaskStatusDef;
import pl.perigee.gauge.domain.TasklistStatusDef;
import pl.perigee.gauge.repository.AttitudeTypeRepository;
import pl.perigee.gauge.repository.IssueStatusRepository;
import pl.perigee.gauge.repository.ProgressLevelRepository;
import pl.perigee.gauge.repository.ProjectStatusRepository;
import pl.perigee.gauge.repository.TaskStatusRepository;
import pl.perigee.gauge.repository.TasklistStatusRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Seeds the two fixed lookup tables and the four status tables on startup. ADR 0020 §13.
 *
 * <p><b>Insert-only.</b> A row is created only when its {@code code} (statuses) or {@code name}
 * (lookups) is absent. An existing row is never updated: once seeded, it belongs to the operator, and
 * a redeploy must not undo a colour change or a rename. That is also what makes a restart a no-op —
 * the log line reports zero inserted, not six.
 *
 * <p><b>Order matters.</b> Lookups are seeded before statuses, because a status row names its
 * progress level and attitude as plain strings. There is no foreign key to stop an inconsistent row,
 * so the ordering is the only thing preventing one.
 *
 * <p><b>Shape.</b> {@code @Startup} + {@code @PostConstruct} + {@code QuarkusTransaction.run}, the
 * pattern that actually works on Quarkus 3.x in this codebase. {@code @Observes StartupEvent} +
 * {@code @Transactional} does not — see issue 0000055D.
 *
 * <p>No feature flags live here. {@code enable.extended.progress.levels}, {@code enable.archive} and
 * {@code enable.trash} gate <em>authoring</em> of statuses, not seeding; they arrive with the admin
 * surface.
 */
@Startup
@ApplicationScoped
public class StatusSeedStartup {

    private static final Logger LOG = Logger.getLogger(StatusSeedStartup.class);

    // ── Fixed progress levels (ADR 0020 §3) ──────────────────────────
    public static final String NASCENT = "NASCENT";
    public static final String CREATED = "CREATED";
    public static final String ONGOING = "ONGOING";
    public static final String HALTED = "HALTED";
    public static final String FINISHED = "FINISHED";
    public static final String FINALIZED = "FINALIZED";

    // ── Fixed attitudes (ADR 0020 §3) ────────────────────────────────
    public static final String REGULAR = "REGULAR";
    public static final String EXCLUDED = "EXCLUDED";
    public static final String ARCHIVE = "ARCHIVE";
    public static final String TRASH = "TRASH";

    // Colours mirror the current palette so nothing shifts visually at switchover.
    private static final String C_TODO = "#60a5fa";
    private static final String C_DOING = "#fbbf24";
    private static final String C_DONE = "#4ade80";
    private static final String C_INACTIVE = "#b8b0dc";
    private static final String C_CANCELED = "#a78bfa";
    private static final String C_FAILED = "#f87171";
    private static final String C_OPENED = "#60a5fa";
    private static final String C_CLOSED = "#94a3b8";

    @Inject
    ProgressLevelRepository progressLevelRepository;

    @Inject
    AttitudeTypeRepository attitudeTypeRepository;

    @Inject
    ProjectStatusRepository projectStatusRepository;

    @Inject
    IssueStatusRepository issueStatusRepository;

    @Inject
    TasklistStatusRepository tasklistStatusRepository;

    @Inject
    TaskStatusRepository taskStatusRepository;

    @PostConstruct
    void init() {
        QuarkusTransaction.run(this::seed);
    }

    /**
     * Seeds every table. Public so a test can prove idempotency by running it a second time
     * against a populated database — the property that matters is "a second run changes nothing",
     * and it is only testable if the second run is reachable.
     */
    public void seed() {
        seedProgressLevels();
        seedAttitudes();
        seedProjectStatuses();
        seedIssueStatuses();
        seedTasklistStatuses();
        seedTaskStatuses();
    }

    private void seedProgressLevels() {
        int inserted = 0;
        inserted += seedLevel(NASCENT, 0);
        inserted += seedLevel(CREATED, 1);
        inserted += seedLevel(ONGOING, 2);
        inserted += seedLevel(HALTED, 3);
        inserted += seedLevel(FINISHED, 4);
        inserted += seedLevel(FINALIZED, 5);
        logTable("progress_levels", inserted, progressLevelRepository.count());
    }

    private int seedLevel(String name, int ordinal) {
        if (progressLevelRepository.findByName(name).isPresent()) {
            return 0;
        }
        progressLevelRepository.save(new ProgressLevelDef(name, ordinal));
        return 1;
    }

    private void seedAttitudes() {
        int inserted = 0;
        inserted += seedAttitude(REGULAR, 0);
        inserted += seedAttitude(EXCLUDED, 1);
        inserted += seedAttitude(ARCHIVE, 2);
        inserted += seedAttitude(TRASH, 3);
        logTable("attitude_types", inserted, attitudeTypeRepository.count());
    }

    private int seedAttitude(String name, int ordinal) {
        if (attitudeTypeRepository.findByName(name).isPresent()) {
            return 0;
        }
        attitudeTypeRepository.save(new AttitudeTypeDef(name, ordinal));
        return 1;
    }

    private void seedProjectStatuses() {
        int inserted = 0;
        if (projectStatusRepository.findByCode("OPENED").isEmpty()) {
            projectStatusRepository.save(new ProjectStatusDef("OPENED", 0, C_OPENED, ONGOING, REGULAR));
            inserted++;
        }
        if (projectStatusRepository.findByCode("CLOSED").isEmpty()) {
            projectStatusRepository.save(new ProjectStatusDef("CLOSED", 1, C_CLOSED, ONGOING, REGULAR));
            inserted++;
        }
        logTable("project_statuses", inserted, projectStatusRepository.count());
    }

    private void seedIssueStatuses() {
        int inserted = 0;
        if (issueStatusRepository.findByCode("TODO").isEmpty()) {
            issueStatusRepository.save(new IssueStatusDef("TODO", 0, C_TODO, CREATED, REGULAR));
            inserted++;
        }
        if (issueStatusRepository.findByCode("DOING").isEmpty()) {
            issueStatusRepository.save(new IssueStatusDef("DOING", 1, C_DOING, ONGOING, REGULAR));
            inserted++;
        }
        if (issueStatusRepository.findByCode("DONE").isEmpty()) {
            issueStatusRepository.save(new IssueStatusDef("DONE", 2, C_DONE, FINISHED, REGULAR));
            inserted++;
        }
        if (issueStatusRepository.findByCode("INACTIVE").isEmpty()) {
            issueStatusRepository.save(new IssueStatusDef("INACTIVE", 3, C_INACTIVE, HALTED, EXCLUDED));
            inserted++;
        }
        logTable("issue_statuses", inserted, issueStatusRepository.count());
    }

    private void seedTasklistStatuses() {
        int inserted = 0;
        if (tasklistStatusRepository.findByCode("TODO").isEmpty()) {
            tasklistStatusRepository.save(new TasklistStatusDef("TODO", 0, C_TODO, CREATED, REGULAR));
            inserted++;
        }
        if (tasklistStatusRepository.findByCode("DOING").isEmpty()) {
            tasklistStatusRepository.save(new TasklistStatusDef("DOING", 1, C_DOING, ONGOING, REGULAR));
            inserted++;
        }
        if (tasklistStatusRepository.findByCode("DONE").isEmpty()) {
            tasklistStatusRepository.save(new TasklistStatusDef("DONE", 2, C_DONE, FINISHED, REGULAR));
            inserted++;
        }
        if (tasklistStatusRepository.findByCode("CANCELED").isEmpty()) {
            tasklistStatusRepository.save(new TasklistStatusDef("CANCELED", 3, C_CANCELED, FINALIZED, REGULAR));
            inserted++;
        }
        logTable("tasklist_statuses", inserted, tasklistStatusRepository.count());
    }

    private void seedTaskStatuses() {
        int inserted = 0;
        if (taskStatusRepository.findByCode("TODO").isEmpty()) {
            taskStatusRepository.save(new TaskStatusDef("TODO", 0, C_TODO, CREATED, REGULAR));
            inserted++;
        }
        if (taskStatusRepository.findByCode("DOING").isEmpty()) {
            taskStatusRepository.save(new TaskStatusDef("DOING", 1, C_DOING, ONGOING, REGULAR));
            inserted++;
        }
        if (taskStatusRepository.findByCode("DONE").isEmpty()) {
            taskStatusRepository.save(new TaskStatusDef("DONE", 2, C_DONE, FINISHED, REGULAR));
            inserted++;
        }
        // FAILED is HALTED but REGULAR: a failed task is a blocker, not a parked one, so it keeps
        // bubbling priority. Only EXCLUDED statuses go quiet.
        if (taskStatusRepository.findByCode("FAILED").isEmpty()) {
            taskStatusRepository.save(new TaskStatusDef("FAILED", 3, C_FAILED, HALTED, REGULAR));
            inserted++;
        }
        if (taskStatusRepository.findByCode("CANCELED").isEmpty()) {
            taskStatusRepository.save(new TaskStatusDef("CANCELED", 4, C_CANCELED, FINALIZED, REGULAR));
            inserted++;
        }
        logTable("task_statuses", inserted, taskStatusRepository.count());
    }

    private static void logTable(String table, int inserted, long total) {
        if (inserted > 0) {
            LOG.infof("Seeded %d row(s) into %s (now %d)", inserted, table, total);
        } else {
            LOG.debugf("%s already seeded (%d rows)", table, total);
        }
    }
}
