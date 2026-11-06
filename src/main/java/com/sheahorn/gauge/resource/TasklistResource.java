package pl.perigee.gauge.resource;

import pl.perigee.gauge.domain.CreateTaskRequest;
import pl.perigee.gauge.domain.LinkDecomposedTaskRequest;
import pl.perigee.gauge.domain.PatchTasklistRequest;
import pl.perigee.gauge.domain.ReorderTasksRequest;
import pl.perigee.gauge.domain.Task;
import pl.perigee.gauge.domain.Tasklist;
import pl.perigee.gauge.domain.UpdateTasklistStatusRequest;
import pl.perigee.gauge.security.ProjectAccessGuard;
import pl.perigee.gauge.service.TaskService;
import pl.perigee.gauge.service.TasklistService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;

import java.util.List;
import java.util.Map;

@Path("/api/tasklists")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TasklistResource {

    @Inject
    TasklistService service;

    @Inject
    TaskService taskService;

    @Inject
    ProjectAccessGuard accessGuard;

    @Operation(
        operationId = "tracker_list_tasklists",
        summary = "List all tasklists, optionally filtered by ?q=searchPhrase (case-insensitive match on title)"
    )
    @GET
    public Response list(@QueryParam("q") String q) {
        List<Tasklist> tasklists;
        if (q != null && !q.isBlank()) {
            tasklists = service.search(q);
        } else {
            tasklists = service.findAll();
        }
        tasklists = tasklists.stream()
            .filter(tl -> accessGuard.canAccessTasklist(tl.id()))
            .toList();
        return Response.ok(tasklists).build();
    }

    @Operation(
        operationId = "tracker_create_tasklist_task",
        summary = "Create a new task in a tasklist (auto-appended to end)"
    )
    @POST
    @Path("/{tasklistId}/tasks")
    public Response createTask(@PathParam("tasklistId") String tasklistId, CreateTaskRequest body) {
        if (!accessGuard.canWriteTasklist(tasklistId)) {
            return forbidden();
        }
        Task task = taskService.create(tasklistId, body.title(), body.description());
        return Response.status(Response.Status.CREATED).entity(task).build();
    }

    @Operation(
        operationId = "tracker_list_tasklist_tasks",
        summary = "List all tasks in a tasklist"
    )
    @GET
    @Path("/{tasklistId}/tasks")
    public Response listTasks(@PathParam("tasklistId") String tasklistId) {
        if (!accessGuard.canAccessTasklist(tasklistId)) {
            return forbidden();
        }
        return Response.ok(taskService.findByTasklistId(tasklistId)).build();
    }

    @Operation(
        operationId = "tracker_reorder_tasklist_tasks",
        summary = "Reorder tasks within a tasklist"
    )
    @PATCH
    @Path("/{tasklistId}/task-order")
    public Response reorderTasks(@PathParam("tasklistId") String tasklistId, ReorderTasksRequest body) {
        if (!accessGuard.canWriteTasklist(tasklistId)) {
            return forbidden();
        }
        taskService.reorder(tasklistId, body.taskIds());
        return Response.ok(taskService.findByTasklistId(tasklistId)).build();
    }

    @Operation(
        operationId = "tracker_get_tasklist",
        summary = "Get a tasklist by ID"
    )
    @GET
    @Path("/{tasklistId}")
    public Response get(@PathParam("tasklistId") String tasklistId) {
        if (!accessGuard.canAccessTasklist(tasklistId)) {
            return forbidden();
        }
        return service.findById(tasklistId)
            .map(tasklist -> Response.ok(tasklist).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @Operation(
        operationId = "tracker_patch_tasklist",
        summary = "Edit simple field: title"
    )
    @PATCH
    @Path("/{tasklistId}")
    public Response patch(@PathParam("tasklistId") String tasklistId, PatchTasklistRequest body) {
        if (!accessGuard.canWriteTasklist(tasklistId)) {
            return forbidden();
        }
        return service.patch(tasklistId, body.title())
            .map(tasklist -> Response.ok(tasklist).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @Operation(
        operationId = "tracker_update_tasklist_status",
        summary = "Update the status of a tasklist"
    )
    @PATCH
    @Path("/{tasklistId}/status")
    public Response updateStatus(@PathParam("tasklistId") String tasklistId, UpdateTasklistStatusRequest body) {
        if (!accessGuard.canWriteTasklist(tasklistId)) {
            return forbidden();
        }
        return service.updateStatus(tasklistId, body.status())
            .map(tasklist -> Response.ok(tasklist).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @Operation(
        operationId = "tracker_link_decomposed_task",
        summary = "Set, change, or unset the task this tasklist decomposes"
    )
    @PATCH
    @Path("/{tasklistId}/decomposes-task")
    public Response linkDecomposedTask(@PathParam("tasklistId") String tasklistId, LinkDecomposedTaskRequest body) {
        if (!accessGuard.canWriteTasklist(tasklistId)) {
            return forbidden();
        }
        return service.updateDecomposesTask(tasklistId, body.taskId())
            .map(tasklist -> Response.ok(tasklist).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @Operation(
        operationId = "tracker_delete_tasklist",
        summary = "Delete a tasklist. Requires ?cascade=true if tasks exist."
    )
    @DELETE
    @Path("/{tasklistId}")
    public Response delete(@PathParam("tasklistId") String tasklistId, @QueryParam("cascade") boolean cascade) {
        if (!accessGuard.canWriteTasklist(tasklistId)) {
            return forbidden();
        }
        if (!cascade && service.hasChildren(tasklistId)) {
            return Response.status(Response.Status.CONFLICT)
                .entity(Map.of("error", "CASCADE_REQUIRED", "message", "This resource has descendants. Retry with cascade=true."))
                .build();
        }
        if (cascade) {
            return service.cascadeDelete(tasklistId)
                ? Response.noContent().build()
                : Response.status(Response.Status.NOT_FOUND).build();
        }
        return service.deleteById(tasklistId)
            ? Response.noContent().build()
            : Response.status(Response.Status.NOT_FOUND).build();
    }

    private Response forbidden() {
        return Response.status(Response.Status.FORBIDDEN)
            .entity(Map.of("error", "FORBIDDEN", "message", "This API key is not authorized to access this project."))
            .build();
    }
}
