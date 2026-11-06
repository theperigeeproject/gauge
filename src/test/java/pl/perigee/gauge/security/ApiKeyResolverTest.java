package pl.perigee.gauge.security;

import pl.perigee.gauge.domain.User;
import pl.perigee.gauge.service.ApiKeyService;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ApiKeyResolverTest {

    @Inject
    ApiKeyResolver apiKeyResolver;

    @Inject
    ApiKeyService apiKeyService;

    @BeforeEach
    @Transactional
    void cleanUp() {
        pl.perigee.gauge.domain.ApiKey.findAll().stream().forEach(ak -> ak.delete());
        User.findAll().stream()
            .map(u -> (User) u)
            .filter(u -> !"admin".equals(u.username))
            .forEach(u -> u.delete());
    }

    @Test
    void testResolveNullKey() {
        Optional<ApiKey> resolved = apiKeyResolver.resolve(null);
        assertFalse(resolved.isPresent());
    }

    @Test
    void testResolveBlankKey() {
        Optional<ApiKey> resolved = apiKeyResolver.resolve("   ");
        assertFalse(resolved.isPresent());
    }

    @Test
    void testResolveEmptyKey() {
        Optional<ApiKey> resolved = apiKeyResolver.resolve("");
        assertFalse(resolved.isPresent());
    }

    @Test
    void testResolveMasterKey() {
        Optional<ApiKey> resolved = apiKeyResolver.resolve("test-key");
        assertTrue(resolved.isPresent());
        assertEquals("master", resolved.get().username);
        assertTrue(resolved.get().isAdmin());
    }

    @Test
    @Transactional
    void testResolveUserApiKey() {
        User user = User.create("testuser", BcryptUtil.bcryptHash("password"), "user");
        user.persist();

        ApiKeyService.CreateResult result = apiKeyService.create(user.id, "Test Key", null, null);

        Optional<ApiKey> resolved = apiKeyResolver.resolve(result.rawKey);
        assertTrue(resolved.isPresent());
        assertEquals("testuser", resolved.get().username);
        assertEquals("user", resolved.get().role);
        assertFalse(resolved.get().isAdmin());
    }

    @Test
    void testResolveInvalidKey() {
        Optional<ApiKey> resolved = apiKeyResolver.resolve("invalid-key");
        assertFalse(resolved.isPresent());
    }

    @Test
    @Transactional
    void testResolveUserKeyWithAdminRole() {
        User admin = User.create("admin2", BcryptUtil.bcryptHash("password"), "admin");
        admin.persist();

        ApiKeyService.CreateResult result = apiKeyService.create(admin.id, "Admin Key", null, null);

        Optional<ApiKey> resolved = apiKeyResolver.resolve(result.rawKey);
        assertTrue(resolved.isPresent());
        assertTrue(resolved.get().isAdmin());
    }

    // ---- restricted project + access level ----

    @Test
    @Transactional
    void testResolveUserKeyWithNullRestrictedProject() {
        User user = User.create("testuser2", BcryptUtil.bcryptHash("password"), "user");
        user.persist();

        ApiKeyService.CreateResult result = apiKeyService.create(user.id, "Key", null, null);

        Optional<ApiKey> resolved = apiKeyResolver.resolve(result.rawKey);
        assertTrue(resolved.isPresent());
        assertFalse(resolved.get().isRestricted());
        assertNull(resolved.get().restrictedProjectId);
    }

    @Test
    @Transactional
    void testResolveUserKeyWithBlankRestrictedProject() {
        User user = User.create("testuser3", BcryptUtil.bcryptHash("password"), "user");
        user.persist();

        ApiKeyService.CreateResult result = apiKeyService.create(user.id, "Key", "   ", null);

        Optional<ApiKey> resolved = apiKeyResolver.resolve(result.rawKey);
        assertTrue(resolved.isPresent());
        assertFalse(resolved.get().isRestricted());
    }

    @Test
    @Transactional
    void testResolveUserKeyWithRestrictedProjectRead() {
        User user = User.create("testuser4", BcryptUtil.bcryptHash("password"), "user");
        user.persist();

        ApiKeyService.CreateResult result = apiKeyService.create(user.id, "Key", "PROJ0001", "READ");

        Optional<ApiKey> resolved = apiKeyResolver.resolve(result.rawKey);
        assertTrue(resolved.isPresent());
        assertTrue(resolved.get().isRestricted());
        assertEquals("PROJ0001", resolved.get().restrictedProjectId);
        assertEquals("READ", resolved.get().accessLevel);
        assertFalse(resolved.get().canWrite());
    }

    @Test
    @Transactional
    void testResolveUserKeyWithRestrictedProjectWrite() {
        User user = User.create("testuser5", BcryptUtil.bcryptHash("password"), "user");
        user.persist();

        ApiKeyService.CreateResult result = apiKeyService.create(user.id, "Key", "PROJ0001", "WRITE");

        Optional<ApiKey> resolved = apiKeyResolver.resolve(result.rawKey);
        assertTrue(resolved.isPresent());
        assertTrue(resolved.get().isRestricted());
        assertEquals("PROJ0001", resolved.get().restrictedProjectId);
        assertEquals("WRITE", resolved.get().accessLevel);
        assertTrue(resolved.get().canWrite());
    }

    @Test
    @Transactional
    void testResolveUserKeyWithRestrictedProjectDefaultAccessLevel() {
        User user = User.create("testuser6", BcryptUtil.bcryptHash("password"), "user");
        user.persist();

        ApiKeyService.CreateResult result = apiKeyService.create(user.id, "Key", "PROJ0001", null);

        Optional<ApiKey> resolved = apiKeyResolver.resolve(result.rawKey);
        assertTrue(resolved.isPresent());
        assertTrue(resolved.get().isRestricted());
        assertEquals("READ", resolved.get().accessLevel);
    }
}
