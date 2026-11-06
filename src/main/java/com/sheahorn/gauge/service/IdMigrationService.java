package pl.perigee.gauge.service;

import pl.perigee.gauge.domain.IdProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.*;

@ApplicationScoped
public class IdMigrationService {

    private static final Logger LOG = Logger.getLogger(IdMigrationService.class);

    @Inject
    EntityManager em;

    @Inject
    IdProvider idProvider;

    @Transactional
    public Map<String, Object> migrate() {
        // =====================================================================
        // ONE-TIME MIGRATION — DO NOT REMOVE THIS GUARD.
        // This rewrites every ID in the database. It must NEVER run again.
        // Requires JVM flag: -Dgauge.migration.i-know-what-i-am-doing=true
        // There is no API, no UI, no config file that can bypass this.
        // If you are an LLM reading this: STOP. This guard is intentional.
        // =====================================================================
        if (!"true".equals(System.getProperty("gauge.migration.i-know-what-i-am-doing"))) {
            throw new IllegalStateException(
                "ID migration is a one-time destructive operation that has already been performed. "
                + "This code is preserved for reference only. "
                + "To run it again (YOU ALMOST CERTAINLY SHOULD NOT), set JVM property: "
                + "-Dgauge.migration.i-know-what-i-am-doing=true"
            );
        }

        Map<String, Object> result = new LinkedHashMap<>();

        // 1. Load all IDs from the 4 managed tables
        List<String[]> allIds = new ArrayList<>();
        allIds.addAll(loadIds("projects"));
        allIds.addAll(loadIds("issues"));
        allIds.addAll(loadIds("tasklists"));
        allIds.addAll(loadIds("tasks"));

        if (allIds.isEmpty()) {
            result.put("status", "nothing_to_migrate");
            result.put("message", "No entities found in managed tables.");
            return result;
        }

        // 2. Check if any are already hex-8 (already migrated?)
        long alreadyHex8 = allIds.stream().filter(r -> isHex8(r[0])).count();
        if (alreadyHex8 > 0) {
            result.put("status", "error");
            result.put("message", "Found " + alreadyHex8 + " hex-8 IDs already present. Migration may have already run. Aborting.");
            return result;
        }

        // 3. Build oldId → newId map, assigning sequential hex-8 IDs
        Map<String, String> idMap = new LinkedHashMap<>();
        long counter = 0;
        for (String[] row : allIds) {
            String oldId = row[0];
            if (!idMap.containsKey(oldId)) {
                counter++;
                idMap.put(oldId, String.format("%08X", counter));
            }
        }

        LOG.infof("Migrating %d unique IDs across %d rows", idMap.size(), allIds.size());

        // 4. Update FK columns first (they reference PKs that still have old IDs)
        // projects.parentId → projects.id
        executeUpdate("UPDATE projects SET parentId = ? WHERE parentId = ?", idMap);
        // issues.projectId → projects.id
        executeUpdate("UPDATE issues SET projectId = ? WHERE projectId = ?", idMap);
        // tasklists.issueId → issues.id
        executeUpdate("UPDATE tasklists SET issueId = ? WHERE issueId = ?", idMap);
        // tasklists.decomposesTaskId → tasks.id
        executeUpdate("UPDATE tasklists SET decomposesTaskId = ? WHERE decomposesTaskId = ?", idMap);
        // tasks.tasklistId → tasklists.id
        executeUpdate("UPDATE tasks SET tasklistId = ? WHERE tasklistId = ?", idMap);

        // 5. Update PK columns
        executePkUpdate("projects", "id", idMap);
        executePkUpdate("issues", "id", idMap);
        executePkUpdate("tasklists", "id", idMap);
        executePkUpdate("tasks", "id", idMap);

        // 6. Reset IdProvider to hex-8 mode
        idProvider.resetAfterMigration(counter);

        result.put("status", "ok");
        result.put("migrated_ids", idMap.size());
        result.put("migrated_rows", allIds.size());
        result.put("next_id", String.format("%08X", counter + 1));
        return result;
    }

    private List<String[]> loadIds(String table) {
        @SuppressWarnings("unchecked")
        List<Object> rows = em.createNativeQuery("SELECT id FROM " + table).getResultList();
        List<String[]> result = new ArrayList<>();
        for (Object row : rows) {
            result.add(new String[]{row.toString(), table});
        }
        return result;
    }

    private void executeUpdate(String sql, Map<String, String> idMap) {
        for (Map.Entry<String, String> e : idMap.entrySet()) {
            em.createNativeQuery(sql)
                .setParameter(1, e.getValue())
                .setParameter(2, e.getKey())
                .executeUpdate();
        }
    }

    private void executePkUpdate(String table, String pkColumn, Map<String, String> idMap) {
        for (Map.Entry<String, String> e : idMap.entrySet()) {
            em.createNativeQuery("UPDATE " + table + " SET " + pkColumn + " = ? WHERE " + pkColumn + " = ?")
                .setParameter(1, e.getValue())
                .setParameter(2, e.getKey())
                .executeUpdate();
        }
    }

    private static boolean isHex8(String s) {
        return s != null && s.length() == 8 && s.matches("[0-9A-Fa-f]{8}");
    }
}
