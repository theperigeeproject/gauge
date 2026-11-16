package pl.perigee.gauge.resource;

import pl.perigee.gauge.domain.StatusBase;
import pl.perigee.gauge.domain.StatusLevel;
import pl.perigee.gauge.service.StatusService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.jboss.logging.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The admin surface for statuses. ADR 0020 §11: <b>one resource with a level path segment</b>, not
 * four resources — the four levels differ in data, not in operations, and one level-to-table mapping
 * beats four copies of the same CRUD.
 *
 * <p><b>This class is a thin adapter and must stay one.</b> Every rule lives in {@link StatusService}:
 * duplicate detection, flag gating, the last-available invariant, the migration-status protections,
 * the orphan policies. If a rule appears to be needed here, it belongs in the service — a rule
 * enforced at this layer is a rule the UI path and the migration path do not get.
 *
 * <p><b>The wire speaks codes, never ids</b> (§2). No response body carries a status id and no
 * request accepts one. Translation happens in the service.
 *
 * <p><b>Every method checks {@link #isAdmin()} as its FIRST statement</b> (ADR 0019). The annotation
 * {@code @RolesAllowed} is banned here: {@code /api/*} is a permit policy, so Quarkus builds no
 * SecurityIdentity for Bearer callers and the annotation would 403 every API client, master key
 * included. The in-code guard fails <em>open</em> if forgotten, which is why each method has a test
 * proving both outcomes.
 */
@Path("/api/admin/statuses")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class StatusAdminResource {

    private static final Logger LOG = Logger.getLogger(StatusAdminResource.class);

    @Inject
    StatusService statusService;

    @Context
    SecurityContext securityContext;

    // ── Read ────────────────────────────────────────────────────────

    /**
     * Every status of this level, ordered by {@code precedenceValue}.
     *
     * <p>Reads {@code all()}, deliberately <b>not</b> {@code available()}: an admin has to be able to
     * see a status a feature flag has switched off, or a disabled row would be invisible and
     * unfixable. Flag-gated authoring is a write concern (§7).
     */
    @Operation(
        operationId = "tracker_list_statuses",
        summary = "List every status of a level, ordered by precedenceValue. Admin only."
    )
    @GET
    @Path("/{level}")
    public Response list(@PathParam("level") String level) {
        if (!isAdmin()) {
            return forbidden();
        }
        StatusLevel parsed = StatusLevel.fromWire(level);
        return Response.ok(statusService.all(parsed).stream()
                .map(StatusAdminResource::toDto)
                .toList())
            .build();
    }

    // ── Create ──────────────────────────────────────────────────────

    @Operation(
        operationId = "tracker_create_status",
        summary = "Create a status for a level. Body: code, precedenceValue, color, progressLevel, "
            + "attitudeType. progressLevel ∈ NASCENT/CREATED/ONGOING/HALTED/FINISHED/FINALIZED; "
            + "attitudeType ∈ REGULAR/EXCLUDED/ARCHIVE/TRASH/MIGRATION. Admin only."
    )
    @POST
    @Path("/{level}")
    public Response create(@PathParam("level") String level, Map<String, Object> body) {
        // FIRST statement (ADR 0019): a non-admin must get 403 even with a malformed body.
        if (!isAdmin()) {
            return forbidden();
        }
        StatusLevel parsed = StatusLevel.fromWire(level);
        StatusBase created = statusService.create(parsed, toSpec(body));
        LOG.infof("Admin created %s status '%s'", parsed.wire, created.code);
        return Response.status(Response.Status.CREATED).entity(toDto(created)).build();
    }

    // ── Update ──────────────────────────────────────────────────────

    /**
     * Updates a status, identified by its current code in the path. A different {@code code} in the
     * body renames it — which is free, because entities reference the id (§2).
     */
    @Operation(
        operationId = "tracker_update_status",
        summary = "Update a status identified by its current code. A changed code renames it. Admin only."
    )
    @PATCH
    @Path("/{level}/{code}")
    public Response update(@PathParam("level") String level,
                           @PathParam("code") String code,
                           Map<String, Object> body) {
        if (!isAdmin()) {
            return forbidden();
        }
        StatusLevel parsed = StatusLevel.fromWire(level);
        StatusBase updated = statusService.update(parsed, code, toSpec(body));
        LOG.infof("Admin updated %s status '%s'", parsed.wire, updated.code);
        return Response.ok(toDto(updated)).build();
    }

    // ── Delete ──────────────────────────────────────────────────────

    /**
     * Removes a status, applying the caller's orphan policy (§10).
     *
     * <p>No policy and referencing rows → {@code 409 STATUS_IN_USE}. No policy and no references →
     * the delete proceeds, because there is nothing to decide.
     */
    @Operation(
        operationId = "tracker_delete_status",
        summary = "Delete a status. Requires ?policy=prevent|remove|migrate when rows reference it; "
            + "migrate also requires ?migrateTo=<code>. Admin only."
    )
    @DELETE
    @Path("/{level}/{code}")
    public Response delete(@PathParam("level") String level,
                           @PathParam("code") String code,
                           @QueryParam("policy") String policy,
                           @QueryParam("migrateTo") String migrateTo) {
        if (!isAdmin()) {
            return forbidden();
        }
        StatusLevel parsed = StatusLevel.fromWire(level);
        statusService.delete(parsed, code, StatusService.RemovalPolicy.fromWire(policy), migrateTo);
        LOG.infof("Admin removed %s status '%s'", parsed.wire, code);
        return Response.noContent().build();
    }

    // ── Mapping ─────────────────────────────────────────────────────

    /**
     * The wire shape. Note what is absent: the id. §2 makes it internal — a caller never learns it,
     * so a caller can never depend on one.
     */
    private static Map<String, Object> toDto(StatusBase status) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("code", status.code);
        dto.put("precedenceValue", status.precedenceValue);
        dto.put("color", status.color);
        dto.put("progressLevel", status.progressLevel);
        dto.put("attitudeType", status.attitudeType);
        return dto;
    }

    private static StatusService.StatusSpec toSpec(Map<String, Object> body) {
        Map<String, Object> safe = body == null ? Map.of() : body;
        return new StatusService.StatusSpec(
            asString(safe.get("code")),
            asInt(safe.get("precedenceValue")),
            asString(safe.get("color")),
            asString(safe.get("progressLevel")),
            asString(safe.get("attitudeType")));
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * A missing or non-numeric precedence becomes {@code 0}, which the service then rejects as a
     * duplicate far more often than not. Deliberately not a silent default to "append at the end" —
     * that would author ordering on the caller's behalf.
     */
    private static int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return 0;
    }

    private boolean isAdmin() {
        return securityContext != null && securityContext.isUserInRole("admin");
    }

    private Response forbidden() {
        return Response.status(Response.Status.FORBIDDEN)
            .entity(Map.of("error", "FORBIDDEN",
                "message", "Only admins can manage statuses."))
            .build();
    }
}
