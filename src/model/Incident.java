package model;

import exception.InvalidInputException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Incident extends BaseEntity {
    private String animalType;
    private String condition;
    private String location;
    private int animalCount;
    private boolean immediateDanger;
    private List<String> specialRequirements;
    private String reporterName;
    private String reporterContact;

    private int priorityScore;
    private PriorityLevel priorityLevel;
    private IncidentStatus status;
    private String assignedTeamId;

    public Incident(String incidentId, String animalType, String condition, String location,
                    int animalCount, boolean immediateDanger, List<String> specialRequirements,
                    String reporterName, String reporterContact, LocalDateTime createdAt) {
        super(incidentId, createdAt);
        this.animalType = animalType;
        this.condition = condition;
        this.location = location;
        this.animalCount = animalCount;
        this.immediateDanger = immediateDanger;
        this.specialRequirements = (specialRequirements != null)
                ? new ArrayList<>(specialRequirements) : new ArrayList<>();
        this.reporterName = reporterName;
        this.reporterContact = reporterContact;
        this.priorityScore = 0;
        this.priorityLevel = PriorityLevel.LOW;
        this.status = IncidentStatus.PENDING_ASSIGNMENT;
        this.assignedTeamId = "";
    }

    public void transitionTo(IncidentStatus nextStatus) throws InvalidInputException {
        if (!this.status.canTransitionTo(nextStatus)) {
            throw new InvalidInputException(
                    "Cannot change incident " + id + " from " + this.status + " to " + nextStatus);
        }
        this.status = nextStatus;
    }

    public String getIncidentId() { return id; }
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
        this.specialRequirements = (specialRequirements != null)
                ? new ArrayList<>(specialRequirements) : new ArrayList<>();
    }

    public String getReporterName() { return reporterName; }
    public void setReporterName(String reporterName) { this.reporterName = reporterName; }

    public String getReporterContact() { return reporterContact; }
    public void setReporterContact(String reporterContact) { this.reporterContact = reporterContact; }

    public int getPriorityScore() { return priorityScore; }
    public void setPriorityScore(int priorityScore) {
        this.priorityScore = priorityScore;
        this.priorityLevel = PriorityLevel.fromScore(priorityScore);
    }

    public PriorityLevel getPriorityLevel() { return priorityLevel; }
    public void setPriorityLevel(PriorityLevel priorityLevel) { this.priorityLevel = priorityLevel; }

    public IncidentStatus getStatus() { return status; }
    public void setStatus(IncidentStatus status) { this.status = status; }

    public String getAssignedTeamId() { return assignedTeamId; }
    public void setAssignedTeamId(String assignedTeamId) {
        this.assignedTeamId = (assignedTeamId != null) ? assignedTeamId : "";
    }

    @Override
    public String toString() {
        String teamLabel = (assignedTeamId == null || assignedTeamId.isEmpty()) ? "Unassigned" : assignedTeamId;
        return String.format("[%s] %s (%s, count=%d) @ %s | Priority: %s (%d/100) | Status: %s | Team: %s",
                id, animalType, condition, animalCount, location,
                priorityLevel, priorityScore, status, teamLabel);
    }
}