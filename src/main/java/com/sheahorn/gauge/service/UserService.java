package pl.perigee.gauge.service;

import pl.perigee.gauge.domain.User;
import pl.perigee.gauge.repository.UserRepository;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class UserService {

    @Inject
    UserRepository repository;

    @Transactional
    public User create(String username, String password, String role) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("password must not be blank");
        }
        String hashed = BcryptUtil.bcryptHash(password);
        User user = User.create(username, hashed, role);
        return repository.save(user);
    }

    public Optional<User> findById(String id) {
        return repository.findById(id);
    }

    public List<User> findAll() {
        return repository.findAll();
    }

    public Optional<User> findByUsername(String username) {
        return repository.findByUsername(username);
    }

    public long count() {
        return User.count();
    }

    @Transactional
    public Optional<User> changePassword(String id, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("password must not be blank");
        }
        return repository.findById(id).map(user -> {
            user.password = BcryptUtil.bcryptHash(newPassword);
            user.persist();
            return user;
        });
    }

    @Transactional
    public boolean deleteById(String id) {
        if (repository.findById(id).isEmpty()) {
            return false;
        }
        if (User.count() <= 1) {
            return false;
        }
        repository.deleteById(id);
        return true;
    }
}
