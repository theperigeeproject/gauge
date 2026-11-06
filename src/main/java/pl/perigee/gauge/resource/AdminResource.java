package pl.perigee.gauge.resource;

import pl.perigee.gauge.service.IdMigrationService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;

import java.util.Map;

@Path("/api/admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class AdminResource {

    @Inject
    IdMigrationService migrationService;

    @Operation(
        operationId = "tracker_migrate_ids",
        summary = "Migrate all UUID IDs to sequential hex-8 IDs. Requires ?confirm=true. BACK UP YOUR DB FIRST."
    )
    @POST
    @Path("/migrate-ids")
    public Response migrateIds(@QueryParam("confirm") boolean confirm) {
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
}
