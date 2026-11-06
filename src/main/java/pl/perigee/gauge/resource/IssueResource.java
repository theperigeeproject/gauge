package pl.perigee.gauge.resource;

import pl.perigee.gauge.domain.CreateIssueRequest;
import pl.perigee.gauge.domain.CreateTasklistRequest;
import pl.perigee.gauge.domain.Issue;
import pl.perigee.gauge.domain.MoveIssueRequest;
import pl.perigee.gauge.domain.PatchIssueRequest;
import pl.perigee.gauge.domain.Tasklist;
import pl.perigee.gauge.domain.UpdateIssuePriorityRequest;
import pl.perigee.gauge.domain.UpdateIssueStatusRequest;
import pl.perigee.gauge.security.ProjectAccessGuard;
import pl.perigee.gauge.service.IssueService;
import pl.perigee.gauge.service.TasklistService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;

import java.util.Map;

@Path("/api/issues")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class IssueResource {

    @Inject
    IssueService service;

    @Inject
    TasklistService tasklistService;

    @Inject
    ProjectAccessGuard accessGuard;

    @Operation(
        operationId = "tracker_list_issues",
        summary = "List all issues, optionally filtered by ?q=searchPhrase (case-insensitive match on title/description)"
    )
    @GET
    public Response list(@QueryParam("q") String q) {
        java.util.List<Issue> issues;
        if (q != null && !q.isBlank()) {
            issues = service.search(q);
        } else {
            issues = service.findAll();
        }
        // Filter out issues the key cannot access
        issues = issues.stream()
            .filter(i -> accessGuard.canAccessIssue(i.id()))
            .toList();
        return Response.ok(issues).build();
    }

    @Operation(
        operationId = "tracker_create_issue_tasklist",
        summary = "Create a new tasklist in an issue"
    )
    @POST
    @Path("/{issueId}/tasklists")
    public Response createTasklist(@PathParam("issueId") String issueId, CreateTasklistRequest body) {
        if (!accessGuard.canWriteIssue(issueId)) {
            return forbidden();
        }
        Tasklist tasklist = tasklistService.create(issueId, body.title(), body.decomposesTaskId());
        return Response.status(Response.Status.CREATED).entity(tasklist).build();
    }

    @Operation(
        operationId = "tracker_list_issue_tasklists",
        summary = "List all tasklists in an issue"
    )
    @GET
    @Path("/{issueId}/tasklists")
    public Response listTasklists(@PathParam("issueId") String issueId) {
        if (!accessGuard.canAccessIssue(issueId)) {
            return forbidden();
        }
        return Response.ok(tasklistService.findByIssueId(issueId)).build();
    }

    @Operation(
        operationId = "tracker_get_issue",
        summary = "Get an issue by ID"
    )
    @GET
    @Path("/{issueId}")
    public Response get(@PathParam("issueId") String issueId) {
        if (!accessGuard.canAccessIssue(issueId)) {
            return forbidden();
        }
        return service.findById(issueId)
            .map(issue -> Response.ok(issue).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @Operation(
        operationId = "tracker_patch_issue",
        summary = "Edit simple fields: title, description"
    )
    @PATCH
    @Path("/{issueId}")
    public Response patch(@PathParam("issueId") String issueId, PatchIssueRequest body) {
        if (!accessGuard.canWriteIssue(issueId)) {
            return forbidden();
        }
        return service.patch(issueId, body.title(), body.description())
            .map(issue -> Response.ok(issue).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @Operation(
        operationId = "tracker_update_issue_status",
        summary = "Update the status of an issue"
    )
    @PATCH
    @Path("/{issueId}/status")
    public Response updateStatus(@PathParam("issueId") String issueId, UpdateIssueStatusRequest body) {
        if (!accessGuard.canWriteIssue(issueId)) {
            return forbidden();
        }
        return service.updateStatus(issueId, body.status())
            .map(issue -> Response.ok(issue).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @Operation(
        operationId = "tracker_update_issue_priority",
        summary = "Update the priority of an issue"
    )
    @PATCH
    @Path("/{issueId}/priority")
    public Response updatePriority(@PathParam("issueId") String issueId, UpdateIssuePriorityRequest body) {
        if (!accessGuard.canWriteIssue(issueId)) {
            return forbidden();
        }
        return service.updatePriority(issueId, body.priority())
            .map(issue -> Response.ok(issue).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @Operation(
        operationId = "tracker_move_issue",
        summary = "Move an issue to another project"
    )
    @PATCH
    @Path("/{issueId}/project")
    public Response moveToProject(@PathParam("issueId") String issueId, MoveIssueRequest body) {
        if (!accessGuard.canWriteIssue(issueId)) {
            return forbidden();
        }
        if (!accessGuard.canWriteProject(body.projectId())) {
            return forbidden();
        }
        return service.moveToProject(issueId, body.projectId())
            .map(issue -> Response.ok(issue).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @Operation(
        operationId = "tracker_delete_issue",
        summary = "Delete an issue. Requires ?cascade=true if tasklists exist."
    )
    @DELETE
    @Path("/{issueId}")
    public Response delete(@PathParam("issueId") String issueId, @QueryParam("cascade") boolean cascade) {
        if (!accessGuard.canWriteIssue(issueId)) {
            return forbidden();
        }
        if (!cascade && service.hasChildren(issueId)) {
            return Response.status(Response.Status.CONFLICT)
                .entity(Map.of("error", "CASCADE_REQUIRED", "message", "This resource has descendants. Retry with cascade=true."))
                .build();
        }
        if (cascade) {
            return service.cascadeDelete(issueId)
                ? Response.noContent().build()
                : Response.status(Response.Status.NOT_FOUND).build();
        }
        return service.deleteById(issueId)
            ? Response.noContent().build()
            : Response.status(Response.Status.NOT_FOUND).build();
    }

    private Response forbidden() {
        return Response.status(Response.Status.FORBIDDEN)
            .entity(Map.of("error", "FORBIDDEN", "message", "This API key is not authorized to access this project."))
            .build();
    }
}
