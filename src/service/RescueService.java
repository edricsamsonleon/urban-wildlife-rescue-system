package service;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class RescueService {
    private static final Set<String> VALID_ANIMAL_TYPES = Set.of(
            "bird", "mammal", "reptile", "amphibian", "marine", "other"
    );

    private final FileHandler fileHandler;
    private final ConcurrentHashMap<String, Incident> incidents = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RescueTeam> teams = new ConcurrentHashMap<>();
    private final PriorityBlockingQueue<Incident> pendingQueue;
    private final ReentrantLock allocationLock = new ReentrantLock(true);
    private final AtomicInteger incidentCounter = new AtomicInteger(1000);
    private final ExecutorService dispatchExecutor = Executors.newFixedThreadPool(4);

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
            } else if (inc.getStatus() == IncidentStatus.PENDING_ASSIGNMENT) {
                if (inc.getPriorityScore() == 0) {
                    calculatePriority(inc);
                }
                enqueueIncident(inc);
            }
        }
        incidentCounter.set(maxId);

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

    public Future<String> reportAndDispatchAsync(String animalType, String condition, String location,
                                                 int animalCount, boolean immediateDanger,
                                                 List<String> specialRequirements,
                                                 String reporterName, String reporterContact)
            throws InvalidInputException, DuplicateIdException, FileStorageException {
        Incident incident = createIncident(animalType, condition, location, animalCount,
                immediateDanger, specialRequirements, reporterName, reporterContact);
        return dispatchExecutor.submit(new DispatchWorker(incident, this));
    }

    public Incident createIncident(String animalType, String condition, String location,
                                   int animalCount, boolean immediateDanger,
                                   List<String> specialRequirements,
                                   String reporterName, String reporterContact)
            throws InvalidInputException, DuplicateIdException, FileStorageException {
        if (animalType == null || !VALID_ANIMAL_TYPES.contains(animalType.trim().toLowerCase())) {
            throw new InvalidInputException(
                    "Invalid animal type. Supported: bird, mammal, reptile, amphibian, marine, other.");
        }
        if (condition == null || condition.trim().isEmpty()) {
            throw new InvalidInputException("Animal condition cannot be empty.");
        }
        if (location == null || location.trim().isEmpty()) {
            throw new InvalidInputException("Approximate location is required.");
        }
        if (animalCount < 1) {
            throw new InvalidInputException("Number of animals must be at least 1.");
        }
        if (reporterName == null || reporterName.trim().isEmpty()
                || reporterContact == null || reporterContact.trim().isEmpty()) {
            throw new InvalidInputException("Reporter name and contact details are required.");
        }

        String incidentId = generateUniqueIncidentId();
        Incident incident = new Incident(
                incidentId,
                animalType.trim().toLowerCase(),
                condition.trim(),
                location.trim(),
                animalCount,
                immediateDanger,
                specialRequirements,
                reporterName.trim(),
                reporterContact.trim(),
                LocalDateTime.now()
        );

        if (incidents.putIfAbsent(incidentId, incident) != null) {
            throw new DuplicateIdException("Duplicate Incident ID generated: " + incidentId);
        }

        try {
            fileHandler.saveIncidents(incidents.values());
        } catch (FileStorageException e) {
            incidents.remove(incidentId);
            throw e;
        }
        return incident;
    }

    public void persistIncidentState(Incident incident) throws FileStorageException {
        incidents.put(incident.getIncidentId().toUpperCase(), incident);
        fileHandler.saveIncidents(incidents.values());
    }

    public void calculatePriority(Incident incident) {
        int score = 0;

        String cond = incident.getCondition().toLowerCase();
        if (cond.contains("critical") || cond.contains("trapped") || cond.contains("bleeding")) {
            score += 40;
        } else if (cond.contains("injured") || cond.contains("hurt") || cond.contains("fracture")) {
            score += 30;
        } else if (cond.contains("sick") || cond.contains("abandoned") || cond.contains("orphaned")) {
            score += 20;
        } else {
            score += 10;
        }

        if (incident.isImmediateDanger()) score += 30;

        int count = incident.getAnimalCount();
        if (count >= 5) score += 15;
        else if (count >= 2) score += 10;
        else score += 5;

        String loc = incident.getLocation().toLowerCase();
        if (loc.contains("highway") || loc.contains("road") || loc.contains("rooftop")
                || loc.contains("powerline") || loc.contains("river") || loc.contains("well")) {
            score += 15;
        } else if (loc.contains("street") || loc.contains("construction") || loc.contains("drain")) {
            score += 8;
        } else {
            score += 3;
        }

        incident.setPriorityScore(Math.min(100, score));
        incident.setPriorityLevel(PriorityLevel.fromScore(incident.getPriorityScore()));
    }

    public void enqueueIncident(Incident incident) {
        if (incident != null && incident.getStatus() == IncidentStatus.PENDING_ASSIGNMENT) {
            pendingQueue.removeIf(existing -> existing.getIncidentId().equals(incident.getIncidentId()));
            pendingQueue.offer(incident);
        }
    }

    public List<RescueTeam> findEligibleTeams(Incident incident) {
        return teams.values().stream()
                .filter(team -> isTeamEligible(incident, team))
                .sorted(Comparator
                        .comparingDouble((RescueTeam t) -> (double) t.getRemainingCapacity() / t.getMaxCapacity())
                        .reversed()
                        .thenComparing(Comparator.comparingInt(
                                (RescueTeam t) -> t.getRescueSkills().size() + t.getEquipment().size()).reversed())
                        .thenComparingInt(RescueTeam::getActiveWorkload)
                        .thenComparing(RescueTeam::getTeamId))
                .collect(Collectors.toList());
    }

    private boolean isTeamEligible(Incident incident, RescueTeam team) {
        if (!team.isAvailableForDispatch()) {
            return false;
        }
        String animal = incident.getAnimalType().toLowerCase();
        if (!team.getAnimalCategories().contains("all") && !team.getAnimalCategories().contains(animal)) {
            return false;
        }
        for (String req : incident.getSpecialRequirements()) {
            if (req == null || req.trim().isEmpty() || req.trim().equalsIgnoreCase("none")) {
                continue;
            }
            String target = req.trim().toLowerCase();
            boolean hasGearOrSkill = team.getEquipment().stream().anyMatch(e -> e.contains(target) || target.contains(e))
                    || team.getRescueSkills().stream().anyMatch(s -> s.contains(target) || target.contains(s));
            if (!hasGearOrSkill) {
                return false;
            }
        }
        return true;
    }

    public int processPendingQueue() throws FileStorageException {
        allocationLock.lock();
        int assignedCount = 0;
        List<Incident> unallocated = new ArrayList<>();

        try {
            Incident current;
            while ((current = pendingQueue.poll()) != null) {
                if (current.getStatus() != IncidentStatus.PENDING_ASSIGNMENT) {
                    continue;
                }
                List<RescueTeam> candidates = findEligibleTeams(current);
                if (candidates.isEmpty()) {
                    unallocated.add(current);
                    continue;
                }

                RescueTeam chosen = candidates.get(0);
                if (!chosen.isAvailableForDispatch()) {
                    unallocated.add(current);
                    continue;
                }

                chosen.incrementWorkload();
                current.setAssignedTeamId(chosen.getTeamId());
                current.setStatus(IncidentStatus.ASSIGNED);
                assignedCount++;
            }

            for (Incident waiting : unallocated) {
                if (waiting.getStatus() == IncidentStatus.PENDING_ASSIGNMENT) {
                    pendingQueue.offer(waiting);
                }
            }

            if (assignedCount > 0) {
                fileHandler.saveTeams(teams.values());
                fileHandler.saveIncidents(incidents.values());
            }
        } finally {
            allocationLock.unlock();
        }
        return assignedCount;
    }

    public void updateIncidentStatus(String incidentId, IncidentStatus newStatus, String actingTeamId)
            throws InvalidInputException, FileStorageException {
        Incident incident = getIncidentOrThrow(incidentId);
        boolean releaseCapacity = false;

        allocationLock.lock();
        try {
            if (actingTeamId != null && !actingTeamId.equalsIgnoreCase(incident.getAssignedTeamId())) {
                throw new InvalidInputException("Team " + actingTeamId + " is not assigned to incident " + incidentId);
            }

            String previousTeamId = incident.getAssignedTeamId();
            incident.transitionTo(newStatus);

            if ((newStatus == IncidentStatus.COMPLETED || newStatus == IncidentStatus.PENDING_ASSIGNMENT)
                    && !previousTeamId.isEmpty()) {
                RescueTeam team = teams.get(previousTeamId.toUpperCase());
                if (team != null) {
                    team.decrementWorkload();
                }
                if (newStatus == IncidentStatus.PENDING_ASSIGNMENT) {
                    incident.setAssignedTeamId("");
                    enqueueIncident(incident);
                }
                releaseCapacity = true;
            }

            fileHandler.saveIncidents(incidents.values());
            fileHandler.saveTeams(teams.values());
        } finally {
            allocationLock.unlock();
        }

        if (releaseCapacity) {
            processPendingQueue();
        }
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
            processPendingQueue();
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
        processPendingQueue();
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
        if (available) {
            processPendingQueue();
        }
    }

    public List<Incident> getAllIncidents() {
        List<Incident> list = new ArrayList<>(incidents.values());
        list.sort(getPriorityComparator());
        return list;
    }

    public List<Incident> getIncidentsByReporter(String reporterName) {
        return incidents.values().stream()
                .filter(i -> i.getReporterName().equalsIgnoreCase(reporterName.trim()))
                .sorted(getPriorityComparator())
                .collect(Collectors.toList());
    }

    public List<Incident> getIncidentsByTeam(String teamId) {
        return incidents.values().stream()
                .filter(i -> teamId.trim().equalsIgnoreCase(i.getAssignedTeamId()))
                .sorted(getPriorityComparator())
                .collect(Collectors.toList());
    }

    public Optional<Incident> findIncidentById(String incidentId) {
        if (incidentId == null) return Optional.empty();
        return Optional.ofNullable(incidents.get(incidentId.trim().toUpperCase()));
    }

    public List<RescueTeam> getAllTeams() {
        List<RescueTeam> list = new ArrayList<>(teams.values());
        list.sort(Comparator.comparing(RescueTeam::getTeamId));
        return list;
    }

    public String generateOperationalReport() {
        List<Incident> allInc = getAllIncidents();
        List<RescueTeam> allTeams = getAllTeams();

        long critical = allInc.stream().filter(i -> i.getPriorityLevel() == PriorityLevel.CRITICAL).count();
        long high = allInc.stream().filter(i -> i.getPriorityLevel() == PriorityLevel.HIGH).count();
        long medium = allInc.stream().filter(i -> i.getPriorityLevel() == PriorityLevel.MEDIUM).count();
        long low = allInc.stream().filter(i -> i.getPriorityLevel() == PriorityLevel.LOW).count();

        long pending = allInc.stream().filter(i -> i.getStatus() == IncidentStatus.PENDING_ASSIGNMENT).count();
        long assigned = allInc.stream().filter(i -> i.getStatus() == IncidentStatus.ASSIGNED).count();
        long inProgress = allInc.stream().filter(i -> i.getStatus() == IncidentStatus.IN_PROGRESS).count();
        long completed = allInc.stream().filter(i -> i.getStatus() == IncidentStatus.COMPLETED).count();

        StringBuilder sb = new StringBuilder();
        sb.append("\n================ OPERATIONAL RESCUE REPORT ================\n");
        sb.append(String.format("Total Incidents: %d  (Pending Queue Size: %d)%n", allInc.size(), pendingQueue.size()));
        sb.append(String.format("By Priority : CRITICAL=%d | HIGH=%d | MEDIUM=%d | LOW=%d%n",
                critical, high, medium, low));
        sb.append(String.format("By Status   : PENDING=%d | ASSIGNED=%d | IN_PROGRESS=%d | COMPLETED=%d%n",
                pending, assigned, inProgress, completed));
        sb.append("-----------------------------------------------------------\n");
        sb.append("Rescue Team Workload & Availability:\n");
        if (allTeams.isEmpty()) {
            sb.append("  No rescue teams registered.\n");
        } else {
            for (RescueTeam t : allTeams) {
                sb.append(String.format("  - %s (%s): %s | Active Load: %d/%d%n",
                        t.getTeamId(), t.getTeamName(), t.getStatus(),
                        t.getActiveWorkload(), t.getMaxCapacity()));
            }
        }
        sb.append("===========================================================\n");
        return sb.toString();
    }

    private Incident getIncidentOrThrow(String incidentId) throws InvalidInputException {
        if (incidentId == null || incidentId.trim().isEmpty()) {
            throw new InvalidInputException("Incident ID is required.");
        }
        Incident incident = incidents.get(incidentId.trim().toUpperCase());
        if (incident == null) {
            throw new InvalidInputException("Incident not found: " + incidentId);
        }
        return incident;
    }

    private RescueTeam getTeamOrThrow(String teamId) throws InvalidInputException {
        if (teamId == null || teamId.trim().isEmpty()) {
            throw new InvalidInputException("Team ID is required.");
        }
        RescueTeam team = teams.get(teamId.trim().toUpperCase());
        if (team == null) {
            throw new InvalidInputException("Rescue team not found: " + teamId);
        }
        return team;
    }

    private String generateUniqueIncidentId() {
        String id;
        do {
            id = "INC-" + incidentCounter.incrementAndGet();
        } while (incidents.containsKey(id));
        return id;
    }

    public void shutdown() {
        dispatchExecutor.shutdown();
        try {
            if (!dispatchExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                dispatchExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            dispatchExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}