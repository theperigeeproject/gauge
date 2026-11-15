package pl.perigee.gauge.resource;

import pl.perigee.gauge.domain.ValidationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns {@link ValidationException} into a 400 with a stable shape:
 * {@code {"error": "...", "message": "..."}}. No stack traces, no silent success.
 *
 * <p>The body is a {@link LinkedHashMap} so the key order is deterministic — clients (and
 * tests) can rely on it.
 */
@Provider
public class ValidationExceptionMapper implements ExceptionMapper<ValidationException> {

    @Override
    public Response toResponse(ValidationException exception) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", exception.error());
        body.put("message", exception.getMessage());
        return Response.status(Response.Status.BAD_REQUEST).entity(body).build();
    }
}
