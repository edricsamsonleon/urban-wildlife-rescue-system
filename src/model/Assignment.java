package model;

import java.time.LocalDateTime;

public class Assignment {
    private final String assignmentId;
    private final String incidentId;
    private final String teamId;
    private final LocalDateTime assignedTime;
    private LocalDateTime completedTime;
    private String status;

    public Assignment(String assignmentId, String incidentId, String teamId,
                      LocalDateTime assignedTime, LocalDateTime completedTime, String status) {
        this.assignmentId = assignmentId;
        this.incidentId = incidentId;
        this.teamId = teamId;
        this.assignedTime = assignedTime != null ? assignedTime : LocalDateTime.now();
        this.completedTime = completedTime;
        this.status = status != null ? status : "ACTIVE";
    }

    public String getAssignmentId() { return assignmentId; }
    public String getIncidentId() { return incidentId; }
    public String getTeamId() { return teamId; }
    public LocalDateTime getAssignedTime() { return assignedTime; }

    public LocalDateTime getCompletedTime() { return completedTime; }
    public void setCompletedTime(LocalDateTime completedTime) { this.completedTime = completedTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @Override
    public String toString() {
        return String.format("[%s] Incident: %s -> Team: %s | Status: %s | Assigned: %s",
                assignmentId, incidentId, teamId, status, assignedTime);
    }
}