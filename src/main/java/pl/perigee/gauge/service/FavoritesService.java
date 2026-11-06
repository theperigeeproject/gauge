package pl.perigee.gauge.service;

import pl.perigee.gauge.domain.UserFavorite;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class FavoritesService {

    public List<String> list(String userId) {
        return UserFavorite.findByUserId(userId).stream()
                .map(f -> f.projectId)
                .collect(Collectors.toList());
    }

    @Transactional
    public void add(String userId, String projectId) {
        List<UserFavorite> existing = UserFavorite.list("userId = ?1 and projectId = ?2", userId, projectId);
        if (existing.isEmpty()) {
            UserFavorite fav = new UserFavorite(userId, projectId);
            fav.persist();
        }
    }

    @Transactional
    public void remove(String userId, String projectId) {
        UserFavorite.deleteByUserIdAndProjectId(userId, projectId);
    }

    @Transactional
    public void reset(String userId) {
        UserFavorite.deleteByUserId(userId);
    }
}
