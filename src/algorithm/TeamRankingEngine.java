package algorithm;

import model.Incident;
import model.RescueTeam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TeamRankingEngine {

    public List<RescueTeam> rankTeams(Incident incident, List<RescueTeam> eligibleTeams) {
        List<RescueTeam> ranked = new ArrayList<>(eligibleTeams);
        ranked.sort(getTeamRankingComparator());
        return ranked;
    }

    public Comparator<RescueTeam> getTeamRankingComparator() {
        return Comparator
                .comparingDouble((RescueTeam t) -> (double) t.getRemainingCapacity() / t.getMaxCapacity())
                .reversed()
                .thenComparing(Comparator.comparingInt(
                        (RescueTeam t) -> t.getRescueSkills().size() + t.getEquipment().size()).reversed())
                .thenComparingInt(RescueTeam::getActiveWorkload)
                .thenComparing(RescueTeam::getTeamId);
    }
}