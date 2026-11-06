package pl.perigee.gauge.domain;

import io.quarkus.arc.Arc;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Static holder to access the IdProvider singleton from entity static factory methods.
 */
@ApplicationScoped
class IdProviderHolder {

    private static volatile IdProvider cached;

    static IdProvider provider() {
        if (cached == null) {
            cached = Arc.container().instance(IdProvider.class).get();
        }
        return cached;
    }
}
