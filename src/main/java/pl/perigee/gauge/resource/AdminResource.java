package pl.perigee.gauge.resource;

import pl.perigee.gauge.service.IdMigrationService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.Operation;

import java.util.Map;

@Path("/api/admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminResource {

    @Inject
    IdMigrationService migrationService;

    @Context
    SecurityContext securityContext;

    @Operation(
        operationId = "tracker_migrate_ids",
        summary = "Migrate all UUID IDs to sequential hex-8 IDs. Requires ?confirm=true. BACK UP YOUR DB FIRST."
    )
    @POST
    @Path("/migrate-ids")
    public Response migrateIds(@QueryParam("confirm") boolean confirm) {
        // Admin check FIRST, before the confirm flag (ADR 0019). When this was
        // @RolesAllowed the check ran before the method body, so a non-admin never
        // learned whether the confirm flag was present.
        if (!isAdmin()) {
            return forbidden();
        }
        if (!confirm) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "CONFIRM_REQUIRED",
                    "message", "This is a destructive one-time operation. BACK UP your H2 database file first, then retry with ?confirm=true."))
                .build();
        }
        try {
            Map<String, Object> result = migrationService.migrate();
            return Response.ok(result).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", "MIGRATION_FAILED", "message", e.getMessage()))
                .build();
        }
    }

    private boolean isAdmin() {
        return securityContext != null && securityContext.isUserInRole("admin");
    }

    private Response forbidden() {
        return Response.status(Response.Status.FORBIDDEN)
            .entity(Map.of("error", "FORBIDDEN", "message", "Only admins can call this endpoint."))
            .build();
    }
}
