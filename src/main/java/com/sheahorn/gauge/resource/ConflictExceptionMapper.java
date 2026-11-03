package com.sheahorn.gauge.resource;

import com.sheahorn.gauge.domain.ConflictException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.LinkedHashMap;
import java.util.Map;

/** {@link ConflictException} → 409 with the same body shape as validation errors. */
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
