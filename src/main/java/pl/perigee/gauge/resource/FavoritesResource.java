package pl.perigee.gauge.resource;

import pl.perigee.gauge.service.FavoritesService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.List;
import java.util.Map;

@Path("/api/favorites")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FavoritesResource {

    @Inject
    FavoritesService service;

    @Context
    SecurityContext securityContext;

    @GET
    public Response list() {
        String username = getUsername();
        if (username == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        List<String> projectIds = service.list(username);
        return Response.ok(projectIds).build();
    }

    @POST
    @Path("/{projectId}")
    public Response add(@PathParam("projectId") String projectId) {
        String username = getUsername();
        if (username == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        service.add(username, projectId);
        return Response.status(Response.Status.CREATED).entity(Map.of("status", "ok")).build();
    }

    @DELETE
    @Path("/{projectId}")
    public Response remove(@PathParam("projectId") String projectId) {
        String username = getUsername();
        if (username == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        service.remove(username, projectId);
        return Response.noContent().build();
    }

    @DELETE
    public Response reset() {
        String username = getUsername();
        if (username == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        service.reset(username);
        return Response.noContent().build();
    }

    private String getUsername() {
        if (securityContext != null && securityContext.getUserPrincipal() != null) {
            return securityContext.getUserPrincipal().getName();
        }
        return null;
    }
}
