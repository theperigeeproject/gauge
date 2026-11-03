package com.sheahorn.gauge.resource;

import com.sheahorn.gauge.domain.CreateStatusRequest;
import com.sheahorn.gauge.domain.PatchStatusRequest;
import com.sheahorn.gauge.domain.StatusCategory;
import com.sheahorn.gauge.domain.StatusDefinition;
import com.sheahorn.gauge.domain.StatusLevel;
import com.sheahorn.gauge.service.StatusService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.Operation;

import java.util.Map;

/**
 * Admin-only management of the configurable status sets.
 *
 * <p>Reads live at {@code GET /api/statuses} (any authenticated caller); this resource is
 * the write side.
 *
 * <p><strong>Admin check is done in code, not with {@code @RolesAllowed}.</strong> Quarkus
 * evaluates {@code @RolesAllowed} against its own {@code SecurityIdentity}, which stays
 * anonymous for Bearer-token callers — {@code SecurityFilter} only sets the JAX-RS
 * {@link SecurityContext}. Using the annotation here would 403 every API client, including
 * the master key.
 */
@Path("/api/admin/statuses")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class StatusAdminResource {

    @Inject
    StatusService service;

    @Context
    SecurityContext securityContext;

    @Operation(
        operationId = "tracker_create_status",
        summary = "Create a status definition (admin only). level: ISSUE|TASKLIST|TASK. "
            + "category: ACTIVE|FINISHED|PARKED. code: 2-32 chars A-Z 0-9 _. color: #rrggbb. "
            + "rank defaults to last. showByDefault defaults to true."
    )
    @POST
    public Response create(CreateStatusRequest body) {
        if (!isAdmin()) {
            return forbidden();
        }
        StatusLevel level = StatusLevel.parse(body.level());
        if (level == null) {
            return badRequest("INVALID_LEVEL",
                "Invalid level '" + body.level() + "'. Valid values: ISSUE, TASKLIST, TASK.");
        }
        StatusCategory category = StatusCategory.parse(body.category());
        if (body.category() != null && category == null) {
            return badRequest("INVALID_CATEGORY",
                "Invalid category '" + body.category() + "'. Valid values: ACTIVE, FINISHED, PARKED.");
        }
        StatusDefinition created = service.create(level, body.code(), body.label(), category,
            body.color(), body.rank(), body.showByDefault());
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @Operation(
        operationId = "tracker_patch_status",
        summary = "Update a status definition (admin only). All fields optional. Changing 'code' "
            + "renames the status and rewrites every entity that uses it. The built-in start status "
            + "TODO cannot be renamed, deleted, or moved out of ACTIVE."
    )
    @PATCH
    @Path("/{id}")
    public Response patch(@PathParam("id") String id, PatchStatusRequest body) {
        if (!isAdmin()) {
            return forbidden();
        }
        return Response.ok(service.patch(id, body)).build();
    }

    @Operation(
        operationId = "tracker_delete_status",
        summary = "Delete a status definition (admin only). Built-in statuses cannot be deleted. "
            + "If the status is in use the call returns 409 STATUS_IN_USE unless ?reassignTo=<code> "
            + "is given, in which case all affected entities are rewritten to that status first. "
            + "A level must keep at least one ACTIVE status."
    )
    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") String id,
                           @QueryParam("reassignTo") String reassignTo) {
        if (!isAdmin()) {
            return forbidden();
        }
        service.delete(id, reassignTo);
        return Response.noContent().build();
    }

    private boolean isAdmin() {
        return securityContext != null && securityContext.isUserInRole("admin");
    }

    private Response forbidden() {
        return Response.status(Response.Status.FORBIDDEN)
            .entity(Map.of("error", "FORBIDDEN", "message", "Only admins can manage statuses."))
            .build();
    }

    private Response badRequest(String error, String message) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(Map.of("error", error, "message", message))
            .build();
    }
}
