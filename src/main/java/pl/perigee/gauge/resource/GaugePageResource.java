package pl.perigee.gauge.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.thymeleaf.TemplateEngine;

@Path("")
public class GaugePageResource {

    @Inject
    TemplateEngine templateEngine;

    @jakarta.ws.rs.core.Context
    SecurityContext securityContext;

    @GET
    @Path("/login.html")
    @Produces(MediaType.TEXT_HTML)
    public Response loginPage(@QueryParam("error") String error) {
        org.thymeleaf.context.Context ctx = new org.thymeleaf.context.Context();
        if (error != null && !error.isBlank()) {
            ctx.setVariable("error", error);
        }
        return Response.ok(templateEngine.process("login", ctx)).build();
    }

    @GET
    @Path("/login-failed.html")
    @Produces(MediaType.TEXT_HTML)
    public Response loginFailedPage(@QueryParam("reason") String reason) {
        org.thymeleaf.context.Context ctx = new org.thymeleaf.context.Context();
        String message = "Your session expired or credentials are invalid.";
        if ("deleted".equals(reason)) {
            message = "Your account has been deleted.";
        } else if ("expired".equals(reason)) {
            message = "Your session has expired.";
        }
        ctx.setVariable("message", message);
        return Response.ok(templateEngine.process("login-failed", ctx)).build();
    }

    @GET
    @Path("/")
    @Produces(MediaType.TEXT_HTML)
    public Response dashboard() {
        org.thymeleaf.context.Context ctx = new org.thymeleaf.context.Context();
        ctx.setVariable("isAdmin", isAdmin());
        return Response.ok(templateEngine.process("dashboard", ctx)).build();
    }

    @GET
    @Path("/ui/account")
    @Produces(MediaType.TEXT_HTML)
    public Response account() {
        org.thymeleaf.context.Context ctx = new org.thymeleaf.context.Context();
        ctx.setVariable("isAdmin", isAdmin());
        return Response.ok(templateEngine.process("account", ctx)).build();
    }

    @GET
    @Path("/ui/projects")
    @Produces(MediaType.TEXT_HTML)
    public Response projects() {
        org.thymeleaf.context.Context ctx = new org.thymeleaf.context.Context();
        ctx.setVariable("isAdmin", isAdmin());
        return Response.ok(templateEngine.process("projects", ctx)).build();
    }

    @GET
    @Path("/ui/projects/new")
    @Produces(MediaType.TEXT_HTML)
    public Response createProject(@QueryParam("parentId") String parentId) {
        org.thymeleaf.context.Context ctx = new org.thymeleaf.context.Context();
        ctx.setVariable("parentId", parentId);
        ctx.setVariable("isAdmin", isAdmin());
        return Response.ok(templateEngine.process("createProject", ctx)).build();
    }

    @GET
    @Path("/ui/projects/{id}")
    @Produces(MediaType.TEXT_HTML)
    public Response projectDetail(@PathParam("id") String id) {
        org.thymeleaf.context.Context ctx = new org.thymeleaf.context.Context();
        ctx.setVariable("projectId", id);
        ctx.setVariable("isAdmin", isAdmin());
        return Response.ok(templateEngine.process("projectDetail", ctx)).build();
    }

    @GET
    @Path("/ui/projects/{id}/issues/new")
    @Produces(MediaType.TEXT_HTML)
    public Response createIssue(@PathParam("id") String id) {
        org.thymeleaf.context.Context ctx = new org.thymeleaf.context.Context();
        ctx.setVariable("projectId", id);
        ctx.setVariable("isAdmin", isAdmin());
        return Response.ok(templateEngine.process("createIssue", ctx)).build();
    }

    @GET
    @Path("/ui/issues/{id}")
    @Produces(MediaType.TEXT_HTML)
    public Response issueDetail(@PathParam("id") String id) {
        org.thymeleaf.context.Context ctx = new org.thymeleaf.context.Context();
        ctx.setVariable("issueId", id);
        ctx.setVariable("isAdmin", isAdmin());
        return Response.ok(templateEngine.process("issueDetail", ctx)).build();
    }

    @GET
    @Path("/ui/issues/{id}/tasklists/new")
    @Produces(MediaType.TEXT_HTML)
    public Response createTasklist(@PathParam("id") String id) {
        org.thymeleaf.context.Context ctx = new org.thymeleaf.context.Context();
        ctx.setVariable("issueId", id);
        ctx.setVariable("isAdmin", isAdmin());
        return Response.ok(templateEngine.process("createTasklist", ctx)).build();
    }

    @GET
    @Path("/ui/tasklists/{id}")
    @Produces(MediaType.TEXT_HTML)
    public Response tasklistDetail(@PathParam("id") String id) {
        org.thymeleaf.context.Context ctx = new org.thymeleaf.context.Context();
        ctx.setVariable("tasklistId", id);
        ctx.setVariable("isAdmin", isAdmin());
        return Response.ok(templateEngine.process("tasklistDetail", ctx)).build();
    }

    @GET
    @Path("/ui/search")
    @Produces(MediaType.TEXT_HTML)
    public Response search() {
        org.thymeleaf.context.Context ctx = new org.thymeleaf.context.Context();
        ctx.setVariable("isAdmin", isAdmin());
        return Response.ok(templateEngine.process("search", ctx)).build();
    }

    @GET
    @Path("/ui/users")
    @RolesAllowed("admin")
    @Produces(MediaType.TEXT_HTML)
    public Response users() {
        org.thymeleaf.context.Context ctx = new org.thymeleaf.context.Context();
        ctx.setVariable("isAdmin", true);
        return Response.ok(templateEngine.process("users", ctx)).build();
    }

    @POST
    @Path("/logout")
    @Produces(MediaType.TEXT_HTML)
    public Response logout() {
        return Response.status(Response.Status.FOUND)
                .header("Location", "/login.html?error=Logged+out")
                .header("Set-Cookie", "gauge_session=; Max-Age=0; Path=/; HttpOnly; SameSite=Lax")
                .build();
    }

    private boolean isAdmin() {
        return securityContext != null && securityContext.isUserInRole("admin");
    }
}
