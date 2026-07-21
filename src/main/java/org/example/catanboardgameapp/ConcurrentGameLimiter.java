package org.example.catanboardgameapp;

import java.util.concurrent.atomic.AtomicInteger;

// Caps how many games run at the same time (see GameConfig.MAX_CONCURRENT_GAMES).
// Thread-safe: under JPro one JVM serves every browser session, and sessions start/stop
// on different threads, so the count is kept in an AtomicInteger.
public class ConcurrentGameLimiter {

    private final int maxGames;
    private final AtomicInteger active = new AtomicInteger(0);

    public ConcurrentGameLimiter(int maxGames) {
        this.maxGames = maxGames;
    }

    // Try to take a slot for a new game. Returns true if a slot was acquired (the caller
    // must later call release()), or false if the limit is already reached.
    public boolean tryAcquire() {
        if (active.incrementAndGet() > maxGames) {
            active.decrementAndGet();
            return false;
        }
        return true;
    }

    // Free a previously acquired slot. Never goes below zero.
    public void release() {
        active.updateAndGet(n -> n > 0 ? n - 1 : 0);
    }

    // Number of games currently holding a slot.
    public int active() {
        return active.get();
    }
}
