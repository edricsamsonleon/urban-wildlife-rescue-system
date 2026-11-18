package concurrency;

import java.util.concurrent.locks.ReentrantLock;

public class AssignmentLockManager {
    private final ReentrantLock allocationLock = new ReentrantLock(true);

    public void lock() {
        allocationLock.lock();
    }

    public void unlock() {
        if (allocationLock.isHeldByCurrentThread()) {
            allocationLock.unlock();
        }
    }

    public boolean isLockedByCurrentThread() {
        return allocationLock.isHeldByCurrentThread();
    }
}