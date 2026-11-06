package pl.perigee.gauge.service;

import pl.perigee.gauge.domain.ApiKey;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ApiKeyServiceTest {

    @Inject
    ApiKeyService apiKeyService;

    @Inject
    EntityManager em;

    @BeforeEach
    @Transactional
    void cleanUp() {
        em.createNativeQuery("DELETE FROM apikeys").executeUpdate();
    }

    private ApiKeyService.CreateResult createKey(String userId, String name) {
        return apiKeyService.create(userId, name, null, null);
    }

    @Test
    @Transactional
    void testCreateApiKey() {
        ApiKeyService.CreateResult result = apiKeyService.create("USER001", "My API Key", null, null);
        assertNotNull(result);
        assertNotNull(result.apiKey);
        assertNotNull(result.rawKey);
        assertEquals("USER001", result.apiKey.userId);
        assertEquals("My API Key", result.apiKey.name);
        assertTrue(result.rawKey.startsWith("atk-"));
    }

    @Test
    @Transactional
    void testRawKeyFormat() {
        ApiKeyService.CreateResult result = createKey("USER001", "Key");
        assertTrue(result.rawKey.startsWith("atk-"));
        String uuidPart = result.rawKey.substring(4);
        assertEquals(36, uuidPart.length());
        assertTrue(uuidPart.contains("-"));
    }

    @Test
    @Transactional
    void testKeyHashDoesNotContainRawKey() {
        ApiKeyService.CreateResult result = createKey("USER001", "Test");
        assertFalse(result.apiKey.keyHash.contains(result.rawKey));
    }

    @Test
    void testHashKeyDeterministic() {
        String hash1 = apiKeyService.hashKey("test-key-1");
        String hash2 = apiKeyService.hashKey("test-key-1");
        String hash3 = apiKeyService.hashKey("test-key-2");
        assertEquals(hash1, hash2);
        assertNotEquals(hash1, hash3);
    }

    @Test
    void testHashKeyDifferentPepperDifferentHash() {
        ApiKeyService service1 = new ApiKeyService("pepper-alpha");
        ApiKeyService service2 = new ApiKeyService("pepper-beta");
        String hash1 = service1.hashKey("same-raw-key");
        String hash2 = service2.hashKey("same-raw-key");
        assertNotEquals(hash1, hash2);
    }

    @Test
    @Transactional
    void testFindByUserId() {
        createKey("USER001", "Key1");
        createKey("USER001", "Key2");
        createKey("USER002", "Key3");
        List<ApiKey> user1Keys = apiKeyService.findByUserId("USER001");
        assertEquals(2, user1Keys.size());
        List<ApiKey> user2Keys = apiKeyService.findByUserId("USER002");
        assertEquals(1, user2Keys.size());
    }

    @Test
    @Transactional
    void testFindByUserIdEmptyForUserWithNoKeys() {
        List<ApiKey> keys = apiKeyService.findByUserId("USER_NO_KEYS");
        assertTrue(keys.isEmpty());
    }

    @Test
    @Transactional
    void testFindByKeyHash() {
        ApiKeyService.CreateResult result = createKey("USER001", "Test");
        String keyHash = apiKeyService.hashKey(result.rawKey);
        ApiKey found = apiKeyService.findByKeyHash(keyHash);
        assertNotNull(found);
        assertEquals(result.apiKey.id, found.id);
    }

    @Test
    @Transactional
    void testFindByKeyHashNotFound() {
        ApiKey found = apiKeyService.findByKeyHash("nonexistent-hash");
        assertNull(found);
    }

    @Test
    @Transactional
    void testDeleteById() {
        ApiKeyService.CreateResult result = createKey("USER001", "To Delete");
        assertTrue(apiKeyService.deleteById(result.apiKey.id, "USER001"));
        assertNull(ApiKey.findById(result.apiKey.id));
    }

    @Test
    @Transactional
    void testDeleteByIdWrongUser() {
        ApiKeyService.CreateResult result = createKey("USER001", "My Key");
        assertFalse(apiKeyService.deleteById(result.apiKey.id, "USER002"));
        assertNotNull(ApiKey.findById(result.apiKey.id));
    }

    @Test
    @Transactional
    void testDeleteByIdNotFound() {
        assertFalse(apiKeyService.deleteById("NONEXISTENT", "USER001"));
    }

    @Test
    @Transactional
    void testCreateWithEmptyNamePassesThrough() {
        ApiKeyService.CreateResult result = apiKeyService.create("USER001", "", null, null);
        assertEquals("", result.apiKey.name);
    }

    @Test
    @Transactional
    void testCreateWithBlankNamePassesThrough() {
        ApiKeyService.CreateResult result = apiKeyService.create("USER001", "   ", null, null);
        assertEquals("   ", result.apiKey.name);
    }

    @Test
    void testHashKeyOutputIs64HexChars() {
        String hash = apiKeyService.hashKey("test");
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }

    @Test
    @Transactional
    void testCreateWithRestrictedProject() {
        ApiKeyService.CreateResult result = apiKeyService.create("USER001", "Key", "PROJ0001", "READ");
        assertEquals("PROJ0001", result.apiKey.restrictedProjectId);
        assertEquals("READ", result.apiKey.accessLevel);
    }

    @Test
    @Transactional
    void testCreateWithRestrictedProjectWrite() {
        ApiKeyService.CreateResult result = apiKeyService.create("USER001", "Key", "PROJ0001", "WRITE");
        assertEquals("PROJ0001", result.apiKey.restrictedProjectId);
        assertEquals("WRITE", result.apiKey.accessLevel);
    }

    @Test
    @Transactional
    void testRawKeyIsDifferentEachTime() {
        ApiKeyService.CreateResult r1 = createKey("USER001", "Key1");
        ApiKeyService.CreateResult r2 = createKey("USER001", "Key2");
        assertNotEquals(r1.rawKey, r2.rawKey);
        assertNotEquals(r1.apiKey.keyHash, r2.apiKey.keyHash);
    }
}
