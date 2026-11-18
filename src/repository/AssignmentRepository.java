package repository;

import exception.FileOperationException;
import model.Assignment;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class AssignmentRepository {
    private static final String FILE_PATH = "data/assignments.csv";
    private static final String LOG_PATH = "data/assignment_log.csv";
    private static final String HEADER = "assignmentId,incidentId,teamId,assignedTime,completedTime,status";

    private final CSVFileManager csvFileManager;
    private final ConcurrentHashMap<String, Assignment> assignments = new ConcurrentHashMap<>();
    private final AtomicInteger idSequence = new AtomicInteger(1000);

    public AssignmentRepository(CSVFileManager csvFileManager) {
        this.csvFileManager = csvFileManager;
    }

    public void loadFromCsv() throws FileOperationException {
        csvFileManager.ensureFileWithHeader(FILE_PATH, HEADER);
        List<String[]> rows = csvFileManager.readRecords(FILE_PATH);
        assignments.clear();

        int maxNumericId = 1000;
        for (String[] row : rows) {
            if (row.length < 6) {
                System.err.println("[WARN] Skipping malformed assignment CSV row: " + Arrays.toString(row));
                continue;
            }
            try {
                String assignmentId = row[0].trim();
                String incidentId = row[1].trim();
                String teamId = row[2].trim();
                LocalDateTime assignedTime = LocalDateTime.parse(row[3].trim());
                LocalDateTime completedTime = row[4].trim().isEmpty() ? null : LocalDateTime.parse(row[4].trim());
                String status = row[5].trim();

                Assignment assignment = new Assignment(assignmentId, incidentId, teamId, assignedTime, completedTime, status);
                assignments.put(assignmentId, assignment);

                if (assignmentId.startsWith("ASN-")) {
                    int num = Integer.parseInt(assignmentId.substring(4));
                    if (num > maxNumericId) maxNumericId = num;
                }
            } catch (Exception e) {
                System.err.println("[WARN] Skipping corrupt assignment record (" + row[0] + "): " + e.getMessage());
            }
        }
        idSequence.set(maxNumericId);
    }

    public String generateNextId() {
        String candidate;
        do {
            candidate = "ASN-" + idSequence.incrementAndGet();
        } while (assignments.containsKey(candidate));
        return candidate;
    }

    public void logEvent(String eventType, String incidentId, String teamId, String details) throws FileOperationException {
        csvFileManager.appendEventLog(LOG_PATH, eventType, incidentId, teamId, details);
    }

    public void add(Assignment assignment) throws FileOperationException {
        assignments.put(assignment.getAssignmentId(), assignment);
        saveAll();
    }

    public void update(Assignment assignment) throws FileOperationException {
        assignments.put(assignment.getAssignmentId(), assignment);
        saveAll();
    }

    public void saveAll() throws FileOperationException {
        List<Assignment> sorted = new ArrayList<>(assignments.values());
        sorted.sort(Comparator.comparing(Assignment::getAssignmentId));

        List<String[]> rows = new ArrayList<>();
        for (Assignment a : sorted) {
            rows.add(new String[]{
                    a.getAssignmentId(),
                    a.getIncidentId(),
                    a.getTeamId(),
                    a.getAssignedTime().toString(),
                    a.getCompletedTime() != null ? a.getCompletedTime().toString() : "",
                    a.getStatus()
            });
        }
        csvFileManager.writeRecordsAtomically(FILE_PATH, HEADER, rows);
    }

    public Optional<Assignment> findActiveByIncidentId(String incidentId) {
        return assignments.values().stream()
                .filter(a -> a.getIncidentId().equalsIgnoreCase(incidentId) && "ACTIVE".equalsIgnoreCase(a.getStatus()))
                .findFirst();
    }

    public Map<String, Integer> countActiveAssignmentsByTeam() {
        Map<String, Integer> counts = new HashMap<>();
        for (Assignment a : assignments.values()) {
            if ("ACTIVE".equalsIgnoreCase(a.getStatus())) {
                counts.merge(a.getTeamId(), 1, Integer::sum);
            }
        }
        return counts;
    }

    public List<Assignment> findAll() {
        return new ArrayList<>(assignments.values());
    }
}