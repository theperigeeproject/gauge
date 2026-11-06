package pl.perigee.gauge.config;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@Startup
@ApplicationScoped
public class MasterKeyGuard {

    private static final Logger LOG = Logger.getLogger(MasterKeyGuard.class);

    @ConfigProperty(name = "gauge.api-key.access")
    String masterKey;

    @ConfigProperty(name = "gauge.api-key.pepper")
    String pepper;

    @PostConstruct
    void check() {
        boolean fatal = false;

        if ("change-me".equals(masterKey)) {
            LOG.error("");
            LOG.error("Master key is still set to 'change-me'. Please change gauge.api-key.access in application.properties.");
            LOG.error("");
            fatal = true;
        }

        if ("change-me".equals(pepper)) {
            LOG.error("");
            LOG.error("Pepper is still set to 'change-me'. Please change gauge.api-key.pepper in application.properties.");
            LOG.error("");
            fatal = true;
        }

        if (fatal) {
            throw new IllegalStateException(
                "Default API key credentials detected. " +
                "Please change gauge.api-key.access and gauge.api-key.pepper in application.properties."
            );
        }
    }
}
