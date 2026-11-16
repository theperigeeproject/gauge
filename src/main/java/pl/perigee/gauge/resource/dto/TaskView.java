package pl.perigee.gauge.resource.dto;

/** A task on the wire. Status is a code; the status id never appears (ADR 0020 §2). */
public record TaskView(
    String id,
    String tasklistId,
    int ordinal,
    String title,
    String description,
    String status
) {}
