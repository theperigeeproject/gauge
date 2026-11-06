package pl.perigee.gauge.resource;

import pl.perigee.gauge.domain.CreateProjectRequest;
import pl.perigee.gauge.domain.CreateIssueRequest;
import pl.perigee.gauge.domain.Issue;
import pl.perigee.gauge.domain.IssueStatus;
import pl.perigee.gauge.domain.PatchProjectRequest;
import pl.perigee.gauge.domain.Priority;
import pl.perigee.gauge.domain.Project;
import pl.perigee.gauge.domain.ProjectAnalysisResult;
import pl.perigee.gauge.domain.ReparentProjectRequest;
import pl.perigee.gauge.domain.SortOption;
import pl.perigee.gauge.security.ProjectAccessGuard;
import pl.perigee.gauge.service.IssueService;
import pl.perigee.gauge.service.ProjectAnalysisService;
import pl.perigee.gauge.service.ProjectService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.openapi.annotations.Operation;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Path("/api/projects")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProjectResource {

    @Inject
    ProjectService service;

    @Inject
    IssueService issueService;

    @Inject
    ProjectAnalysisService analysisService;

    @Inject
    ProjectAccessGuard accessGuard;

    @Context
    SecurityContext securityContext;

    @Operation(
        operationId = "tracker_create_project_issue",
        summary = "Create a new issue in a project"
    )
    @POST
    @Path("/{projectId}/issues")
    public Response createIssue(@PathParam("projectId") String projectId, CreateIssueRequest body) {
        if (!accessGuard.canWriteProject(projectId)) {
            return forbidden();
        }
        Priority priority = body.priority() != null ? body.priority() : Priority.NORMAL;
        Issue issue = issueService.create(projectId, body.title(), body.description(), priority);
        return Response.status(Response.Status.CREATED).entity(issue).build();
    }

    @Operation(
        operationId = "tracker_list_project_issues",
        summary = "List all issues in a project. Use ?sort=, ?priority=, ?status= for sorting/filtering"
    )
    @GET
    @Path("/{projectId}/issues")
    public Response listIssues(
        @PathParam("projectId") String projectId,
        @QueryParam("sort") String sort,
        @QueryParam("priority") String priority,
        @QueryParam("status") String status
    ) {
        if (!accessGuard.canAccessProject(projectId)) {
            return forbidden();
        }
        SortOption sortOption = null;
        if (sort != null && !sort.isBlank()) {
            try {
                sortOption = SortOption.valueOf(sort.toUpperCase());
            } catch (IllegalArgumentException e) {
                sortOption = SortOption.PRIORITY_STATUS_NAME;
            }
        }

        Set<Priority> priorities = null;
        if (priority != null && !priority.isBlank()) {
            priorities = Arrays.stream(priority.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try { return Priority.valueOf(s.toUpperCase()); }
                    catch (IllegalArgumentException e) { return null; }
                })
                .filter(p -> p != null)
                .collect(Collectors.toSet());
        }

        Set<IssueStatus> statuses = null;
        if (status != null && !status.isBlank()) {
            statuses = Arrays.stream(status.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try { return IssueStatus.valueOf(s.toUpperCase()); }
                    catch (IllegalArgumentException e) { return null; }
                })
                .filter(s -> s != null)
                .collect(Collectors.toSet());
        }

        return Response.ok(issueService.findByProjectIdSortedAndFiltered(projectId, sortOption, priorities, statuses)).build();
    }

    @Operation(
        operationId = "tracker_create_project",
        summary = "Create a new project"
    )
    @POST
    public Response create(CreateProjectRequest body) {
        if (!accessGuard.canCreateProject(body.parentId())) {
            return forbidden();
        }
        Project project = service.create(body.name(), body.description(), body.parentId());
        return Response.status(Response.Status.CREATED).entity(project).build();
    }

    @Operation(
        operationId = "tracker_list_projects",
        summary = "List projects. Use ?parentId=null for root, ?parentId=X for subprojects, ?q=phrase for search, ?flat=true for all, ?ids=a,b,c for specific IDs"
    )
    @GET
    public Response list(
        @QueryParam("parentId") String parentId,
        @QueryParam("q") String q,
        @QueryParam("flat") boolean flat,
        @QueryParam("ids") String ids
    ) {
        List<Project> projects;
        if (ids != null && !ids.isBlank()) {
            projects = service.findByIds(List.of(ids.split(",")));
        } else if (q != null && !q.isBlank()) {
            projects = service.search(q);
        } else if (flat) {
            projects = service.findAll();
        } else if (parentId == null || "null".equals(parentId)) {
            projects = service.findRootProjects();
        } else {
            projects = service.findByParentId(parentId);
        }
        // Filter out projects the key cannot access
        projects = projects.stream()
            .filter(p -> accessGuard.canAccessProject(p.id()))
            .toList();
        return Response.ok(projects).build();
    }

    @Operation(
        operationId = "tracker_get_project",
        summary = "Get a project by ID"
    )
    @GET
    @Path("/{projectId}")
    public Response get(@PathParam("projectId") String projectId) {
        if (!accessGuard.canAccessProject(projectId)) {
            return forbidden();
        }
        return service.findById(projectId)
            .map(project -> Response.ok(project).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @Operation(
        operationId = "tracker_get_project_ancestors",
        summary = "Get the ancestor chain from this project up to its root project"
    )
    @GET
    @Path("/{projectId}/ancestors")
    public Response ancestors(@PathParam("projectId") String projectId) {
        if (!accessGuard.canAccessProject(projectId)) {
            return forbidden();
        }
        if (service.findById(projectId).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(service.getAncestors(projectId)).build();
    }

    @Operation(
        operationId = "tracker_patch_project",
        summary = "Edit simple fields: name, description, removalLock (admin only for lock)"
    )
    @PATCH
    @Path("/{projectId}")
    public Response patch(@PathParam("projectId") String projectId, PatchProjectRequest body) {
        if (!accessGuard.canWriteProject(projectId)) {
            return forbidden();
        }
        // Only admin can set removalLock
        String removalLock = body.removalLock();
        if (removalLock != null && !isAdmin()) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(Map.of("error", "FORBIDDEN", "message", "Only admins can change the removal lock."))
                .build();
        }
        return service.patch(projectId, body.name(), body.description(), removalLock)
            .map(project -> Response.ok(project).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @Operation(
        operationId = "tracker_reparent_project",
        summary = "Change a project's parent, or make it root"
    )
    @PATCH
    @Path("/{projectId}/parent")
    public Response reparent(@PathParam("projectId") String projectId, ReparentProjectRequest body) {
        if (!accessGuard.canWriteProject(projectId)) {
            return forbidden();
        }
        if (body.parentId() != null && !accessGuard.canWriteProject(body.parentId())) {
            return forbidden();
        }
        return service.reparent(projectId, body.parentId())
            .map(project -> Response.ok(project).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @Operation(
        operationId = "tracker_analyze_projects",
        summary = "Get analysis stats for all projects (recursive aggregation)"
    )
    @GET
    @Path("/analysis")
    public Response analyze() {
        Map<String, ProjectAnalysisResult> all = analysisService.analyzeAll();
        Map<String, ProjectAnalysisResult> filtered = new java.util.HashMap<>();
        for (var entry : all.entrySet()) {
            if (accessGuard.canAccessProject(entry.getKey())) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return Response.ok(filtered).build();
    }

    @Operation(
        operationId = "tracker_delete_project",
        summary = "Delete a project. Requires ?cascade=true if children exist. Refuses if removalLock is \"locked\"."
    )
    @DELETE
    @Path("/{projectId}")
    public Response delete(@PathParam("projectId") String projectId, @QueryParam("cascade") boolean cascade) {
        if (!accessGuard.canWriteProject(projectId)) {
            return forbidden();
        }
        if (service.isLocked(projectId)) {
            return Response.status(Response.Status.CONFLICT)
                .entity(Map.of("error", "LOCKED", "message", "This project is locked against removal."))
                .build();
        }
        if (!cascade && service.hasChildren(projectId)) {
            return Response.status(Response.Status.CONFLICT)
                .entity(Map.of("error", "CASCADE_REQUIRED", "message", "This resource has descendants. Retry with cascade=true."))
                .build();
        }
        if (cascade) {
            return service.cascadeDelete(projectId)
                ? Response.noContent().build()
                : Response.status(Response.Status.NOT_FOUND).build();
        }
        return service.deleteById(projectId)
            ? Response.noContent().build()
            : Response.status(Response.Status.NOT_FOUND).build();
    }

    private boolean isAdmin() {
        return securityContext != null && securityContext.isUserInRole("admin");
    }

    private Response forbidden() {
        return Response.status(Response.Status.FORBIDDEN)
            .entity(Map.of("error", "FORBIDDEN", "message", "This API key is not authorized to access this project."))
            .build();
    }
}
