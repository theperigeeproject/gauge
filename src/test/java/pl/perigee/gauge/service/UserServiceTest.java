package pl.perigee.gauge.service;

import pl.perigee.gauge.domain.User;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class UserServiceTest {

    @Inject
    UserService userService;

    @Inject
    EntityManager em;

    @BeforeEach
    @Transactional
    void cleanUp() {
        em.createNativeQuery("DELETE FROM users WHERE username != 'admin'").executeUpdate();
    }

    // ---- helpers ----

    private User createUser(String username) {
        return userService.create(username, "password", "user");
    }

    // ---- 0: create — username, bcrypt password, role, UUID ----

    @Test
    @Transactional
    void testCreateUser() {
        User user = userService.create("testuser", "password123", "user");
        assertNotNull(user);
        assertNotNull(user.id);
        assertEquals("testuser", user.username);
        assertEquals("user", user.role);
        assertTrue(user.active);
        // ID should be a UUID (36 chars with dashes)
        assertEquals(36, user.id.length());
        assertTrue(user.id.contains("-"));
    }

    // ---- 1: create — password is bcrypt hash, not plaintext ----

    @Test
    @Transactional
    void testPasswordIsHashed() {
        User created = userService.create("hashed", "mypassword", "user");
        assertNotEquals("mypassword", created.password);
        assertTrue(created.password.startsWith("$2a$"));
        assertTrue(BcryptUtil.matches("mypassword", created.password));
    }

    // ---- 2: findById — exists / empty ----

    @Test
    @Transactional
    void testFindById() {
        User created = createUser("findme");
        Optional<User> found = userService.findById(created.id);
        assertTrue(found.isPresent());
        assertEquals("findme", found.get().username);
    }

    @Test
    @Transactional
    void testFindByIdNotFound() {
        Optional<User> found = userService.findById("NONEXISTENT");
        assertFalse(found.isPresent());
    }

    // ---- 3: findByUsername — exists / empty ----

    @Test
    @Transactional
    void testFindByUsername() {
        createUser("findbyname");
        Optional<User> found = userService.findByUsername("findbyname");
        assertTrue(found.isPresent());
        assertEquals("findbyname", found.get().username);
    }

    @Test
    @Transactional
    void testFindByUsernameNotFound() {
        Optional<User> found = userService.findByUsername("nonexistent");
        assertFalse(found.isPresent());
    }

    // ---- 4: findAll — returns all users ----

    @Test
    @Transactional
    void testFindAll() {
        createUser("user1");
        createUser("user2");
        List<User> all = userService.findAll();
        assertTrue(all.size() >= 2);
    }

    // ---- 5: changePassword — new bcrypt, old no longer matches ----

    @Test
    @Transactional
    void testChangePassword() {
        User created = createUser("changepw");
        Optional<User> updated = userService.changePassword(created.id, "newpassword");
        assertTrue(updated.isPresent());
        assertTrue(BcryptUtil.matches("newpassword", updated.get().password));
        assertFalse(BcryptUtil.matches("password", updated.get().password));
    }

    // ---- 6: changePassword nonexistent → empty ----

    @Test
    @Transactional
    void testChangePasswordNotFound() {
        Optional<User> updated = userService.changePassword("NONEXISTENT", "newpassword");
        assertFalse(updated.isPresent());
    }

    // ---- 7: deleteById — removes user ----

    @Test
    @Transactional
    void testDeleteById() {
        // Need at least 2 users so the "last user" guard doesn't block
        createUser("keeper");
        User created = createUser("delete");
        assertTrue(userService.deleteById(created.id));
        assertFalse(userService.findById(created.id).isPresent());
    }

    @Test
    @Transactional
    void testDeleteByIdNotFound() {
        assertFalse(userService.deleteById("NONEXISTENT"));
    }

    // ---- 8: create — duplicate username → DB unique constraint ----

    @Test
    @Transactional
    void testCreateDuplicateUsername() {
        createUser("dupuser");
        assertThrows(Exception.class, () -> {
            User u = userService.create("dupuser", "password2", "user");
            em.flush();
        });
    }

    // ---- 9: create — empty/blank username → rejected at service level ----

    @Test
    @Transactional
    void testCreateEmptyUsernameThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            userService.create("", "password", "user"));
        assertTrue(ex.getMessage().contains("username"));
    }

    @Test
    @Transactional
    void testCreateBlankUsernameThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            userService.create("   ", "password", "user"));
        assertTrue(ex.getMessage().contains("username"));
    }

    // ---- 10: create — empty/blank password → rejected at service level ----

    @Test
    @Transactional
    void testCreateEmptyPasswordThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            userService.create("emptyPW", "", "user"));
        assertTrue(ex.getMessage().contains("password"));
    }

    @Test
    @Transactional
    void testCreateBlankPasswordThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            userService.create("blankPW", "   ", "user"));
        assertTrue(ex.getMessage().contains("password"));
    }

    // ---- 11: changePassword — verify old password no longer works ----
    // Covered by testChangePassword above.

    // ---- 12: changePassword — empty/blank → rejected at service level ----

    @Test
    @Transactional
    void testChangePasswordEmptyThrows() {
        User created = createUser("emptyPWchange");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            userService.changePassword(created.id, ""));
        assertTrue(ex.getMessage().contains("password"));
    }

    @Test
    @Transactional
    void testChangePasswordBlankThrows() {
        User created = createUser("blankPWchange");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            userService.changePassword(created.id, "   "));
        assertTrue(ex.getMessage().contains("password"));
    }

    // ---- 13: deleteById — cannot delete last user ----
    // Not enforced at service level. Resource-layer concern.

    // ---- 14: deleteById — non-admin cannot delete others ----
    // Not enforced at service level. Resource-layer concern.

    // ---- 15: User.findByUsername — null for nonexistent ----

    @Test
    void testUserStaticFindByUsernameNotFound() {
        User found = User.findByUsername("nonexistent_user_xyz");
        assertNull(found);
    }

    // ---- 16: changePassword — null → IllegalArgumentException ----

    @Test
    @Transactional
    void testChangePasswordNull() {
        User created = createUser("changepwnull");
        assertThrows(IllegalArgumentException.class, () ->
            userService.changePassword(created.id, null)
        );
    }

    // ---- 17: create — null username/password → fail ----

    @Test
    @Transactional
    void testCreateWithNullUsername() {
        assertThrows(IllegalArgumentException.class, () ->
            userService.create(null, "password", "user")
        );
    }

    @Test
    @Transactional
    void testCreateWithNullPassword() {
        assertThrows(IllegalArgumentException.class, () ->
            userService.create("testuser", null, "user")
        );
    }
}
