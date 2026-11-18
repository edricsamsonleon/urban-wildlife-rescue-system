package algorithm;

import model.Incident;
import model.RescueTeam;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TeamMatchingEngine {

    public List<RescueTeam> findEligibleTeams(Incident incident, List<RescueTeam> allTeams) {
        List<RescueTeam> eligible = new ArrayList<>();
        if (incident == null || allTeams == null) {
            return eligible;
        }

        for (RescueTeam team : allTeams) {
            if (isTeamEligible(incident, team)) {
                eligible.add(team);
            }
        }
        return eligible;
    }

    public boolean isTeamEligible(Incident incident, RescueTeam team) {
        if (incident == null || team == null) {
            return false;
        }
        if (!team.isEligibleForDispatch()) {
            return false;
        }
        if (!supportsAnimalType(team, incident.getAnimalType())) {
            return false;
        }
        if (!meetsSpecialRequirements(team, incident.getSpecialRequirements())) {
            return false;
        }
        return isAvailableInTimeSlot(team, incident.getSubmissionTime().toLocalTime());
    }

    private boolean supportsAnimalType(RescueTeam team, String animalType) {
        if (animalType == null || animalType.trim().isEmpty()) {
            return false;
        }
        String target = animalType.trim().toLowerCase();
        Set<String> categories = team.getAnimalCategories();
        return categories.contains("all") || categories.contains(target);
    }

    private boolean meetsSpecialRequirements(RescueTeam team, List<String> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return true;
        }
        Set<String> equipment = team.getEquipment();
        Set<String> skills = team.getRescueSkills();

        for (String req : requirements) {
            if (req == null || req.trim().isEmpty() || req.trim().equalsIgnoreCase("none")) {
                continue;
            }
            String normalizedReq = req.trim().toLowerCase();
            boolean hasMatch = equipment.contains(normalizedReq)
                    || skills.contains(normalizedReq)
                    || equipment.stream().anyMatch(e -> e.contains(normalizedReq) || normalizedReq.contains(e))
                    || skills.stream().anyMatch(s -> s.contains(normalizedReq) || normalizedReq.contains(s));

            if (!hasMatch) {
                return false;
            }
        }
        return true;
    }

    private boolean isAvailableInTimeSlot(RescueTeam team, LocalTime incidentTime) {
        List<String> slots = team.getAvailabilitySlots();
        if (slots == null || slots.isEmpty()) {
            return true;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
        for (String slot : slots) {
            String trimmed = slot.trim();
            if (trimmed.equalsIgnoreCase("24/7") || trimmed.equalsIgnoreCase("any")) {
                return true;
            }
            if (trimmed.contains("-")) {
                String[] parts = trimmed.split("-");
                if (parts.length == 2) {
                    try {
                        LocalTime start = LocalTime.parse(parts[0].trim(), formatter);
                        LocalTime end = LocalTime.parse(parts[1].trim(), formatter);
                        if (!end.isBefore(start)) {
                            if (!incidentTime.isBefore(start) && !incidentTime.isAfter(end)) {
                                return true;
                            }
                        } else {
                            // Overnight shift (e.g., 20:00-06:00)
                            if (!incidentTime.isBefore(start) || !incidentTime.isAfter(end)) {
                                return true;
                            }
                        }
                    } catch (Exception ignored) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}