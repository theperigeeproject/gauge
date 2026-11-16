package pl.perigee.gauge.repository;

import pl.perigee.gauge.domain.IssueStatusDef;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Database access for issue statuses. The status service is the only caller — nothing outside the
 * status package touches these rows directly, which is what keeps the code-to-id translation in one
 * place (ADR 0020 §4).
 *
 * <p>Deliberately minimal. Later increments add what they actually need; an unused method is an
 * untested method.
 */
@ApplicationScoped
public class IssueStatusRepository {

    @Transactional
    public IssueStatusDef save(IssueStatusDef status) {
        Optional<IssueStatusDef> existing = IssueStatusDef.findByIdOptional(status.id);
        if (existing.isPresent()) {
            IssueStatusDef managed = existing.get();
            managed.code = status.code;
            managed.precedenceValue = status.precedenceValue;
            managed.color = status.color;
            managed.progressLevel = status.progressLevel;
            managed.attitudeType = status.attitudeType;
            managed.persist();
            return managed;
        }
        status.persist();
        return status;
    }

    public Optional<IssueStatusDef> findById(Integer id) {
        return IssueStatusDef.findByIdOptional(id);
    }

    /** The seed identifier and the wire identity. Case-sensitive; callers normalise. */
    public Optional<IssueStatusDef> findByCode(String code) {
        return IssueStatusDef.find("code", code).firstResultOptional();
    }

    /** Ordered by {@code precedenceValue} ascending — the only ordering that exists. */
    public List<IssueStatusDef> listAll() {
        return IssueStatusDef.list("ORDER BY precedenceValue ASC");
    }

    public long count() {
        return IssueStatusDef.count();
    }

    @Transactional
    public void delete(IssueStatusDef status) {
        IssueStatusDef.deleteById(status.id);
    }
}
