package model;

import exception.InvalidInputException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RescueTeam extends BaseEntity {
    private String teamName;
    private List<String> members;
    private Set<String> animalCategories;
    private Set<String> rescueSkills;
    private Set<String> equipment;
    private TeamStatus status;
    private int maxCapacity;
    private int activeWorkload;

    public RescueTeam(String teamId, String teamName, List<String> members,
                      Set<String> animalCategories, Set<String> rescueSkills,
                      Set<String> equipment, TeamStatus status,
                      int maxCapacity, int activeWorkload) {
        super(teamId, LocalDateTime.now());
        this.teamName = teamName;
        this.members = (members != null) ? new ArrayList<>(members) : new ArrayList<>();
        this.animalCategories = normalizeSet(animalCategories);
        this.rescueSkills = normalizeSet(rescueSkills);
        this.equipment = normalizeSet(equipment);
        this.status = (status != null) ? status : TeamStatus.AVAILABLE;
        this.maxCapacity = Math.max(1, maxCapacity);
        this.activeWorkload = Math.max(0, activeWorkload);
    }

    private Set<String> normalizeSet(Set<String> raw) {
        Set<String> clean = new HashSet<>();
        if (raw != null) {
            for (String item : raw) {
                if (item != null && !item.trim().isEmpty()) {
                    clean.add(item.trim().toLowerCase());
                }
            }
        }
        return clean;
    }

    public int getRemainingCapacity() {
        return Math.max(0, maxCapacity - activeWorkload);
    }

    public boolean hasCapacity() {
        return activeWorkload < maxCapacity;
    }

    public boolean isAvailableForDispatch() {
        return status != TeamStatus.UNAVAILABLE && hasCapacity();
    }

    public void incrementWorkload() {
        this.activeWorkload++;
        if (this.status != TeamStatus.UNAVAILABLE) {
            this.status = TeamStatus.ASSIGNED;
        }
    }

    public void decrementWorkload() {
        if (this.activeWorkload > 0) {
            this.activeWorkload--;
        }
        if (this.status != TeamStatus.UNAVAILABLE) {
            this.status = (this.activeWorkload == 0) ? TeamStatus.AVAILABLE : TeamStatus.ASSIGNED;
        }
    }

    public String getTeamId() { return id; }
    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }

    public List<String> getMembers() { return Collections.unmodifiableList(members); }
    public void setMembers(List<String> members) {
        this.members = (members != null) ? new ArrayList<>(members) : new ArrayList<>();
    }

    public Set<String> getAnimalCategories() { return Collections.unmodifiableSet(animalCategories); }
    public void setAnimalCategories(Set<String> animalCategories) {
        this.animalCategories = normalizeSet(animalCategories);
    }

    public Set<String> getRescueSkills() { return Collections.unmodifiableSet(rescueSkills); }
    public void setRescueSkills(Set<String> rescueSkills) {
        this.rescueSkills = normalizeSet(rescueSkills);
    }

    public Set<String> getEquipment() { return Collections.unmodifiableSet(equipment); }
    public void setEquipment(Set<String> equipment) {
        this.equipment = normalizeSet(equipment);
    }

    public TeamStatus getStatus() { return status; }
    public void setStatus(TeamStatus status) { this.status = status; }

    public int getMaxCapacity() { return maxCapacity; }
    public void setMaxCapacity(int maxCapacity) throws InvalidInputException {
        if (maxCapacity < 1) {
            throw new InvalidInputException("Maximum capacity must be at least 1.");
        }
        if (maxCapacity < this.activeWorkload) {
            throw new InvalidInputException(
                    "Cannot reduce max capacity below current active workload (" + this.activeWorkload + ").");
        }
        this.maxCapacity = maxCapacity;
    }

    public int getActiveWorkload() { return activeWorkload; }
    public void setActiveWorkload(int activeWorkload) {
        this.activeWorkload = Math.max(0, activeWorkload);
    }

    @Override
    public String toString() {
        return String.format("[%s - %s] Status: %s | Load: %d/%d | Animals: %s | Skills: %s | Gear: %s",
                id, teamName, status, activeWorkload, maxCapacity,
                animalCategories, rescueSkills, equipment);
    }
}