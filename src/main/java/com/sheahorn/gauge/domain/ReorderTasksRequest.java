package pl.perigee.gauge.domain;

import java.util.List;

public record ReorderTasksRequest(
    List<String> taskIds
) {}
