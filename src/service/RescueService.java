package service;

import exception.AssignmentException;
import exception.DuplicateIdException;
import exception.FileStorageException;
import exception.InvalidInputException;
import model.Incident;
import model.IncidentStatus;
import model.PriorityLevel;
import model.RescueTeam;
import model.TeamStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class RescueService {
    private final FileHandler fileHandler;
    private final ConcurrentHashMap<String, Incident> incidents = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RescueTeam> teams = new ConcurrentHashMap<>();
    private final PriorityBlockingQueue<Incident> pendingQueue;
    private final ReentrantLock allocationLock = new ReentrantLock(true);
    private final AtomicInteger incidentCounter = new AtomicInteger(1000);

    public RescueService(FileHandler fileHandler) throws FileStorageException {
        this.fileHandler = fileHandler;
        this.pendingQueue = new PriorityBlockingQueue<>(16, getPriorityComparator());
        initializeFromStorage();
    }

    private void initializeFromStorage() throws FileStorageException {
        for (RescueTeam team : fileHandler.loadTeams()) {
            teams.put(team.getTeamId().toUpperCase(), team);
        }

        int maxId = 1000;
        Map<String, Integer> activeLoads = new HashMap<>();

        for (Incident inc : fileHandler.loadIncidents()) {
            incidents.put(inc.getIncidentId().toUpperCase(), inc);

            if (inc.getIncidentId().toUpperCase().startsWith("INC-")) {
                try {
                    int num = Integer.parseInt(inc.getIncidentId().substring(4));
                    if (num > maxId) maxId = num;
                } catch (NumberFormatException ignored) {
                }
            }

            if ((inc.getStatus() == IncidentStatus.ASSIGNED || inc.getStatus() == IncidentStatus.IN_PROGRESS)
                    && !inc.getAssignedTeamId().isEmpty()) {
                activeLoads.merge(inc.getAssignedTeamId().toUpperCase(), 1, Integer::sum);
            }
        }
        incidentCounter.set(maxId);

        // Reconcile team workloads from active incidents to prevent desync across restarts
        for (RescueTeam team : teams.values()) {
            int actualLoad = activeLoads.getOrDefault(team.getTeamId().toUpperCase(), 0);
            team.setActiveWorkload(actualLoad);
            if (team.getStatus() != TeamStatus.UNAVAILABLE) {
                team.setStatus(actualLoad == 0 ? TeamStatus.AVAILABLE : TeamStatus.ASSIGNED);
            }
        }
        fileHandler.saveTeams(teams.values());
    }

    public Comparator<Incident> getPriorityComparator() {
        return Comparator.comparingInt(Incident::getPriorityScore).reversed()
                .thenComparing(Incident::getCreatedAt)
                .thenComparing(Incident::getIncidentId);
    }

    public RescueTeam registerTeam(String teamId, String teamName, List<String> members,
                                   Set<String> animalCategories, Set<String> rescueSkills,
                                   Set<String> equipment, int maxCapacity)
            throws InvalidInputException, DuplicateIdException, FileStorageException {
        if (teamId == null || teamId.trim().isEmpty()) {
            throw new InvalidInputException("Team ID cannot be empty.");
        }
        if (teamName == null || teamName.trim().isEmpty()) {
            throw new InvalidInputException("Team name cannot be empty.");
        }
        if (animalCategories == null || animalCategories.isEmpty()) {
            throw new InvalidInputException("Team must support at least one animal category.");
        }
        if (maxCapacity < 1) {
            throw new InvalidInputException("Maximum capacity must be at least 1.");
        }

        String normalizedId = teamId.trim().toUpperCase();
        RescueTeam team = new RescueTeam(normalizedId, teamName.trim(), members,
                animalCategories, rescueSkills, equipment, TeamStatus.AVAILABLE, maxCapacity, 0);

        if (teams.putIfAbsent(normalizedId, team) != null) {
            throw new DuplicateIdException("Rescue team ID already exists: " + normalizedId);
        }

        try {
            fileHandler.saveTeams(teams.values());
        } catch (FileStorageException e) {
            teams.remove(normalizedId);
            throw e;
        }
        return team;
    }

    public void updateTeamProfile(String teamId, List<String> members, Set<String> animalCategories,
                                  Set<String> rescueSkills, Set<String> equipment, int maxCapacity)
            throws InvalidInputException, FileStorageException {
        RescueTeam team = getTeamOrThrow(teamId);
        allocationLock.lock();
        try {
            team.setMaxCapacity(maxCapacity);
            if (members != null && !members.isEmpty()) team.setMembers(members);
            if (animalCategories != null && !animalCategories.isEmpty()) team.setAnimalCategories(animalCategories);
            if (rescueSkills != null) team.setRescueSkills(rescueSkills);
            if (equipment != null) team.setEquipment(equipment);
            fileHandler.saveTeams(teams.values());
        } finally {
            allocationLock.unlock();
        }
    }

    public void updateTeamAvailability(String teamId, boolean available)
            throws InvalidInputException, FileStorageException {
        RescueTeam team = getTeamOrThrow(teamId);
        allocationLock.lock();
        try {
            if (!available) {
                team.setStatus(TeamStatus.UNAVAILABLE);
            } else {
                team.setStatus(team.getActiveWorkload() == 0 ? TeamStatus.AVAILABLE : TeamStatus.ASSIGNED);
            }
            fileHandler.saveTeams(teams.values());
        } finally {
            allocationLock.unlock();
        }
    }

    protected RescueTeam getTeamOrThrow(String teamId) throws InvalidInputException {
        if (teamId == null || teamId.trim().isEmpty()) {
            throw new InvalidInputException("Team ID is required.");
        }
        RescueTeam team = teams.get(teamId.trim().toUpperCase());
        if (team == null) {
            throw new InvalidInputException("Rescue team not found: " + teamId);
        }
        return team;
    }

    public List<RescueTeam> getAllTeams() {
        List<RescueTeam> list = new ArrayList<>(teams.values());
        list.sort(Comparator.comparing(RescueTeam::getTeamId));
        return list;
    }
}