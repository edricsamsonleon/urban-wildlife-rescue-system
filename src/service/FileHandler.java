package service;

import exception.FileStorageException;
import model.Incident;
import model.IncidentStatus;
import model.PriorityLevel;
import model.RescueTeam;
import model.TeamStatus;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FileHandler {
    private static final String INCIDENTS_PATH = "data/incidents.csv";
    private static final String TEAMS_PATH = "data/teams.csv";

    private static final String INCIDENTS_HEADER =
            "incidentId,animalType,condition,location,animalCount,immediateDanger,specialRequirements,reporterName,reporterContact,createdAt,priorityScore,priorityLevel,status,assignedTeamId";
    private static final String TEAMS_HEADER =
            "teamId,teamName,members,animalCategories,rescueSkills,equipment,status,maxCapacity,activeWorkload";

    private final Object fileLock = new Object();

    public List<RescueTeam> loadTeams() throws FileStorageException {
        synchronized (fileLock) {
            ensureFile(TEAMS_PATH, TEAMS_HEADER);
            List<RescueTeam> teams = new ArrayList<>();

            for (String[] row : readCsvRows(TEAMS_PATH)) {
                if (row.length < 9) {
                    System.err.println("[WARN] Skipping malformed team CSV row: " + Arrays.toString(row));
                    continue;
                }
                try {
                    String teamId = row[0].trim();
                    String teamName = row[1].trim();
                    List<String> members = splitList(row[2]);
                    Set<String> animals = splitSet(row[3]);
                    Set<String> skills = splitSet(row[4]);
                    Set<String> equipment = splitSet(row[5]);
                    TeamStatus status = TeamStatus.valueOf(row[6].trim().toUpperCase());
                    int maxCapacity = Integer.parseInt(row[7].trim());
                    int activeWorkload = Integer.parseInt(row[8].trim());

                    teams.add(new RescueTeam(teamId, teamName, members, animals, skills,
                            equipment, status, maxCapacity, activeWorkload));
                } catch (Exception e) {
                    System.err.println("[WARN] Skipping invalid team record (" + row[0] + "): " + e.getMessage());
                }
            }
            return teams;
        }
    }

    public void saveTeams(Collection<RescueTeam> teams) throws FileStorageException {
        synchronized (fileLock) {
            List<RescueTeam> sorted = new ArrayList<>(teams);
            sorted.sort(Comparator.comparing(RescueTeam::getTeamId));

            List<String[]> rows = new ArrayList<>();
            for (RescueTeam t : sorted) {
                rows.add(new String[]{
                        t.getTeamId(),
                        t.getTeamName(),
                        String.join(";", t.getMembers()),
                        String.join(";", t.getAnimalCategories()),
                        String.join(";", t.getRescueSkills()),
                        String.join(";", t.getEquipment()),
                        t.getStatus().name(),
                        String.valueOf(t.getMaxCapacity()),
                        String.valueOf(t.getActiveWorkload())
                });
            }
            writeCsvRowsAtomically(TEAMS_PATH, TEAMS_HEADER, rows);
        }
    }

    public List<Incident> loadIncidents() throws FileStorageException {
        synchronized (fileLock) {
            ensureFile(INCIDENTS_PATH, INCIDENTS_HEADER);
            List<Incident> incidents = new ArrayList<>();

            for (String[] row : readCsvRows(INCIDENTS_PATH)) {
                if (row.length < 14) {
                    System.err.println("[WARN] Skipping malformed incident CSV row: " + Arrays.toString(row));
                    continue;
                }
                try {
                    String id = row[0].trim();
                    String animalType = row[1].trim();
                    String condition = row[2].trim();
                    String location = row[3].trim();
                    int count = Integer.parseInt(row[4].trim());
                    boolean danger = Boolean.parseBoolean(row[5].trim());
                    List<String> reqs = splitList(row[6]);
                    String reporterName = row[7].trim();
                    String reporterContact = row[8].trim();
                    LocalDateTime createdAt = LocalDateTime.parse(row[9].trim());
                    int score = Integer.parseInt(row[10].trim());
                    PriorityLevel level = PriorityLevel.valueOf(row[11].trim().toUpperCase());
                    IncidentStatus status = IncidentStatus.valueOf(row[12].trim().toUpperCase());
                    String assignedTeamId = row[13].trim();

                    Incident incident = new Incident(id, animalType, condition, location, count,
                            danger, reqs, reporterName, reporterContact, createdAt);
                    incident.setPriorityScore(score);
                    incident.setPriorityLevel(level);
                    incident.setStatus(status);
                    incident.setAssignedTeamId(assignedTeamId);

                    incidents.add(incident);
                } catch (Exception e) {
                    System.err.println("[WARN] Skipping invalid incident record (" + row[0] + "): " + e.getMessage());
                }
            }
            return incidents;
        }
    }

    public void saveIncidents(Collection<Incident> incidents) throws FileStorageException {
        synchronized (fileLock) {
            List<Incident> sorted = new ArrayList<>(incidents);
            sorted.sort(Comparator.comparing(Incident::getIncidentId));

            List<String[]> rows = new ArrayList<>();
            for (Incident inc : sorted) {
                rows.add(new String[]{
                        inc.getIncidentId(),
                        inc.getAnimalType(),
                        inc.getCondition(),
                        inc.getLocation(),
                        String.valueOf(inc.getAnimalCount()),
                        String.valueOf(inc.isImmediateDanger()),
                        String.join(";", inc.getSpecialRequirements()),
                        inc.getReporterName(),
                        inc.getReporterContact(),
                        inc.getCreatedAt().toString(),
                        String.valueOf(inc.getPriorityScore()),
                        inc.getPriorityLevel().name(),
                        inc.getStatus().name(),
                        inc.getAssignedTeamId() != null ? inc.getAssignedTeamId() : ""
                });
            }
            writeCsvRowsAtomically(INCIDENTS_PATH, INCIDENTS_HEADER, rows);
        }
    }

    private void ensureFile(String filePath, String header) throws FileStorageException {
        try {
            Path path = Paths.get(filePath);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            if (!Files.exists(path) || Files.size(path) == 0) {
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, false))) {
                    writer.write(header);
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            throw new FileStorageException("Unable to initialize storage file: " + filePath, e);
        }
    }

    private List<String[]> readCsvRows(String filePath) throws FileStorageException {
        List<String[]> rows = new ArrayList<>();
        File file = new File(filePath);
        if (!file.exists()) return rows;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            boolean isHeader = true;
            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }
                if (line.trim().isEmpty()) continue;
                rows.add(parseCsvLine(line));
            }
            return rows;
        } catch (IOException e) {
            throw new FileStorageException("Failed to read CSV file: " + filePath, e);
        }
    }

    private void writeCsvRowsAtomically(String filePath, String header, List<String[]> rows) throws FileStorageException {
        try {
            Path target = Paths.get(filePath);
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Path temp = Paths.get(filePath + ".tmp");

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(temp.toFile(), false))) {
                writer.write(header);
                writer.newLine();
                for (String[] row : rows) {
                    writer.write(formatCsvRow(row));
                    writer.newLine();
                }
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new FileStorageException("Failed to save CSV file: " + filePath, e);
        }
    }

    private String formatCsvRow(String[] fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(escapeCsv(fields[i]));
        }
        return sb.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String[] parseCsvLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '\"') {
                    current.append('\"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                tokens.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        tokens.add(current.toString());
        return tokens.toArray(new String[0]);
    }

    private List<String> splitList(String raw) {
        if (raw == null || raw.trim().isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.trim().split(";")));
    }

    private Set<String> splitSet(String raw) {
        if (raw == null || raw.trim().isEmpty()) return new HashSet<>();
        return new HashSet<>(Arrays.asList(raw.trim().split(";")));
    }
}