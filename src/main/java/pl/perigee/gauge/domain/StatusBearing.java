package pl.perigee.gauge.domain;

/**
 * The status id column every status-bearing entity carries, plus the legacy code it replaced.
 *
 * <p><b>Why an interface and not four duplicated field declarations.</b> The four entities are
 * Panache entities with public fields and no inheritance; an interface cannot supply the field, only
 * the accessor contract. That is enough: the migration, the scanner and the DTO mapping all work
 * through {@link #statusId()} / {@link #setStatusId(Integer)} and {@link #legacyStatusCode()}, and
 * none of them needs to know which level it is holding.
 *
 * <p><b>`statusId` is deliberately not called `status`.</b> On an upgraded database the legacy
 * varchar column still occupies that name (ADR 0020 §5). Naming them differently is what makes a
 * reader able to tell which side of the translation they are on: `statusId` is the internal
 * referent, `status` on the wire is the code.
 *
 * <p><b>The legacy accessor is read-only by design.</b> There is no setter: after the migration
 * nothing writes a code into that column again. It exists so the migration can read it once, and so
 * a future cleanup can find it.
 */
public interface StatusBearing {

    /** The internal status id. Null until migrated; never null through the API. */
    Integer statusId();

    void setStatusId(Integer statusId);

    /**
     * The pre-migration status code, straight off the untouched legacy column.
     *
     * <p>Read by {@code StatusDataMigrationStartup} and by nothing else. Do not use it to resolve
     * behaviour — that is exactly the name-dependent coupling ADR 0020 §6 forbids.
     */
    String legacyStatusCode();
}
