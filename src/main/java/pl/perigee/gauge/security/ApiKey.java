package pl.perigee.gauge.security;

public class ApiKey {
    public final String userId;
    public final String username;
    public final String role;
    public final String restrictedProjectId;
    public final String accessLevel; // "READ" or "WRITE", null if unrestricted

    public static final ApiKey MASTER = new ApiKey(null, "master", "admin", null, null);

    public ApiKey(String userId, String username, String role, String restrictedProjectId, String accessLevel) {
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.restrictedProjectId = (restrictedProjectId == null || restrictedProjectId.isBlank()) ? null : restrictedProjectId;
        this.accessLevel = (this.restrictedProjectId == null) ? null : (accessLevel != null ? accessLevel : "READ");
    }

    public boolean isAdmin() {
        return "admin".equals(role);
    }

    /** Returns true if this key is restricted to a specific project. */
    public boolean isRestricted() {
        return restrictedProjectId != null;
    }

    /** Returns true if this key has write access (unrestricted, or restricted with WRITE level). */
    public boolean canWrite() {
        return !isRestricted() || "WRITE".equals(accessLevel);
    }
}
