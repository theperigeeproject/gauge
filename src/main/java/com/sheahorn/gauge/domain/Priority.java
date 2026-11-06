package pl.perigee.gauge.domain;

public enum Priority {
    LOW(0),
    NORMAL(1),
    HIGH(2);

    public final int severity;

    Priority(int severity) {
        this.severity = severity;
    }

    public static Priority fromSeverity(int severity) {
        for (Priority p : values()) {
            if (p.severity == severity) return p;
        }
        return NORMAL;
    }
}
