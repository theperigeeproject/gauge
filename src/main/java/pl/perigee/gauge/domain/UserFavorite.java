package pl.perigee.gauge.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.List;

@Entity
@Table(name = "user_favorites")
@IdClass(UserFavorite.UserFavoriteId.class)
public class UserFavorite extends PanacheEntityBase {

    @Id
    @Column(name = "user_id", nullable = false, length = 128)
    public String userId;

    @Id
    @Column(name = "project_id", nullable = false, length = 36)
    public String projectId;

    public UserFavorite() {
    }

    public UserFavorite(String userId, String projectId) {
        this.userId = userId;
        this.projectId = projectId;
    }

    public static List<UserFavorite> findByUserId(String userId) {
        return list("userId", userId);
    }

    public static long deleteByUserIdAndProjectId(String userId, String projectId) {
        return delete("userId = ?1 and projectId = ?2", userId, projectId);
    }

    public static long deleteByUserId(String userId) {
        return delete("userId", userId);
    }

    public static class UserFavoriteId implements Serializable {
        public String userId;
        public String projectId;

        public UserFavoriteId() {
        }

        public UserFavoriteId(String userId, String projectId) {
            this.userId = userId;
            this.projectId = projectId;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof UserFavoriteId)) return false;
            UserFavoriteId other = (UserFavoriteId) o;
            return userId.equals(other.userId) && projectId.equals(other.projectId);
        }

        @Override
        public int hashCode() {
            return userId.hashCode() * 31 + projectId.hashCode();
        }
    }
}
