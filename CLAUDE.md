# CLAUDE.md, CatanBoardGame

Desktop JavaFX implementation of the board game Catan (Settlers of Catan),
built as a university software project. Java, Maven, Zulu 17 JDK.

## What this is

A single player Catan game against AI opponents, played through a JavaFX UI.
It implements the core Catan rules: hex board setup, resource production on
dice rolls, building roads/settlements/cities, trading, development cards,
the robber, longest road, and biggest army.

## Layout

- `pom.xml`, `mvnw` / `mvnw.cmd` - Maven build, wraps JavaFX via
  `javafx-maven-plugin`. Java source/target level 17.
- `src/main/java/module-info.java` - Java module descriptor (`javafx.controls`,
  `javafx.fxml`, `java.desktop`).
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
- `src/main/java/org/example/catanboardgameviews/` - JavaFX views:
  `CatanBoardGameView.java` (main game screen), `MenuView.java` (main menu).
- `src/main/java/org/example/controller/` - JavaFX controllers wiring views to
  game logic: `GameController.java`, `BuildController.java`,
  `TradeController.java`, `TurnController.java`.
- `src/main/resources/` - FXML (`hello-view.fxml`), and images used by the UI
  (`Icons/`, `backgrounds/`, `UI/`, `dice/`).
- `src/test/java/org/example/catanboardgameapp/` - JUnit 5 and Mockito tests:
  `GameplayTest.java`, `AITester.java`.

## Build, run, test

Requires Java 17 (Zulu 17 JDK recommended) and internet access on first run
to resolve Maven and JavaFX dependencies.

Build:

```
./mvnw compile
```

Run the app (uses the `javafx-maven-plugin` `default-cli` execution, main
class `org.example.catanboardgameapp.CatanBoardGameApp`):

```
./mvnw javafx:run
```

Run tests:

```
./mvnw test
```

On Windows use `mvnw.cmd` instead of `./mvnw`.

## Notes

- The UI is JavaFX (`javafx-controls`, `javafx-fxml`, `javafx-swing`), so a
  headless CI environment without a display will not be able to launch the
  app; tests target the model/logic classes, not the views.
- `controlsfx` and `bootstrapfx` are declared as Maven dependencies but their
  module requirements are commented out in `module-info.java`; they are not
  currently wired into the module.
