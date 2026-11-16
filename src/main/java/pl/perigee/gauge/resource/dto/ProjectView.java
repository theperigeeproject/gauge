package pl.perigee.gauge.resource.dto;

/**
 * A project on the wire. Status is a code, and it is <b>new</b> on this surface: projects gained a
 * lifecycle status in increment 3 and had none before, so nothing was removed to make room.
 */
public record ProjectView(
    String id,
    String name,
    String description,
    String parentId,
    String removalLock,
    String status
) {}
