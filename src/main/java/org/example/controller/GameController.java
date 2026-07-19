package org.example.controller;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.catanboardgameapp.AIOpponent;
import org.example.catanboardgameapp.Gameplay;
import org.example.catanboardgameapp.Player;
import org.example.catanboardgameviews.CatanBoardGameView;
import org.example.catanboardgameviews.MenuView;

public class GameController {

    private final Stage primaryStage;
    // Single persistent Scene for the whole app. JPro binds its browser input
    // mapping to one Scene; swapping the Stage's Scene breaks mouse coordinates,
    // so every screen change swaps this Scene's ROOT instead of the Scene.
    private Scene scene;
    // True only while the game board screen is showing, so the board key handler
    // (WASD / R / C / SPACE / ESC) stays inert on menu/options/credits screens.
    private boolean gameScreenActive = false;
    private CatanBoardGameView gameView;
    private TurnController turnController;
    private TradeController tradeController;
    private Gameplay gameplay;
    private BuildController buildController;
    private MenuView menuView;
    // Set by options menu; default true, do not make final
    private boolean shufflePlayers = true;

    //___________________________CONSTRUCTOR_________________________________//
    // Initialize with primary application stage
    public GameController(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    //___________________________SCENE / SCREEN SWAPPING____________________//
    // Called once at startup to hand the app's single Scene to the controller.
    public void setScene(Scene scene) {
        this.scene = scene;
    }
    public Scene getScene() {
        return scene;
    }
    // Swap the visible screen without replacing the Scene (JPro-safe).
    public void setRoot(Parent root) {
        if (scene != null) {
            scene.setRoot(root);
        }
    }
    public boolean isGameScreenActive() {
        return gameScreenActive;
    }

    //___________________________FUNCTIONS__________________________________//
    // Starts a new game with specified settings
    public void startGame(int playerCount, int boardSize, int easyAI, int medAI, int hardAI) {
        gameplay = new Gameplay(boardSize - 1, this);
        gameplay.setMenuView(this.menuView);

        // Add players
        gameplay.initializeAllPlayers(playerCount, easyAI, medAI, hardAI, shufflePlayers);
        gameplay.resetCounters();

        // Initialize controllers before creating view
        turnController = new TurnController(this);
        this.setTurnController(turnController);

        tradeController = new TradeController(this);
        this.setTradeController(tradeController);

        // Create view after controllers
        gameView = new CatanBoardGameView(gameplay, this, boardSize - 1);
        gameplay.setCatanBoardGameView(gameView);

        // Build UI
        gameView.buildGameUI();
        gameplay.initializeDevelopmentCards();

        setRoot(gameView.getRootNode());
        gameScreenActive = true;
        primaryStage.show();

        // Handle first player's initial placement
        Player currentPlayer = gameplay.getCurrentPlayer();
        if (gameplay.isInInitialPhase()) {
            if (currentPlayer instanceof AIOpponent ai) {
                ai.placeInitialSettlementAndRoad(gameplay, gameView.getBoardGroup());
            } else {
                gameView.prepareForHumanInitialPlacement(currentPlayer);
            }
        }
    }
    // Resets the current game state
    public void resetGame() {
        if (gameplay != null) {
            gameplay.stopAllAIThreads();  // Stop any active AI threads
            gameplay.resetCounters();
            gameplay = null;
        }
        if (gameView != null) {
            gameView = null;
        }
    }
    // Returns to the main menu
    public void returnToMenu(MenuView menuView) {
        gameScreenActive = false; // board key handler goes inert on the menu
        if (gameplay != null) {
            gameplay.pauseGame(false); // ensures all threads and state are halted
        }
        if (menuView != null) {
            menuView.showMainMenu();
        }
    }
    // Resumes an existing game session
    public void resumeGame() {
        if (gameplay == null || gameView == null) return;
        gameplay.resumeGame(false); // handles AI restart / treads
        gameplay.setCatanBoardGameView(gameView); // restore view reference
        setRoot(gameView.getRootNode()); // bring game view back (swap root, keep Scene)
        gameScreenActive = true;
    }

    //___________________________SETTERS__________________________________//
    public void setMenuView(MenuView menuView) {
        this.menuView = menuView;
        if (gameplay != null) {
            gameplay.setMenuView(menuView);}}

    public void setBuildController(BuildController buildController) {
        this.buildController = buildController;
    }
    public void setTradeController(TradeController tradeController) {
        this.tradeController = tradeController;
    }
    public void setTurnController(TurnController turnController) {
        this.turnController = turnController;
    }

    //___________________________GETTERS__________________________________//
    public Gameplay getGameplay() {
        return gameplay;
    }
    public TurnController getTurnController() {
        return turnController;
    }
    public TradeController getTradeController() {
        return tradeController;
    }
    public BuildController getBuildController() {
        return buildController;
    }
    public CatanBoardGameView getGameView() {
        return gameView;
    }
    public MenuView getMenuView() {
        return menuView;
    }

    //___________________________BOOLEAN__________________________________//
    // Returns true if a game session is currently active
    public boolean hasSavedSession() {
        return gameplay != null;
    }
}
