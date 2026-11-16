package pl.perigee.gauge.resource;

import pl.perigee.gauge.domain.ConflictException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns {@link ConflictException} into a 409 with the same body shape as
 * {@link ValidationExceptionMapper}: {@code {"error": "...", "message": "..."}}.
 *
 * <p>Status removal is the first producer: a status still referenced by entity rows cannot be
 * deleted unless the caller states an orphan policy, and that is a 409, not a 400 — the request is
 * correct, the state is not.
 */
@Provider
public class ConflictExceptionMapper implements ExceptionMapper<ConflictException> {

    @Override
    public Response toResponse(ConflictException exception) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", exception.error());
        body.put("message", exception.getMessage());
        return Response.status(Response.Status.CONFLICT).entity(body).build();
    }
}
