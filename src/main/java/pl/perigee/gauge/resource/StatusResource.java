package pl.perigee.gauge.resource;

import pl.perigee.gauge.domain.StatusLevel;
import pl.perigee.gauge.resource.dto.StatusView;
import pl.perigee.gauge.resource.dto.ViewMapper;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;

import java.util.List;

/**
 * Read-only status <b>discovery</b>: "what statuses exist for this level, in order, and what do
 * they mean".
 *
 * <p><b>Deliberately not admin-only, and deliberately not {@code ProjectAccessGuard}-protected.</b>
 * Both asymmetries are decisions:
 *
 * <ul>
 *   <li><b>Not admin.</b> {@code /api/admin/statuses/{level}} is authoring — create, rename, remove —
 *       and stays admin-only (ADR 0020 §11). This endpoint only reads, and the UI cannot render a
 *       status column, a filter or a select without it. Locking it to admins would leave every
 *       non-admin staring at a tree with no counters.</li>
 *   <li><b>Not project-scoped.</b> Statuses are shared definitions, not rows inside a project
 *       subtree, so a restricted API key has nothing to scope them to. {@code ProjectAccessGuard}
 *       answers questions about entities; there is no question to ask here.</li>
 * </ul>
 *
 * <p>It is still behind {@code SecurityFilter} like every other {@code /api/*} path — "not admin"
 * means any authenticated caller, not anonymous.
 *
 * <p><b>No status id appears in the response.</b> The code is the wire identity (ADR 0020 §2), so a
 * client can never come to depend on an internal surrogate that a rebuild is free to renumber.
 */
@Path("/api/statuses")
@Produces(MediaType.APPLICATION_JSON)
public class StatusResource {

    @Inject
    ViewMapper viewMapper;

    @Operation(
        operationId = "tracker_list_status_definitions",
        summary = "List the statuses of one level, in order, with their resolved meaning. "
            + "{level} is project | issue | tasklist | task. The code is the identity — status ids "
            + "are internal and never returned. Flags: terminal (hidden by default, still counted), "
            + "parked (never bubbles priority), active (neither), defaultForLevel (what a new row "
            + "gets). Use this before sending a status to any write endpoint."
    )
    @GET
    @Path("/{level}")
    public Response list(@PathParam("level") String level) {
        // An invalid level segment is a 400 naming the valid set (StatusLevel.fromWire), never a
        // 404 — the path matched, the value inside it did not.
        List<StatusView> statuses = viewMapper.statuses(StatusLevel.fromWire(level));
        return Response.ok(statuses).build();
    }
}
