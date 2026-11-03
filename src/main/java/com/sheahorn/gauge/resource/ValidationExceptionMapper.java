package com.sheahorn.gauge.resource;

import com.sheahorn.gauge.domain.ValidationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns {@link ValidationException} into a 400 with a stable shape:
 * {@code {"error": "...", "message": "..."}}. No stack traces, no silent success.
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
