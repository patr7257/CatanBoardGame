# CLAUDE.md, CatanBoardGame

JavaFX implementation of the board game Catan (Settlers of Catan), built as a
university software project. Java 21 + Maven for the desktop app and tests; the
same game also runs in a browser via JPro (which injects its own patched JavaFX
and needs JDK 24+, we use JDK 25).

## What this is

A single player Catan game against AI opponents, played through a JavaFX UI.
It implements the core Catan rules: hex board setup, resource production on
dice rolls, building roads/settlements/cities, trading, development cards,
the robber, longest road, and biggest army. The same JavaFX app is also served
in a browser via JPro (jpro.one), no rewrite of the game; see "Run in the
browser" below and `DEPLOY.md`. It is deployed live at `catan.patrickrobel.dk`
(hosted on Dokploy, built from the `Dockerfile` as a `jpro:release` image).

A hard concurrency cap keeps one game running at a time on the shared VPS:
`GameConfig.MAX_CONCURRENT_GAMES` (+ `ConcurrentGameLimiter`) is enforced in
`CatanBoardGameApp.start()/stop()`, and `src/main/resources/jpro.conf` reaps an
abandoned browser session (`closeOnDisconnectAfter`) so the slot frees quickly.
Player-count limits live in `GameConfig` too. Raise these to allow more.

## Layout

- `pom.xml`, `mvnw` / `mvnw.cmd` - Maven build. `javafx-maven-plugin` runs the
  desktop app; `jpro-maven-plugin` (`jpro:run`) serves it in a browser. Java
  source/target level 21.
- `src/main/java/module-info.java` - Java module descriptor (`javafx.controls`,
  `javafx.fxml`, `java.desktop`, `jpro.webapi`).
- `src/main/java/org/example/catanboardgameapp/` - core game model and logic:
  - `CatanBoardGameApp.java` - JavaFX `Application` entry point.
  - `Board.java`, `Tile.java`, `Vertex.java`, `Edge.java`, `Harbor.java` - board
    graph (tiles, corners, edges, harbors).
  - `Player.java`, `Resource.java`, `DevelopmentCard.java` - player state and
    cards.
  - `Gameplay.java` - main turn/game flow orchestration.
  - `Robber.java`, `LongestRoadManager.java`, `BiggestArmyManager.java` - rule
    subsystems.
  - `AIOpponent.java` - AI player decision logic (building, trading, robber
    placement).
  - `DrawOrDisplay.java` - board and piece rendering helpers.
  - `BuildResult.java` - result type for build actions.
  - `GameConfig.java` - central game-wide limits (`MIN_PLAYERS`/`MAX_PLAYERS`,
    `MAX_CONCURRENT_GAMES`).
  - `ConcurrentGameLimiter.java` - thread-safe cap on concurrent games.
- `src/main/java/org/example/catanboardgameviews/` - JavaFX views:
  `CatanBoardGameView.java` (main game screen), `MenuView.java` (main menu).
- `src/main/java/org/example/controller/` - JavaFX controllers wiring views to
  game logic: `GameController.java`, `BuildController.java`,
  `TradeController.java`, `TurnController.java`.
- `src/main/resources/` - images used by the UI (`Icons/`, `backgrounds/`,
  `UI/`, `dice/`), plus `jpro.conf` (JPro session-reaping config).
- `src/test/java/org/example/catanboardgameapp/` - JUnit 5 and Mockito tests:
  `GameplayTest.java`, `AITester.java`, `ConcurrentGameLimiterTest.java`.

## Build, run, test

Requires Java 21 for the desktop build and tests, and internet access on first
run to resolve Maven and JavaFX dependencies.

Build:

```
./mvnw compile
```

Run the desktop app (uses the `javafx-maven-plugin` `default-cli` execution,
main class `org.example.catanboardgameapp.CatanBoardGameApp`):

```
./mvnw javafx:run
```

Run tests:

```
./mvnw test
```

On Windows use `mvnw.cmd` instead of `./mvnw`.

## Run in the browser (JPro)

JPro serves the real JavaFX app in a browser (no rewrite). It injects its own
patched JavaFX 26, which needs a JDK 24+ runtime, so run this under JDK 25 (the
desktop build and tests stay on Java 21):

```
JAVA_HOME=<jdk-25> ./mvnw jpro:run
```

Then open http://localhost:8080. Hosting on Dokploy: see `Dockerfile` +
`DEPLOY.md`.

## Notes

- The desktop UI is JavaFX, so a headless CI environment without a display will
  not be able to launch it; tests target the model/logic classes, not the views.
  (JPro renders headless via Monocle, which is how the browser build works on a
  server.)
- Tests mock `GameController`; on JDK 25 the Surefire `argLine` passes
  `-Dnet.bytebuddy.experimental=true` so Mockito can instrument on the newer JDK.
- `controlsfx` and `bootstrapfx` are declared as Maven dependencies but their
  module requirements are commented out in `module-info.java`; they are not
  currently wired into the module.
