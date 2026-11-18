package concurrency;

import algorithm.PriorityCalculator;
import model.Assignment;
import model.Incident;
import repository.IncidentRepository;

import java.util.Optional;
import java.util.concurrent.Callable;

public class IncidentProcessor implements Callable<Optional<Assignment>> {
    private final Incident incident;
    private final PriorityCalculator priorityCalculator;
    private final IncidentRepository incidentRepository;
    private final ProcessingManager processingManager;

    public IncidentProcessor(Incident incident,
                             PriorityCalculator priorityCalculator,
                             IncidentRepository incidentRepository,
                             ProcessingManager processingManager) {
        this.incident = incident;
        this.priorityCalculator = priorityCalculator;
        this.incidentRepository = incidentRepository;
        this.processingManager = processingManager;
    }

    @Override
    public Optional<Assignment> call() throws Exception {
        priorityCalculator.calculateAndAssign(incident);
        incidentRepository.update(incident);
        processingManager.enqueueIncident(incident);
        return processingManager.dispatchPendingIncidentsAndFind(incident.getIncidentId());
    }
}