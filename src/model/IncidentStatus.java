package model;

public enum IncidentStatus {
    PENDING_ASSIGNMENT,
    ASSIGNED,
    IN_PROGRESS,
    COMPLETED;

    public boolean canTransitionTo(IncidentStatus next) {
        if (next == null || this == next) return false;
        return switch (this) {
            case PENDING_ASSIGNMENT -> next == ASSIGNED;
            case ASSIGNED -> next == IN_PROGRESS || next == PENDING_ASSIGNMENT;
            case IN_PROGRESS -> next == COMPLETED;
            case COMPLETED -> false;
        };
    }
}