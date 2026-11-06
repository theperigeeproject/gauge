package pl.perigee.gauge.service;

import pl.perigee.gauge.domain.ApiKey;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ApiKeyService {

    private final String pepper;

    public ApiKeyService(
        @ConfigProperty(name = "gauge.api-key.pepper") String pepper
    ) {
        this.pepper = pepper;
    }

    public static class CreateResult {
        public final ApiKey apiKey;
        public final String rawKey;

        public CreateResult(ApiKey apiKey, String rawKey) {
            this.apiKey = apiKey;
            this.rawKey = rawKey;
        }
    }

    @Transactional
    public CreateResult create(String userId, String name, String restrictedProjectId, String accessLevel) {
        String rawKey = "atk-" + UUID.randomUUID().toString();
        String keyHash = hashKey(rawKey);
        ApiKey ak = ApiKey.create(userId, name, keyHash, restrictedProjectId, accessLevel);
        ak.persist();
        return new CreateResult(ak, rawKey);
    }

    public List<ApiKey> findByUserId(String userId) {
        return ApiKey.findByUserId(userId);
    }

    @Transactional
    public boolean deleteById(String id, String userId) {
        ApiKey ak = ApiKey.findById(id);
        if (ak != null && ak.userId.equals(userId)) {
            ak.delete();
            return true;
        }
        return false;
    }

    public String hashKey(String rawKey) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update((rawKey + pepper).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public ApiKey findByKeyHash(String keyHash) {
        return ApiKey.findByKeyHash(keyHash);
    }
}
