package model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RescueTeam {
    private final String teamId;
    private List<String> members;
    private Set<String> animalCategories;
    private Set<String> rescueSkills;
    private Set<String> equipment;
    private TeamStatus status;
    private int maxCapacity;
    private int activeWorkload;
    private List<String> availabilitySlots;

    public RescueTeam(String teamId, List<String> members, Set<String> animalCategories,
                      Set<String> rescueSkills, Set<String> equipment, TeamStatus status,
                      int maxCapacity, int activeWorkload, List<String> availabilitySlots) {
        this.teamId = teamId;
        this.members = members != null ? new ArrayList<>(members) : new ArrayList<>();
        this.animalCategories = normalizeSet(animalCategories);
        this.rescueSkills = normalizeSet(rescueSkills);
        this.equipment = normalizeSet(equipment);
        this.status = status != null ? status : TeamStatus.AVAILABLE;
        this.maxCapacity = maxCapacity;
        this.activeWorkload = Math.max(0, activeWorkload);
        this.availabilitySlots = availabilitySlots != null
                ? new ArrayList<>(availabilitySlots) : new ArrayList<>();
    }

    private Set<String> normalizeSet(Set<String> input) {
        Set<String> normalized = new HashSet<>();
        if (input != null) {
            for (String item : input) {
                if (item != null && !item.trim().isEmpty()) {
                    normalized.add(item.trim().toLowerCase());
                }
            }
        }
        return normalized;
    }

    public int getRemainingCapacity() {
        return Math.max(0, maxCapacity - activeWorkload);
    }

    public boolean hasCapacity() {
        return activeWorkload < maxCapacity;
    }

    public boolean isEligibleForDispatch() {
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

    public String getTeamId() { return teamId; }

    public List<String> getMembers() { return Collections.unmodifiableList(members); }
    public void setMembers(List<String> members) {
        this.members = members != null ? new ArrayList<>(members) : new ArrayList<>();
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
    public void setMaxCapacity(int maxCapacity) { this.maxCapacity = maxCapacity; }

    public int getActiveWorkload() { return activeWorkload; }
    public void setActiveWorkload(int activeWorkload) {
        this.activeWorkload = Math.max(0, activeWorkload);
    }

    public List<String> getAvailabilitySlots() {
        return Collections.unmodifiableList(availabilitySlots);
    }
    public void setAvailabilitySlots(List<String> availabilitySlots) {
        this.availabilitySlots = availabilitySlots != null
                ? new ArrayList<>(availabilitySlots) : new ArrayList<>();
    }

    @Override
    public String toString() {
        return String.format("[%s] Status: %s | Load: %d/%d | Animals: %s | Skills: %s | Equipment: %s | Slots: %s",
                teamId, status, activeWorkload, maxCapacity,
                animalCategories, rescueSkills, equipment, availabilitySlots);
    }
}