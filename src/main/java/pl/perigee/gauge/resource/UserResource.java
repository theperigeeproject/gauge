package pl.perigee.gauge.resource;

import pl.perigee.gauge.domain.User;
import pl.perigee.gauge.service.UserService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Optional;

@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource {

    private static final Logger LOG = Logger.getLogger(UserResource.class);

    @Inject
    UserService service;

    @Context
    SecurityContext securityContext;

    @GET
    @Path("/me")
    public Response me() {
        if (securityContext == null || securityContext.getUserPrincipal() == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        String username = securityContext.getUserPrincipal().getName();
        return service.findByUsername(username)
                .map(u -> Response.ok(Map.of("id", u.id, "username", u.username, "role", u.role)).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @GET
    public Response list() {
        // Admin check FIRST, before any body/param validation (ADR 0019).
        if (!isAdmin()) {
            return forbidden();
        }
        return Response.ok(service.findAll().stream()
                .map(u -> Map.of("id", u.id, "username", u.username, "role", u.role))
                .toList())
                .build();
    }

    @POST
    public Response create(Map<String, Object> body) {
        // Admin check FIRST — a non-admin must get 403 even with a malformed body,
        // exactly as it did when this was @RolesAllowed. Moving it below the null
        // checks would answer 400 and leak validation results to unauthorized callers.
        if (!isAdmin()) {
            return forbidden();
        }

        String username = (String) body.get("username");
        String password = (String) body.get("password");
        String role = (String) body.get("role");
        if (role == null || role.isBlank()) {
            role = "admin";
        }

        if (username == null || username.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "username is required"))
                    .build();
        }
        if (password == null || password.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "password is required"))
                    .build();
        }

        if (service.findByUsername(username).isPresent()) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", "username already taken"))
                    .build();
        }

        User user = service.create(username, password, role);

        LOG.infof("Created user: %s", username);
        return Response.status(Response.Status.CREATED)
                .entity(Map.of("id", user.id, "username", user.username, "role", user.role))
                .build();
    }

    @PATCH
    @Path("/{id}/password")
    public Response changePassword(@PathParam("id") String id, Map<String, Object> body) {
        String newPassword = (String) body.get("password");
        if (newPassword == null || newPassword.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "password is required"))
                    .build();
        }

        // Non-admin users can only change their own password
        if (!isAdmin()) {
            String currentUsername = securityContext != null && securityContext.getUserPrincipal() != null
                    ? securityContext.getUserPrincipal().getName() : null;
            Optional<User> target = service.findById(id);
            if (target.isEmpty() || !target.get().username.equals(currentUsername)) {
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(Map.of("error", "you can only change your own password"))
                        .build();
            }
        }

        return service.changePassword(id, newPassword)
                .map(u -> Response.ok(Map.of("id", u.id, "username", u.username)).build())
                .orElse(Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "user not found"))
                        .build());
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") String id) {
        // Admin check FIRST (ADR 0019). A non-admin gets 403 whether or not the id exists.
        if (!isAdmin()) {
            return forbidden();
        }
        Optional<User> user = service.findById(id);
        if (user.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "user not found"))
                    .build();
        }

        if (service.count() <= 1) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "cannot delete the last user"))
                    .build();
        }

        String username = user.get().username;
        service.deleteById(id);
        LOG.infof("Deleted user: %s", username);
        return Response.noContent().build();
    }

    private boolean isAdmin() {
        return securityContext != null && securityContext.isUserInRole("admin");
    }

    private Response forbidden() {
        return Response.status(Response.Status.FORBIDDEN)
                .entity(Map.of("error", "FORBIDDEN", "message", "Only admins can manage users."))
                .build();
    }
}
