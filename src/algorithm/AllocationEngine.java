package algorithm;

public class AllocationEngine {
}
package algorithm;

import exception.AssignmentException;
import exception.FileOperationException;
import model.Assignment;
import model.Incident;
import model.IncidentStatus;
import model.RescueTeam;
import repository.AssignmentRepository;
import repository.IncidentRepository;
import repository.TeamRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class AllocationEngine {
    private final TeamMatchingEngine matchingEngine;
    private final TeamRankingEngine rankingEngine;
    private final IncidentRepository incidentRepository;
    private final TeamRepository teamRepository;
    private final AssignmentRepository assignmentRepository;

    public AllocationEngine(TeamMatchingEngine matchingEngine,
                            TeamRankingEngine rankingEngine,
                            IncidentRepository incidentRepository,
                            TeamRepository teamRepository,
                            AssignmentRepository assignmentRepository) {
        this.matchingEngine = matchingEngine;
        this.rankingEngine = rankingEngine;
        this.incidentRepository = incidentRepository;
        this.teamRepository = teamRepository;
        this.assignmentRepository = assignmentRepository;
    }

    public Optional<Assignment> allocateBestTeam(Incident incident)
            throws AssignmentException, FileOperationException {
        if (incident == null) {
            throw new AssignmentException("Cannot allocate a null incident.");
        }
        if (incident.getStatus() != IncidentStatus.PENDING_ASSIGNMENT) {
            return Optional.empty();
        }

        List<RescueTeam> allTeams = teamRepository.findAll();
        List<RescueTeam> eligible = matchingEngine.findEligibleTeams(incident, allTeams);
        if (eligible.isEmpty()) {
            return Optional.empty();
        }

        List<RescueTeam> ranked = rankingEngine.rankTeams(incident, eligible);
        RescueTeam selectedTeam = ranked.get(0);

        // Re-verify availability and workload capacity right before committing
        if (!selectedTeam.isEligibleForDispatch()) {
            throw new AssignmentException("Selected team " + selectedTeam.getTeamId() + " is no longer eligible.");
        }

        String assignmentId = assignmentRepository.generateNextId();
        Assignment assignment = new Assignment(
                assignmentId,
                incident.getIncidentId(),
                selectedTeam.getTeamId(),
                LocalDateTime.now(),
                null,
                "ACTIVE"
        );

        // Write-ahead event log before mutating persisted state
        assignmentRepository.logEvent(
                "TEAM_ASSIGNED",
                incident.getIncidentId(),
                selectedTeam.getTeamId(),
                "Auto-assigned via " + assignmentId
        );

        selectedTeam.incrementWorkload();
        incident.setAssignedTeamId(selectedTeam.getTeamId());
        incident.setStatus(IncidentStatus.ASSIGNED);

        assignmentRepository.add(assignment);
        teamRepository.update(selectedTeam);
        incidentRepository.update(incident);

        return Optional.of(assignment);
    }
}