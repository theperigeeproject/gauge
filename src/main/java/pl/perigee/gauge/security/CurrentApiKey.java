package pl.perigee.gauge.security;

import jakarta.enterprise.context.RequestScoped;

/**
 * Request-scoped holder for the resolved API key, set by SecurityFilter.
 */
@RequestScoped
public class CurrentApiKey {

    private ApiKey apiKey;

    public ApiKey get() {
        return apiKey;
    }

    public void set(ApiKey apiKey) {
        this.apiKey = apiKey;
    }
}
