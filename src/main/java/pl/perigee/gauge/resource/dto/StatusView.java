package pl.perigee.gauge.resource.dto;

/**
 * One status as the UI needs to see it: the <b>code</b> as identity, plus the server's verdicts.
 *
 * <p><b>No id.</b> The internal status id never reaches a caller (ADR 0020 §2), so a renderer can
 * never depend on one — the code is the wire identity, and a rename just changes what appears here.
 *
 * <p><b>The predicates are resolved server-side on purpose.</b> {@code terminal}, {@code parked},
 * {@code active} and {@code readVisible} are the §6 definitions, computed by
 * {@code StatusService}. Handing a template or a browser script the raw axes would invite it to
 * re-derive "hidden" from {@code progressLevel == 'FINALIZED'}, which is exactly the
 * name-dependent coupling §6 forbids — and it would break the moment an operator adds a status the
 * frontend has never heard of.
 */
public record StatusView(
    String code,
    String color,
    int precedenceValue,
    String progressLevel,
    String attitudeType,
    boolean terminal,
    boolean parked,
    boolean active,
    boolean readVisible,
    boolean defaultForLevel
) {}
