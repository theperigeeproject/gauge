package com.sheahorn.gauge.resource;

import com.sheahorn.gauge.domain.StatusDefinition;
import com.sheahorn.gauge.domain.StatusLevel;
import com.sheahorn.gauge.service.StatusService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;

import java.util.List;
import java.util.Map;

/**
 * Read-only status discovery. Available to every authenticated caller (UI and agents) so
 * nobody has to guess which status codes are legal at a given level.
 */
@Path("/api/statuses")
@Produces(MediaType.APPLICATION_JSON)
public class StatusResource {

    @Inject
    StatusService service;

    @Operation(
        operationId = "tracker_list_statuses",
        summary = "List statuses. Without ?level= returns all three levels keyed by level name "
            + "(ISSUE, TASKLIST, TASK). With ?level=ISSUE|TASKLIST|TASK returns a flat list for that level. "
            + "Each status has: id, code, label, level, category (ACTIVE|FINISHED|PARKED), rank, color, showByDefault, builtin."
    )
    @GET
    public Response list(@QueryParam("level") String level) {
        if (level == null || level.isBlank()) {
            return Response.ok(service.everything()).build();
        }
        StatusLevel parsed = StatusLevel.parse(level);
        if (parsed == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "INVALID_LEVEL",
                    "message", "Invalid level '" + level + "'. Valid values: ISSUE, TASKLIST, TASK."))
                .build();
        }
        List<StatusDefinition> definitions = service.list(parsed);
        return Response.ok(definitions).build();
    }
}
