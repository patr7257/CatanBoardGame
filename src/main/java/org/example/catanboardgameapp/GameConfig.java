package org.example.catanboardgameapp;

// Central place for game-wide limits. Change a value here and it takes effect everywhere.
public final class GameConfig {

    // Total players allowed in a single match (humans + AI opponents).
    // The range is enforced in the Game Options menu (MenuView) and capped again by the
    // colour pool in Gameplay.initializeAllPlayers. If you raise MAX_PLAYERS above the number
    // of colours in that pool, add more colours there too.
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 6; // raise here to allow larger games

    // How many games may run at the same time across all browser sessions. Under JPro
    // (single server) every browser session is its own Application instance in one JVM,
    // and CatanBoardGameApp enforces this cap so the shared VPS is not overloaded.
    // Raise this (and size the VPS up) to allow more concurrent games.
    public static final int MAX_CONCURRENT_GAMES = 1;

    private GameConfig() {}
}
