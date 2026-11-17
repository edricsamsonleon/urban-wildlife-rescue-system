package model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Incident {
    private final String incidentId;
    private String animalType;
    private String condition;
    private String location;
    private int animalCount;
    private boolean immediateDanger;
    private List<String> specialRequirements;
    private String reporterId;
    private String reporterContact;
    private final LocalDateTime submissionTime;

    private int priorityScore;
    private Priority priorityLevel;
    private IncidentStatus status;
    private String assignedTeamId;

    public Incident(String incidentId, String animalType, String condition, String location,
                    int animalCount, boolean immediateDanger, List<String> specialRequirements,
                    String reporterId, String reporterContact, LocalDateTime submissionTime) {
        this.incidentId = incidentId;
        this.animalType = animalType;
        this.condition = condition;
        this.location = location;
        this.animalCount = animalCount;
        this.immediateDanger = immediateDanger;
        this.specialRequirements = specialRequirements != null
                ? new ArrayList<>(specialRequirements) : new ArrayList<>();
        this.reporterId = reporterId;
        this.reporterContact = reporterContact;
        this.submissionTime = submissionTime != null ? submissionTime : LocalDateTime.now();
        this.priorityScore = 0;
        this.priorityLevel = Priority.LOW;
        this.status = IncidentStatus.PENDING_ASSIGNMENT;
        this.assignedTeamId = "";
    }

    public String getIncidentId() { return incidentId; }
    public String getAnimalType() { return animalType; }
    public void setAnimalType(String animalType) { this.animalType = animalType; }

    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public int getAnimalCount() { return animalCount; }
    public void setAnimalCount(int animalCount) { this.animalCount = animalCount; }

    public boolean isImmediateDanger() { return immediateDanger; }
    public void setImmediateDanger(boolean immediateDanger) { this.immediateDanger = immediateDanger; }

    public List<String> getSpecialRequirements() {
        return Collections.unmodifiableList(specialRequirements);
    }
    public void setSpecialRequirements(List<String> specialRequirements) {
        this.specialRequirements = specialRequirements != null
                ? new ArrayList<>(specialRequirements) : new ArrayList<>();
    }

    public String getReporterId() { return reporterId; }
    public void setReporterId(String reporterId) { this.reporterId = reporterId; }

    public String getReporterContact() { return reporterContact; }
    public void setReporterContact(String reporterContact) { this.reporterContact = reporterContact; }

    public LocalDateTime getSubmissionTime() { return submissionTime; }

    public int getPriorityScore() { return priorityScore; }
    public void setPriorityScore(int priorityScore) {
        this.priorityScore = priorityScore;
        this.priorityLevel = Priority.fromScore(priorityScore);
    }

    public Priority getPriorityLevel() { return priorityLevel; }
    public void setPriorityLevel(Priority priorityLevel) { this.priorityLevel = priorityLevel; }

    public IncidentStatus getStatus() { return status; }
    public void setStatus(IncidentStatus status) { this.status = status; }

    public String getAssignedTeamId() { return assignedTeamId; }
    public void setAssignedTeamId(String assignedTeamId) {
        this.assignedTeamId = assignedTeamId != null ? assignedTeamId : "";
    }

    @Override
    public String toString() {
        return String.format("[%s] %s (%s, count=%d) @ %s | Priority: %s (%d) | Status: %s | Team: %s",
                incidentId, animalType, condition, animalCount, location,
                priorityLevel, priorityScore, status,
                (assignedTeamId == null || assignedTeamId.isEmpty()) ? "None" : assignedTeamId);
    }
}