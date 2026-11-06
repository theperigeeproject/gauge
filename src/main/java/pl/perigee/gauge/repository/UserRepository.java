package pl.perigee.gauge.repository;

import pl.perigee.gauge.domain.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class UserRepository {

    @Transactional
    public User save(User user) {
        Optional<User> existing = User.findByIdOptional(user.id);
        if (existing.isPresent()) {
            User managed = existing.get();
            managed.username = user.username;
            managed.password = user.password;
            managed.role = user.role;
            managed.active = user.active;
            managed.persist();
            return managed;
        }
        user.persist();
        return user;
    }

    public Optional<User> findById(String id) {
        return User.findByIdOptional(id);
    }

    public List<User> findAll() {
        return User.listAll();
    }

    @Transactional
    public void deleteById(String id) {
        User.deleteById(id);
    }

    public Optional<User> findByUsername(String username) {
        return User.find("username", username).firstResultOptional();
    }
}
