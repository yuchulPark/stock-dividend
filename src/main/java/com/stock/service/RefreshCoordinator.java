package com.stock.service;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
@Component
public class RefreshCoordinator {
    private final ReentrantLock[] locks = new ReentrantLock[128];
    private final ReentrantLock[] searchLocks = new ReentrantLock[128];
    public RefreshCoordinator() {
        for (int i = 0; i < locks.length; i++) { locks[i] = new ReentrantLock(); searchLocks[i] = new ReentrantLock(); }
    }
    // Bounded single-server locks. Re-read cache after acquiring the lock.
    public <T> T withLock(String key, Supplier<T> action) {
        // Asset refresh may acquire a search lock. Separate arrays prevent cross-stripe lock inversion.
        ReentrantLock[] group = key.startsWith("search:") ? searchLocks : locks;
        ReentrantLock lock = group[Math.floorMod(key.hashCode(), group.length)];
        lock.lock();
        try { return action.get(); }
        finally { lock.unlock(); }
    }
}
