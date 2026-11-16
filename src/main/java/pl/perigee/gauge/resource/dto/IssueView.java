package pl.perigee.gauge.resource.dto;

/**
 * An issue as it appears on the wire. Status is a <b>code</b>; the internal status id never appears
 * (ADR 0020 §2).
 *
 * <p>Field names and order match the entity's old JSON exactly, so clients and the templates' JS
 * need no change. What is gone is {@code statusId}, which used to leak because the entity was
 * serialized raw.
 */
public record IssueView(
    String id,
    String projectId,
    String title,
    String description,
    String status,
    String priority
) {}
