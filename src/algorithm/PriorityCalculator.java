package algorithm;

import model.Incident;
import model.Priority;

import java.util.Comparator;

public class PriorityCalculator {

    public int calculateAndAssign(Incident incident) {
        int conditionPoints = scoreCondition(incident.getCondition());
        int dangerPoints = incident.isImmediateDanger() ? 30 : 0;
        int countPoints = scoreAnimalCount(incident.getAnimalCount());
        int locationPoints = scoreLocationRisk(incident.getLocation());

        int totalScore = Math.min(100, conditionPoints + dangerPoints + countPoints + locationPoints);
        incident.setPriorityScore(totalScore);
        incident.setPriorityLevel(Priority.fromScore(totalScore));
        return totalScore;
    }

    private int scoreCondition(String condition) {
        if (condition == null) return 10;
        String normalized = condition.trim().toLowerCase();

        if (normalized.contains("critical") || normalized.contains("trapped")
                || normalized.contains("bleeding") || normalized.contains("severe")) {
            return 40;
        }
        if (normalized.contains("injured") || normalized.contains("fracture")
                || normalized.contains("wound") || normalized.contains("hurt")) {
            return 30;
        }
        if (normalized.contains("sick") || normalized.contains("abandoned")
                || normalized.contains("orphaned") || normalized.contains("weak")) {
            return 20;
        }
        return 10; // displaced or general rescue
    }

    private int scoreAnimalCount(int count) {
        if (count >= 5) return 15;
        if (count >= 2) return 10;
        return 5;
    }

    private int scoreLocationRisk(String location) {
        if (location == null) return 3;
        String normalized = location.trim().toLowerCase();

        if (normalized.contains("highway") || normalized.contains("road")
                || normalized.contains("traffic") || normalized.contains("rooftop")
                || normalized.contains("roof") || normalized.contains("powerline")
                || normalized.contains("electric") || normalized.contains("river")
                || normalized.contains("canal") || normalized.contains("well")) {
            return 15;
        }
        if (normalized.contains("street") || normalized.contains("construction")
                || normalized.contains("market") || normalized.contains("drain")
                || normalized.contains("bridge") || normalized.contains("commercial")) {
            return 8;
        }
        return 3; // park, garden, residential area
    }

    public Comparator<Incident> getIncidentPriorityComparator() {
        return Comparator.comparingInt(Incident::getPriorityScore).reversed()
                .thenComparing(Incident::getSubmissionTime)
                .thenComparing(Incident::getIncidentId);
    }
}