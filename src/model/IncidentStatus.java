package model;

public enum IncidentStatus {
    PENDING_ASSIGNMENT,
    ASSIGNED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    public boolean canTransitionTo(IncidentStatus next) {
        if (next == null || this == next) return false;
        return switch (this) {
            case PENDING_ASSIGNMENT -> next == ASSIGNED || next == CANCELLED;
            case ASSIGNED -> next == IN_PROGRESS || next == PENDING_ASSIGNMENT || next == CANCELLED;
            case IN_PROGRESS -> next == COMPLETED || next == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}