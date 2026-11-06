package pl.perigee.gauge.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiKeyTest {

    @Test
    void testMasterKeyIsAdmin() {
        assertTrue(ApiKey.MASTER.isAdmin());
    }

    @Test
    void testMasterKeyIsNotRestricted() {
        assertFalse(ApiKey.MASTER.isRestricted());
    }

    @Test
    void testMasterKeyHasNullRestrictedProjectId() {
        assertNull(ApiKey.MASTER.restrictedProjectId);
    }

    @Test
    void testMasterKeyCanWrite() {
        assertTrue(ApiKey.MASTER.canWrite());
    }

    @Test
    void testUserKeyIsNotAdmin() {
        ApiKey key = new ApiKey("user1", "testuser", "user", null, null);
        assertFalse(key.isAdmin());
    }

    @Test
    void testAdminKeyIsAdmin() {
        ApiKey key = new ApiKey("user1", "adminuser", "admin", null, null);
        assertTrue(key.isAdmin());
    }

    @Test
    void testNullRestrictedProjectIdIsNotRestricted() {
        ApiKey key = new ApiKey("user1", "testuser", "user", null, null);
        assertFalse(key.isRestricted());
        assertNull(key.restrictedProjectId);
    }

    @Test
    void testBlankRestrictedProjectIdIsNotRestricted() {
        ApiKey key = new ApiKey("user1", "testuser", "user", "   ", null);
        assertFalse(key.isRestricted());
        assertNull(key.restrictedProjectId);
    }

    @Test
    void testRestrictedProjectIdIsRestricted() {
        ApiKey key = new ApiKey("user1", "testuser", "user", "PROJ0001", "READ");
        assertTrue(key.isRestricted());
        assertEquals("PROJ0001", key.restrictedProjectId);
        assertEquals("READ", key.accessLevel);
    }

    @Test
    void testRestrictedKeyDefaultsToReadWhenAccessLevelNull() {
        ApiKey key = new ApiKey("user1", "testuser", "user", "PROJ0001", null);
        assertTrue(key.isRestricted());
        assertEquals("READ", key.accessLevel);
    }

    @Test
    void testUnrestrictedKeyCanWrite() {
        ApiKey key = new ApiKey("user1", "testuser", "user", null, null);
        assertTrue(key.canWrite());
    }

    @Test
    void testReadKeyCannotWrite() {
        ApiKey key = new ApiKey("user1", "testuser", "user", "PROJ0001", "READ");
        assertFalse(key.canWrite());
    }

    @Test
    void testWriteKeyCanWrite() {
        ApiKey key = new ApiKey("user1", "testuser", "user", "PROJ0001", "WRITE");
        assertTrue(key.canWrite());
    }

    @Test
    void testUserIdAndUsername() {
        ApiKey key = new ApiKey("user1", "testuser", "user", null, null);
        assertEquals("user1", key.userId);
        assertEquals("testuser", key.username);
        assertEquals("user", key.role);
    }
}
