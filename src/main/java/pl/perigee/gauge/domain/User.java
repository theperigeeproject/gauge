package pl.perigee.gauge.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User extends PanacheEntityBase {

    @Id
    @Column(length = 36)
    public String id;

    @Column(unique = true, nullable = false, length = 128)
    public String username;

    @JsonIgnore
    @Column(nullable = false, length = 256)
    public String password;

    @Column(nullable = false, length = 64)
    public String role;

    @Column(nullable = false)
    public boolean active = true;

    public static User create(String username, String hashedPassword, String role) {
        User u = new User();
        u.id = UUID.randomUUID().toString();
        u.username = username;
        u.password = hashedPassword;
        u.role = role;
        u.active = true;
        return u;
    }

    public static User findByUsername(String username) {
        return find("username", username).firstResult();
    }
}
