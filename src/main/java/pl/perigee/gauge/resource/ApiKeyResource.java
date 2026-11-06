package pl.perigee.gauge.resource;

import pl.perigee.gauge.domain.ApiKey;
import pl.perigee.gauge.security.ProjectAccessGuard;
import pl.perigee.gauge.service.ApiKeyService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.List;
import java.util.Map;

@Path("/api/apikeys")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ApiKeyResource {

    @Inject
    ApiKeyService service;

    @Inject
    ProjectAccessGuard accessGuard;

    @Context
    SecurityContext securityContext;

    @GET
    public Response list() {
        String userId = getCurrentUserId();
        if (userId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        List<ApiKey> keys = service.findByUserId(userId);
        var result = keys.stream()
                .map(k -> {
                    var m = new java.util.HashMap<String, Object>();
                    m.put("id", k.id);
                    m.put("name", k.name);
                    if (k.restrictedProjectId != null && !k.restrictedProjectId.isBlank()) {
                        m.put("restrictedProjectId", k.restrictedProjectId);
                        m.put("accessLevel", k.accessLevel != null ? k.accessLevel : "READ");
                    }
                    return m;
                })
                .toList();
        return Response.ok(result).build();
    }

    @POST
    public Response create(Map<String, Object> body) {
        String userId = getCurrentUserId();
        if (userId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        String name = (String) body.get("name");
        if (name == null || name.isBlank()) {
            name = "API Key";
        }
        String restrictedProjectId = (String) body.get("restrictedProjectId");
        String accessLevel = (String) body.get("accessLevel");
        if (restrictedProjectId != null && restrictedProjectId.isBlank()) {
            restrictedProjectId = null;
        }
        if (accessLevel == null || accessLevel.isBlank()) {
            accessLevel = "READ";
        }
        ApiKeyService.CreateResult result = service.create(userId, name, restrictedProjectId, accessLevel);
        var entity = new java.util.HashMap<String, Object>();
        entity.put("id", result.apiKey.id);
        entity.put("name", result.apiKey.name);
        entity.put("key", result.rawKey);
        if (restrictedProjectId != null) {
            entity.put("restrictedProjectId", restrictedProjectId);
            entity.put("accessLevel", accessLevel);
        }
        return Response.status(Response.Status.CREATED)
                .entity(entity)
                .build();
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") String id) {
        String userId = getCurrentUserId();
        if (userId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        if (service.deleteById(id, userId)) {
            return Response.noContent().build();
        }
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    private String getCurrentUserId() {
        if (securityContext == null || securityContext.getUserPrincipal() == null) {
            return null;
        }
        String username = securityContext.getUserPrincipal().getName();
        pl.perigee.gauge.domain.User user = pl.perigee.gauge.domain.User.findByUsername(username);
        return user != null ? user.id : null;
    }
}
