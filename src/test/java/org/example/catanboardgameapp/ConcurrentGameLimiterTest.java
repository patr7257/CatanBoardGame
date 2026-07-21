package org.example.catanboardgameapp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Verifies the concurrent-game cap logic that CatanBoardGameApp uses to allow only
// GameConfig.MAX_CONCURRENT_GAMES games at a time.
class ConcurrentGameLimiterTest {

    @Test
    void allowsOneGameThenRejectsUntilReleased() {
        ConcurrentGameLimiter limiter = new ConcurrentGameLimiter(1);

        assertTrue(limiter.tryAcquire(), "first game should be allowed");
        assertFalse(limiter.tryAcquire(), "second game should be rejected while one is active");
        assertEquals(1, limiter.active());

        limiter.release();
        assertEquals(0, limiter.active());
        assertTrue(limiter.tryAcquire(), "after release a new game should be allowed");
    }

    @Test
    void honoursAHigherLimit() {
        ConcurrentGameLimiter limiter = new ConcurrentGameLimiter(3);

        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertEquals(3, limiter.active());

        assertFalse(limiter.tryAcquire(), "fourth game should be rejected at limit 3");

        limiter.release();
        assertEquals(2, limiter.active());
        assertTrue(limiter.tryAcquire(), "a freed slot should be reusable");
    }

    @Test
    void releaseNeverGoesBelowZero() {
        ConcurrentGameLimiter limiter = new ConcurrentGameLimiter(1);

        limiter.release(); // release with nothing acquired
        assertEquals(0, limiter.active());
        assertTrue(limiter.tryAcquire(), "limiter should still work after an extra release");
    }
}
