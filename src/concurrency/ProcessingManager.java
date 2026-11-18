package concurrency;

import algorithm.AllocationEngine;
import algorithm.PriorityCalculator;
import exception.AssignmentException;
import exception.FileOperationException;
import model.Assignment;
import model.Incident;
import model.IncidentStatus;
import repository.AssignmentRepository;
import repository.IncidentRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

public class ProcessingManager {
    private final ExecutorService executorService;
    private final PriorityBlockingQueue<Incident> priorityQueue;
    private final PriorityCalculator priorityCalculator;
    private final AllocationEngine allocationEngine;
    private final AssignmentLockManager lockManager;
    private final IncidentRepository incidentRepository;
    private final AssignmentRepository assignmentRepository;

    public ProcessingManager(int workerThreads,
                             PriorityCalculator priorityCalculator,
                             AllocationEngine allocationEngine,
                             AssignmentLockManager lockManager,
                             IncidentRepository incidentRepository,
                             AssignmentRepository assignmentRepository) {
        this.executorService = Executors.newFixedThreadPool(Math.max(2, workerThreads));
        this.priorityCalculator = priorityCalculator;
        this.allocationEngine = allocationEngine;
        this.lockManager = lockManager;
        this.incidentRepository = incidentRepository;
        this.assignmentRepository = assignmentRepository;
        this.priorityQueue = new PriorityBlockingQueue<>(16, priorityCalculator.getIncidentPriorityComparator());
    }

    public void initializeQueueFromRepository() {
        priorityQueue.clear();
        List<Incident> pending = incidentRepository.findPendingIncidents();
        for (Incident inc : pending) {
            if (inc.getPriorityScore() == 0) {
                priorityCalculator.calculateAndAssign(inc);
            }
            priorityQueue.offer(inc);
        }
    }

    public void enqueueIncident(Incident incident) {
        if (incident != null && incident.getStatus() == IncidentStatus.PENDING_ASSIGNMENT) {
            priorityQueue.removeIf(existing -> existing.getIncidentId().equals(incident.getIncidentId()));
            priorityQueue.offer(incident);
        }
    }

    public Future<Optional<Assignment>> submitIncidentAsync(Incident incident) {
        IncidentProcessor task = new IncidentProcessor(
                incident, priorityCalculator, incidentRepository, this);
        return executorService.submit(task);
    }

    public Future<Integer> triggerReassessmentAsync() {
        return executorService.submit(this::processPendingQueue);
    }

    public int processPendingQueue() {
        lockManager.lock();
        int assignedCount = 0;
        List<Incident> unallocated = new ArrayList<>();

        try {
            Incident current;
            while ((current = priorityQueue.poll()) != null) {
                if (current.getStatus() != IncidentStatus.PENDING_ASSIGNMENT) {
                    continue;
                }
                try {
                    Optional<Assignment> result = allocationEngine.allocateBestTeam(current);
                    if (result.isPresent()) {
                        assignedCount++;
                    } else {
                        unallocated.add(current);
                    }
                } catch (AssignmentException | FileOperationException e) {
                    System.err.println("[WARN] Allocation retry deferred for "
                            + current.getIncidentId() + ": " + e.getMessage());
                    unallocated.add(current);
                }
            }

            for (Incident remaining : unallocated) {
                if (remaining.getStatus() == IncidentStatus.PENDING_ASSIGNMENT) {
                    priorityQueue.offer(remaining);
                }
            }
        } finally {
            lockManager.unlock();
        }
        return assignedCount;
    }

    public Optional<Assignment> dispatchPendingIncidentsAndFind(String targetIncidentId) {
        processPendingQueue();
        return assignmentRepository.findActiveByIncidentId(targetIncidentId);
    }

    public int getPendingQueueSize() {
        return priorityQueue.size();
    }

    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}