package pl.perigee.gauge.security;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class SecurityFilter implements ContainerRequestFilter {

    private static final Set<String> PUBLIC_PATHS = Set.of(
        "login.html",
        "login-failed.html",
        "logout"
    );

    @Inject
    ApiKeyResolver apiKeyResolver;

    @Inject
    CurrentApiKey currentApiKey;

    @ConfigProperty(name = "gauge.auth.enabled", defaultValue = "true")
    boolean authEnabled;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (!authEnabled) {
            return;
        }

        String path = requestContext.getUriInfo().getPath();

        // Normalize: RESTEasy Reactive may include a leading slash
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        // Public paths — no auth required
        if (PUBLIC_PATHS.contains(path)) {
            return;
        }

        // Already authenticated via session (form login) — allow through
        SecurityContext sec = requestContext.getSecurityContext();
        if (sec != null && sec.getUserPrincipal() != null) {
            return;
        }

        // Everything else requires a Bearer token
        String authHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of("error", "UNAUTHORIZED", "message", "Authorization: Bearer <token> required."))
                .build());
            return;
        }

        String providedKey = authHeader.substring(7).trim();
        Optional<ApiKey> resolved = apiKeyResolver.resolve(providedKey);

        if (resolved.isEmpty()) {
            requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of("error", "UNAUTHORIZED", "message", "Invalid token."))
                .build());
            return;
        }

        ApiKey apiKey = resolved.get();
        requestContext.setProperty("gauge.api-key", apiKey);
        currentApiKey.set(apiKey);

        // Set a SecurityContext so @RolesAllowed works for Bearer token users
        requestContext.setSecurityContext(new ApiKeySecurityContext(apiKey));
    }

    private static class ApiKeySecurityContext implements SecurityContext {

        private final ApiKey apiKey;

        ApiKeySecurityContext(ApiKey apiKey) {
            this.apiKey = apiKey;
        }

        @Override
        public Principal getUserPrincipal() {
            return () -> apiKey.username;
        }

        @Override
        public boolean isUserInRole(String role) {
            return apiKey.isAdmin() && "admin".equals(role);
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public String getAuthenticationScheme() {
            return "Bearer";
        }
    }
}
