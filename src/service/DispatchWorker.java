package service;

import model.Incident;

import java.util.concurrent.Callable;

public class DispatchWorker implements Callable<String> {
    private final Incident incident;
    private final RescueService rescueService;

    public DispatchWorker(Incident incident, RescueService rescueService) {
        this.incident = incident;
        this.rescueService = rescueService;
    }

    @Override
    public String call() throws Exception {
        rescueService.calculatePriority(incident);
        rescueService.persistIncidentState(incident);
        rescueService.enqueueIncident(incident);
        rescueService.processPendingQueue();

        String assigned = incident.getAssignedTeamId().isEmpty()
                ? "Pending Assignment (no eligible team currently free)"
                : "Assigned to " + incident.getAssignedTeamId();

        return String.format("[%s] %s -> Priority: %s (%d/100) | %s",
                Thread.currentThread().getName(),
                incident.getIncidentId(),
                incident.getPriorityLevel(),
                incident.getPriorityScore(),
                assigned);
    }
}