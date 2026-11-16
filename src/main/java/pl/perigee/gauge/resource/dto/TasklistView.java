package pl.perigee.gauge.resource.dto;

/** A tasklist on the wire. Status is a code; the status id never appears (ADR 0020 §2). */
public record TasklistView(
    String id,
    String issueId,
    String title,
    String status,
    String decomposesTaskId
) {}
