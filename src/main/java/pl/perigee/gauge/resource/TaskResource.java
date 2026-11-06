package pl.perigee.gauge.resource;

import pl.perigee.gauge.domain.PatchTaskRequest;
import pl.perigee.gauge.domain.Task;
import pl.perigee.gauge.domain.UpdateTaskStatusRequest;
import pl.perigee.gauge.security.ProjectAccessGuard;
import pl.perigee.gauge.service.TaskService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;

import java.util.Map;

@Path("/api/tasks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TaskResource {

    @Inject
    TaskService service;

    @Inject
    ProjectAccessGuard accessGuard;

    @Operation(
        operationId = "tracker_list_tasks",
        summary = "List all tasks, optionally filtered by ?q=searchPhrase (case-insensitive match on title/description)"
    )
    @GET
    public Response list(@QueryParam("q") String q) {
        java.util.List<Task> tasks;
        if (q != null && !q.isBlank()) {
            tasks = service.search(q);
        } else {
            tasks = service.findAll();
        }
        tasks = tasks.stream()
            .filter(t -> accessGuard.canAccessTask(t.id()))
            .toList();
        return Response.ok(tasks).build();
    }

    @Operation(
        operationId = "tracker_get_task",
        summary = "Get a task by ID"
    )
    @GET
    @Path("/{taskId}")
    public Response get(@PathParam("taskId") String taskId) {
        if (!accessGuard.canAccessTask(taskId)) {
            return forbidden();
        }
        return service.findById(taskId)
            .map(task -> Response.ok(task).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @Operation(
        operationId = "tracker_patch_task",
        summary = "Edit simple fields: title, description"
    )
    @PATCH
    @Path("/{taskId}")
    public Response patch(@PathParam("taskId") String taskId, PatchTaskRequest body) {
        if (!accessGuard.canWriteTask(taskId)) {
            return forbidden();
        }
        return service.patch(taskId, body.title(), body.description())
            .map(task -> Response.ok(task).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @Operation(
        operationId = "tracker_update_task_status",
        summary = "Update the status of a task"
    )
    @PATCH
    @Path("/{taskId}/status")
    public Response updateStatus(@PathParam("taskId") String taskId, UpdateTaskStatusRequest body) {
        if (!accessGuard.canWriteTask(taskId)) {
            return forbidden();
        }
        return service.updateStatus(taskId, body.status())
            .map(task -> Response.ok(task).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @Operation(
        operationId = "tracker_delete_task",
        summary = "Delete a task by ID"
    )
    @DELETE
    @Path("/{taskId}")
    public Response delete(@PathParam("taskId") String taskId) {
        if (!accessGuard.canWriteTask(taskId)) {
            return forbidden();
        }
        return service.deleteById(taskId)
            ? Response.noContent().build()
            : Response.status(Response.Status.NOT_FOUND).build();
    }

    private Response forbidden() {
        return Response.status(Response.Status.FORBIDDEN)
            .entity(Map.of("error", "FORBIDDEN", "message", "This API key is not authorized to access this project."))
            .build();
    }
}
