package org.example.catanboardgameapp;

import javafx.scene.control.*;
import javafx.scene.paint.Color;
import org.example.catanboardgameviews.CatanBoardGameView;
import java.util.*;
import org.example.catanboardgameviews.MenuView;
import org.example.controller.GameController;
import static org.example.catanboardgameapp.DevelopmentCard.DevelopmentCardType.*;

// Handles all game play relevant logic, all rules being enforced, functions to run the game etc.
// Also sets up and directs multiple other class
public class Gameplay {

    //__________________________CONFIG & VIEWS_____________________________//
    private final GameController gameController;
    private final int boardRadius;
    private DrawOrDisplay drawOrDisplay;
    private CatanBoardGameView catanBoardGameView;
    private MenuView menuView;

    //__________________________PLAYER STATE_____________________________//
    private final List<Player> playerList = new ArrayList<>();
    private int currentPlayerIndex;
    private Player currentPlayer;

    //__________________________TURN & PHASE CONTROL_____________________________//
    private boolean initialPhase = true;               // True until all initial placements are done
    private boolean forwardOrder = true;               // Direction of placement turn order
    private boolean hasRolledThisTurn = false;         // Tracks whether dice were rolled this turn
    private boolean waitingForInitialRoad = false;     // Set to true after placing initial settlement
    private volatile boolean gamePaused = false;       // Used to pause/resume game (e.g., for menu)
    private Thread activeAIThread;
    private boolean gameOver = false;                  // Set true when someone reaches victory

    //__________________________BOARD & GAME DATA_____________________________//
    private Board board;
    private Vertex lastInitialSettlement = null;       // Used for checking where to place road
    private DevelopmentCard developmentCard;

    // All available development cards in the deck
    private final DevelopmentCard.DevelopmentCardType[] developmentCardTypes = {
            MONOPOLY, MONOPOLY,
            YEAROFPLENTY, YEAROFPLENTY,
            ROADBUILDING, ROADBUILDING,
            VICTORYPOINT, VICTORYPOINT, VICTORYPOINT, VICTORYPOINT, VICTORYPOINT,
            KNIGHT, KNIGHT, KNIGHT, KNIGHT, KNIGHT, KNIGHT,
            KNIGHT, KNIGHT, KNIGHT, KNIGHT, KNIGHT, KNIGHT, KNIGHT
    };


    private List<DevelopmentCard.DevelopmentCardType> shuffledDevelopmentCards;

    private final LongestRoadManager longestRoadManager;
    private final BiggestArmyManager biggestArmy;

    private int lastRolledDie1;
    private int lastRolledDie2;
    private int turnCounter = 0;    // Used for crash protection & stats

    //__________________________CONSTRUCTOR_____________________________//
    // Create a new game session
    public Gameplay(int boardRadius, GameController gameController) {
        this.drawOrDisplay = new DrawOrDisplay(boardRadius, this);
        this.boardRadius = boardRadius;
        this.gameController = gameController;
        this.longestRoadManager = new LongestRoadManager(this);
        this.biggestArmy = new BiggestArmyManager(this);
    }
    //________________________INITIALIZE_______________________________//
    // Initializes and shuffles the development card deck
    public void initializeDevelopmentCards() {
        if (catanBoardGameView == null) {
            throw new IllegalStateException("CatanBoardGameView must be set before initializing development cards.");
        }

        // Setup development card handler with game reference
        this.developmentCard = new DevelopmentCard(
                this,
                playerList,
                catanBoardGameView,
                gameController.getTradeController()
        );

        // Shuffle the development card deck using enum values directly
        List<DevelopmentCard.DevelopmentCardType> shuffledDevCards =
                new ArrayList<>(Arrays.asList(developmentCardTypes));
        Collections.shuffle(shuffledDevCards);
        this.shuffledDevelopmentCards = shuffledDevCards;
    }

    // Sets up all human and AI players and assigns them a color and ID
    public void initializeAllPlayers(int humanCount, int aiEasy, int aiMedium, int aiHard,  boolean shuffle) {
        playerList.clear();
        List<Color> colors = new ArrayList<>(List.of(
                Color.RED, Color.BLUE, Color.GREEN, Color.DARKORANGE, Color.PURPLE, Color.YELLOW
        ));

        int idCounter = 1;

        // Add human players
        for (int i = 0; i < humanCount && !colors.isEmpty(); i++) {
            playerList.add(new Player(idCounter++, colors.remove(0), this));
        }
        AIOpponent.ThinkingSpeed selectedSpeed = menuView.getSelectedAISpeed(); // <- retrieve selected speed

        // Add AI players by difficulty level
        for (int i = 0; i < aiEasy && !colors.isEmpty(); i++) {
            AIOpponent ai = new AIOpponent(idCounter++, colors.remove(0), AIOpponent.StrategyLevel.EASY, this);
            ai.setThinkingSpeed(selectedSpeed);
            playerList.add(ai);
        }
        for (int i = 0; i < aiMedium && !colors.isEmpty(); i++) {
            AIOpponent ai = new AIOpponent(idCounter++, colors.remove(0), AIOpponent.StrategyLevel.MEDIUM, this);
            ai.setThinkingSpeed(selectedSpeed);
            playerList.add(ai);
        }
        for (int i = 0; i < aiHard && !colors.isEmpty(); i++) {
            AIOpponent ai = new AIOpponent(idCounter++, colors.remove(0), AIOpponent.StrategyLevel.HARD, this);
            ai.setThinkingSpeed(selectedSpeed);
            playerList.add(ai);
        }

        // Optional: shuffle player list for random turn order
        if (shuffle){
        Collections.shuffle(playerList);

        // Reset player IDs after shuffle
        for (int i = 0; i < playerList.size(); i++) {
            playerList.get(i).setPlayerId(i + 1);
        } }
        // Set first player
        if (!playerList.isEmpty()) {
            currentPlayerIndex = 0;
            currentPlayer = playerList.get(0);
        }
    }

    //____________________________TURN MANAGEMENT______________________________//
    // Advances the game to the next player's turn
    public void nextPlayerTurn() {
        stopAllAIThreads(); // Stop any in-progress AI thread before advancing
        crashGameIfMaxTurnsExceeded(500, turnCounter); // Safety check against infinite loops
        startOfTurnEffects(); // Rotate player and refresh sidebar
        // Initial Phase
        if (initialPhase) {
            if (waitingForInitialRoad) {
                catanBoardGameView.logToGameLog("Player " + currentPlayer.getPlayerId() + " must place a road.");
                return;
            }
            // Move to next player (or reverse direction after first loop)
            currentPlayerIndex = forwardOrder ? currentPlayerIndex + 1 : currentPlayerIndex - 1;
            // If forward loop finished, switch to backward loop
            if (forwardOrder && currentPlayerIndex >= playerList.size()) {
                currentPlayerIndex = playerList.size() - 1;
                forwardOrder = false;
                if (currentPlayer instanceof AIOpponent ai) {
                    startAIThread(ai); // Safe AI startup
                } else {
                    catanBoardGameView.showDiceButton();
                }
            // If backward loop finished, start main phase
            } else if (!forwardOrder && currentPlayerIndex < 0) {
                // Transition from initial to main phase
                initialPhase = false;
                forwardOrder = true;
                currentPlayerIndex = 0;
                currentPlayer = playerList.get(currentPlayerIndex);
                lastInitialSettlement = null;

                // Log and prepare first player’s turn
                catanBoardGameView.runOnFX(() -> {
                    catanBoardGameView.logToGameLog("All initial placements complete. Starting first turn...");
                    catanBoardGameView.logToGameLog("_____________________________________________________\n");
                    if (currentPlayer instanceof AIOpponent ai) {
                        startAIThread(ai); // Safe AI startup
                    } else {
                        catanBoardGameView.showDiceButton();
                    }
                });
                return;
            }
            // Prepare next player for settlement + road placement
            currentPlayer = playerList.get(currentPlayerIndex);
            lastInitialSettlement = null;

            if (currentPlayer instanceof AIOpponent ai) {
                startAIThread(ai); // AI places settlement/road automatically
            } else {
                catanBoardGameView.prepareForHumanInitialPlacement(currentPlayer);
            }
            return;
        }
        // Main Game Phase
        waitingForInitialRoad = false;
        lastInitialSettlement = null;
        if (currentPlayer instanceof AIOpponent ai) {
            startAIThread(ai);
        } else {
            catanBoardGameView.showDiceButton();
        }
    }

    // Helper logic called at the start of every turn
    private void startOfTurnEffects() {
        if (!initialPhase) {
            if (getCurrentPlayer() instanceof AIOpponent && !isGamePaused()) {
                catanBoardGameView.logToGameLog("AI " + getCurrentPlayer() +  " has ended their turn.");
            }
            else {
                if (!isGamePaused()) catanBoardGameView.logToGameLog(getCurrentPlayer() +  " has ended their turn.");
            }
            if (!isGamePaused())catanBoardGameView.logToGameLog("_____________________________________________________\n");
            // Rotate to next player
            currentPlayerIndex = (currentPlayerIndex + 1) % playerList.size();
            currentPlayer = playerList.get(currentPlayerIndex);
        }

        // Update sidebar and hide buttons
        catanBoardGameView.runOnFX(() -> catanBoardGameView.refreshSidebar());
        catanBoardGameView.hideTurnButton();
        setHasRolledThisTurn(false);

        // Center the board if any human players are present
        if (hasHumanPlayers()) {
            catanBoardGameView.centerBoard(
                    catanBoardGameView.getBoardGroup(),
                    menuView.getGAME_WIDTH(),
                    menuView.getGAME_HEIGHT()
            );
        }
    }

    // Stops the game and exits if the turn limit is exceeded (Used to prevent infinite loops or AI bugs while testing)
    public void crashGameIfMaxTurnsExceeded(int MAX_TURNS, int turnCounter) {
        if (turnCounter > MAX_TURNS) {
            pauseGame(false);
            String error = "Fatal error: MAX_TURNS (" + MAX_TURNS + ") exceeded. Possible infinite loop or thread leak.";
            System.err.println(error);

            // Stop all running threads gracefully
            stopAllAIThreads();

            // Alert player before exiting
            // Non-blocking overlay, then return to the main menu instead of killing the JVM
            // (under JPro one JVM serves every browser session, so System.exit would end all).
            catanBoardGameView.runOnFX(() ->
                    catanBoardGameView.showInfoOverlay("Game Crash",
                            "Too many turns! Returning to the main menu.\n\n" + error,
                            () -> {
                                if (menuView != null) {
                                    menuView.showMainMenu();
                                }
                            }));
        }
    }

    //_____________________________DICE________________________________//
    // Simulates rolling the dice and triggers effects
    public void rollDice() {
        turnCounter++;
        setHasRolledThisTurn(true);
        // Logic part (no FX)
        Random rand = new Random();
        lastRolledDie1 = rand.nextInt(6) + 1;
        lastRolledDie2 = rand.nextInt(6) + 1;
        int roll = lastRolledDie1 + lastRolledDie2;
        catanBoardGameView.runOnFX(() -> {
            // Update dice visuals and logs
            catanBoardGameView.updateDiceImages(lastRolledDie1, lastRolledDie2);
            catanBoardGameView.logToGameLog(currentPlayer + " ROLLED " + roll + "!");
            catanBoardGameView.hideDiceButton();
            catanBoardGameView.showTurnButton();
            catanBoardGameView.runOnFX(() -> catanBoardGameView.refreshSidebar());
            // Handle robber or resource distribution
            if (roll == 7) {
                catanBoardGameView.getRobber().activateRobber(true, currentPlayer);
            } else {
                catanBoardGameView.logToGameLog("Distributing resources:");
                distributeResources(roll);
            }
            catanBoardGameView.runOnFX(() -> catanBoardGameView.refreshSidebar());
        });
    }

    // Distribute resources to players based on the current dice roll
    public void distributeResources(int diceRoll) {
        boolean resourcesDistributed = false;
        for (Tile tile : board.getTiles()) {
            if (tile.getTileDiceNumber() == diceRoll) {
                Resource.ResourceType type = tile.getResourcetype();

                // Skip sea and desert tiles
                if (type == Resource.ResourceType.SEA || type == Resource.ResourceType.DESERT) continue;
                for (Vertex vertex : tile.getVertices()) {
                    Player owner = vertex.getOwner();
                    if (owner != null) {
                        String res = type.getName();
                        int amount = vertex.isCity() ? 2 : 1;
                        owner.getResources().merge(res, amount, Integer::sum);
                        resourcesDistributed = true;    // Flag for game log
                        // Log the resource gain
                        String logMsg = "Player " + owner.getPlayerId() + " gets " + res;
                        catanBoardGameView.runOnFX(() -> catanBoardGameView.logToGameLog(logMsg));
                    }
                }
            }
        }
        if (!resourcesDistributed) {
            catanBoardGameView.runOnFX(() -> catanBoardGameView.logToGameLog("No Player received anything this turn"));
        }
    }

    //_________________________________________ AI THREAD _____________________________________________//
    // Starts a new AI thread for the current AI player's turn
    public void startAIThread(AIOpponent ai) {
        // If a thread is already running or the game is over, exit early
        if (activeAIThread != null && activeAIThread.isAlive()) return;
        if (isGameOver()) return;

        // Create a new background thread
        activeAIThread = new Thread(() -> {
            // Wait if the game is paused
            while (isGamePaused()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            // Call AI actions depending on the phase
            if (initialPhase) {
                ai.placeInitialSettlementAndRoad(this, catanBoardGameView.getBoardGroup());
            } else {
                ai.makeMoveAI(this, getCatanBoardGameView().getBoardGroup());
            }
        });
        activeAIThread.setDaemon(true); // Ensure JVM can exit even if this thread is running
        activeAIThread.start();         // Launch thread
    }

    // Interrupts and nulls the AI thread (called before turn switch)
    public void stopAllAIThreads() {
        if (activeAIThread != null && activeAIThread.isAlive()) {
            activeAIThread.interrupt();
        }
        activeAIThread = null;
    }

    //_________________________________BUY AND PLAY DEVELOPMENT CARDS_____________________________________//
    // Attempt to buy a development card for the current player
    public void buyDevelopmentCard() {
        if (shuffledDevelopmentCards.isEmpty()) {
            // No cards left to buy
            catanBoardGameView.runOnFX(drawOrDisplay::showNoMoreDevelopmentCardToBuyPopup);
        } else if (!hasRolledDice()){
            // Enforce rolling dice before any action
            catanBoardGameView.runOnFX(() -> drawOrDisplay.rollDiceBeforeActionPopup("You must roll the dice before taking any actions!"));
        } else if (canRemoveResource("Wool", 1) && canRemoveResource("Ore", 1) && canRemoveResource("Grain", 1)) {
            // Pay resources to buy the card
            removeResource("Wool", 1);
            removeResource("Ore", 1);
            removeResource("Grain", 1);
            // Draw a development card from the top of the shuffled list
            DevelopmentCard.DevelopmentCardType cardType = shuffledDevelopmentCards.remove(0);

            // Add it to the player's development card map
            currentPlayer.getDevelopmentCards().merge(cardType, 1, Integer::sum);

            // Log the purchase to the game log
            String log = currentPlayer + " bought a development card ";
            catanBoardGameView.runOnFX(() -> {
                catanBoardGameView.logToGameLog(log);
                catanBoardGameView.runOnFX(() -> catanBoardGameView.refreshSidebar());
            });
        } else {
            // Insufficient resources
            catanBoardGameView.runOnFX(drawOrDisplay::showFailToBuyDevelopmentCardPopup);
        }
    }

    // Attempt to play a specific development card for a given player
    public void playDevelopmentCard(Player player, DevelopmentCard.DevelopmentCardType type) {
        if (isActionBlockedByDevelopmentCard()) {
            // Disallow playing another card while one is still being processed
            drawOrDisplay.showFinishDevelopmentCardActionPopup();
            return;
        }

        // Perform the effect of the development card
        type.play(player, developmentCard);

        // Safely remove it from the player's collection
        player.getDevelopmentCards().computeIfPresent(type, (k, v) -> (v > 1) ? v - 1 : 0);

        // UI update
        catanBoardGameView.runOnFX(() -> {
            catanBoardGameView.runOnFX(() -> catanBoardGameView.refreshSidebar());
        });
    }

    // Get the central development card handler
    public DevelopmentCard getDevelopmentCard() {
        return developmentCard;
    }

    //_____________________________RESOURCES & TRADING_____________________________//

    // Check if current player has enough of a given resource
    public boolean canRemoveResource(String resource, int amount) {
        return currentPlayer.getResources().getOrDefault(resource, 0) >= amount;
    }

    // Subtract a resource from current player
    public void removeResource(String resource, int amount) {
        int current = currentPlayer.getResources().getOrDefault(resource, 0);
        if (current >= amount) {
            currentPlayer.getResources().put(resource, current - amount);
        }
    }

    // Add a resource to current player
    public void addResource(String resource, int amount) {
        currentPlayer.getResources().put(resource,
                currentPlayer.getResources().getOrDefault(resource, 0) + amount);
    }

    //_____________________________BUILDING FUNCTIONS____________________________//
    // Attempt to build an initial settlement
    public BuildResult buildInitialSettlement(Vertex vertex) {
        if (vertex == null || !isValidSettlementPlacement(vertex)) return BuildResult.INVALID_VERTEX;
        if (currentPlayer.getSettlements().contains(vertex)) return BuildResult.INVALID_VERTEX;
        if (initialPhase && waitingForInitialRoad) return BuildResult.INVALID_VERTEX;

        currentPlayer.getSettlements().add(vertex);
        vertex.setOwner(currentPlayer);
        vertex.makeSettlement();
        increasePlayerScore(currentPlayer);

        waitingForInitialRoad = true;
        lastInitialSettlement = vertex;

        // Grant initial resources after second settlement
        if (currentPlayer.getSettlements().size() == 2) {
            for (Tile tile : vertex.getAdjacentTiles()) {
                Resource.ResourceType type = tile.getResourcetype();

                // Only valid land tiles provide starting resources
                if (type != Resource.ResourceType.DESERT && type != Resource.ResourceType.SEA) {
                    currentPlayer.getResources().merge(type.getName(), 1, Integer::sum);
                }
            }
        }
        return BuildResult.SUCCESS;
    }

    // Attempt to build a road (either during initial or main phase)
    public BuildResult buildRoad(Edge edge) {
        if (initialPhase && waitingForInitialRoad) {
            if (!edge.isConnectedTo(lastInitialSettlement)) return BuildResult.NOT_CONNECTED;
            if (!isValidRoadPlacement(edge)) return BuildResult.INVALID_EDGE;
            currentPlayer.getRoads().add(edge);
            waitingForInitialRoad = false;
            lastInitialSettlement = null;

            // Update longest road tracking
            longestRoadManager.calculateAndUpdateLongestRoad(currentPlayer);
            catanBoardGameView.runOnFX(() -> catanBoardGameView.refreshSidebar());
            return BuildResult.SUCCESS;
        }

        if (initialPhase) return BuildResult.INVALID_EDGE;

        if (!isValidRoadPlacement(edge)) return BuildResult.INVALID_EDGE;

        // Max road limit check
        if (currentPlayer.getRoads().size() >= menuView.getMaxRoads()) {
            return BuildResult.TOO_MANY_ROADS;
        }

        // Require resources: 1 Brick, 1 Wood
        if (canRemoveResource("Brick", 1) && canRemoveResource("Wood", 1)) {
            removeResource("Brick", 1);
            removeResource("Wood", 1);
            currentPlayer.getRoads().add(edge);
            longestRoadManager.calculateAndUpdateLongestRoad(currentPlayer);
            catanBoardGameView.runOnFX(() -> catanBoardGameView.refreshSidebar());
            return BuildResult.SUCCESS;
        }

        return BuildResult.INSUFFICIENT_RESOURCES;
    }

    // Build a new settlement (main phase only)
    public BuildResult buildSettlement(Vertex vertex) {
        if (vertex == null || !isValidSettlementPlacement(vertex)) return BuildResult.INVALID_VERTEX;
        if (currentPlayer.getSettlementsAndCities().contains(vertex)) return BuildResult.INVALID_VERTEX;
        // Enforce max settlements limit
        if (currentPlayer.getSettlements().size() >= menuView.getMaxSettlements()) {
            return BuildResult.TOO_MANY_SETTLEMENTS;
        }

        // Require resources: 1 Brick, 1 Wood, 1 Grain, 1 Wool
        if (canRemoveResource("Brick", 1) &&
                canRemoveResource("Wood", 1) &&
                canRemoveResource("Grain", 1) &&
                canRemoveResource("Wool", 1)) {

            removeResource("Brick", 1);
            removeResource("Wood", 1);
            removeResource("Grain", 1);
            removeResource("Wool", 1);

            currentPlayer.getSettlements().add(vertex);
            vertex.setOwner(currentPlayer);
            vertex.makeSettlement();
            increasePlayerScore(currentPlayer);
            return BuildResult.SUCCESS;
        }
        return BuildResult.INSUFFICIENT_RESOURCES;
    }

    // Upgrade an existing settlement to a city
    public BuildResult buildCity(Vertex vertex) {
        if (isNotValidCityPlacement(vertex)) return BuildResult.INVALID_VERTEX;

        // Check if player reached max city limit
        if (currentPlayer.getCities().size() >= menuView.getMaxCities()) {
            return BuildResult.TOO_MANY_CITIES;
        }

        // Require resources: 3 Ore, 2 Grain
        if (canRemoveResource("Ore", 3) && canRemoveResource("Grain", 2)) {
            removeResource("Ore", 3);
            removeResource("Grain", 2);
            currentPlayer.getSettlements().remove(vertex);
            currentPlayer.getCities().add(vertex);
            vertex.setOwner(currentPlayer);
            vertex.makeCity();
            increasePlayerScore(currentPlayer);
            return BuildResult.UPGRADED_TO_CITY;
        }

        // Not enough resources
        drawOrDisplay.notEnoughResourcesPopup("Not enough resources to build a city");
        return BuildResult.INSUFFICIENT_RESOURCES;
    }

    // Used by development card to place a road without resource cost
    public BuildResult placeFreeRoad(Player player, Edge edge) {
        if (!isValidRoadPlacement(edge)) return BuildResult.INVALID_EDGE;
        if (player.getRoads().size() >= menuView.getMaxRoads()) return BuildResult.TOO_MANY_ROADS;

        // Call through build controller for shared logic and animations
        catanBoardGameView.runOnFX(() -> {
            getGameController().getBuildController().buildRoad(edge, player);
        });
        player.getRoads().add(edge);
        // Recalculate longest road for consistency
        longestRoadManager.calculateAndUpdateLongestRoad(player);
        catanBoardGameView.runOnFX(() -> catanBoardGameView.refreshSidebar());
        return BuildResult.SUCCESS;
    }

    //______________________VALID BUILD CHECKS___________________________//
    // Checks if a Settlement placement is Valid!
    public boolean isValidSettlementPlacement(Vertex vertex) {
        return isValidSettlementPlacement(vertex, /* ignoreRoadConnection = */ false);
    }
    public boolean isValidSettlementPlacement(Vertex vertex,
                                              boolean ignoreRoadConnection) {
        // 1. Vertex must be empty
        if (vertex.hasSettlement()) return false;

        // 2. Must touch at least one land tile (not sea/harbour only)
        boolean hasLand = vertex.getAdjacentTiles()
                .stream()
                .anyMatch(tile -> !tile.isSea());
        if (!hasLand) return false;

        // 3. Distance-2 rule: no neighbouring settlements
        for (Vertex neighbour : vertex.getNeighbors()) {
            if (neighbour.hasSettlement()) return false;
        }

        // 4. During the main phase, settlement must connect to one of the
        //    current player's roads, unless the caller asked us to ignore
        //    that rule (e.g. for hypothetical scoring)
        if (!ignoreRoadConnection && !isInInitialPhase()) {
            boolean hasOwnAdjacentRoad = getCurrentPlayer().getRoads()
                    .stream()
                    .anyMatch(edge -> edge.isConnectedTo(vertex));
            if (!hasOwnAdjacentRoad) return false;
        }

        return true;  // All checks passed
    }

    // Check if the road placement follows all rules
    public boolean isValidRoadPlacement(Edge edge) {
        // Must connect two vertices with at least one land tile each
        boolean vertex1HasLand = edge.getVertex1().getAdjacentTiles().stream().anyMatch(t -> !t.isSea());
        boolean vertex2HasLand = edge.getVertex2().getAdjacentTiles().stream().anyMatch(t -> !t.isSea());
        if (!vertex1HasLand || !vertex2HasLand) return false;

        // No duplicate roads allowed
        if (playerList.stream().anyMatch(p -> p.getRoads().contains(edge))) return false;

        // Prevent road from connecting through an opponent’s settlement
        for (Player player : playerList) {
            if (player != currentPlayer) {
                if (player.getSettlementsAndCities().contains(edge.getVertex1()) &&
                        currentPlayer.getRoads().stream().anyMatch(r -> r.isConnectedTo(edge.getVertex1()))) {
                    return false;
                }
                if (player.getSettlementsAndCities().contains(edge.getVertex2()) &&
                        currentPlayer.getRoads().stream().anyMatch(r -> r.isConnectedTo(edge.getVertex2()))) {
                    return false;
                }
            }
        }

        // Must connect to current player's road or settlement
        boolean connectsToSettlementOrCity = currentPlayer.getSettlementsAndCities().contains(edge.getVertex1()) ||
                currentPlayer.getSettlementsAndCities().contains(edge.getVertex2());
        boolean connectsToRoad = currentPlayer.getRoads().stream().anyMatch(r ->
                r.isConnectedTo(edge.getVertex1()) || r.isConnectedTo(edge.getVertex2()));
        return connectsToSettlementOrCity || connectsToRoad;
    }

    // Ensure only own settlements can be upgraded to cities
    public boolean isNotValidCityPlacement(Vertex vertex) {
        return !(vertex.hasSettlement() && vertex.getOwner() == currentPlayer);
    }

    //___________________________SCORE MANAGEMENT_____________________________//
    // Simple increase player score by +1 VP and check for game over
    public void increasePlayerScore(Player player) {
        player.playerScorePlusOne();
        // Win check
        if (player.getPlayerScore() >= menuView.getMaxVictoryPoints()) {
            if (isGamePaused()) return;
            endOfGameWinnerPopup(currentPlayer);
        }
    }

    // For Longest Road and Biggest Army (Decrease the VP's)
    public void decreasePlayerScoreByTwo(Player player) {
        player.playerScoreMinusOne();
        player.playerScoreMinusOne();
    }

    // For Longest Road and Biggest Army (Increase the VP's)
    public void increasePlayerScoreByTwo() {
        currentPlayer.playerScorePlusOne();
        currentPlayer.playerScorePlusOne();
        // Win check
        if (isGameOver()) {
            if (isGamePaused()) return;
            stopAllAIThreads();
            endOfGameWinnerPopup(currentPlayer);
        }
    }

    // Displays end of game winner popup (visuals in class "DrawOrDisplay")
    private void endOfGameWinnerPopup(Player winner) {
        stopAllAIThreads();
        //Makes sure the popup doesn't open twice.
        if (gameOver) return;
        gameOver = true;
        catanBoardGameView.runOnFX(() -> {
            drawOrDisplay.showEndGamePopup(
                    winner,
                    playerList,
                    turnCounter,
                    menuView.getGAME_WIDTH(),
                    menuView.getGAME_HEIGHT(),
                    menuView::showMainMenu);
        });
    }

    //_______________________________PAUSE FUNCTIONS_________________________________//
    public void pauseGame(boolean robberAction) {
        if (robberAction) {
            gamePaused = true;
            stopAllAIThreads();  // interrupt AI thread cleanly
        }
        else {
            if (!gamePaused) {
                this.drawOrDisplay.pauseThinkingAnimation(this.drawOrDisplay); // Stop animation
                catanBoardGameView.logToGameLog("Game paused.");
                gamePaused = true;
                stopAllAIThreads();  // interrupt AI thread cleanly
            }
        }
    }

    // Resumes game from paused state
    public void resumeGame(boolean robberAction) {
        if (robberAction) {
            gamePaused = false;
            // Resumes AI if current player is AI
            if (currentPlayer instanceof AIOpponent ai) {
                startAIThread(ai);
            }
        }
        else {
            if (!gamePaused) return; // prevent spamming or double-starting
            this.drawOrDisplay.resumeThinkingAnimation(this.drawOrDisplay); // resumes paused animations
            catanBoardGameView.logToGameLog("Game resumed.");
            gamePaused = false;
            // Resumes AI if current player is AI
            if (currentPlayer instanceof AIOpponent ai) {
                startAIThread(ai);
            }
        }
    }

    public boolean isGamePaused() {
        return gamePaused;
    }

    //_______________________________BOOLEAN VALIDITY CHECKS_________________________________//
    // Checks if someone has won the game.
    public boolean isGameOver() {
        return playerList.stream().anyMatch(p -> p.getPlayerScore() >= menuView.getMaxVictoryPoints());
    }

    public boolean isActionBlockedByDevelopmentCard() {
        return developmentCard.isPlayingCard();
    }

    // UI Guard -> Checks if it is AI's turn and then blocks actions until AI is done.
    public boolean isBlockedByAITurn() {
        if (gameController.getGameplay().getCurrentPlayer() instanceof AIOpponent) {
            drawOrDisplay.showAITurnPopup();
            return true;
        }
        return false;
    }

    // Some of the visuals depend on whether there are human players in the game, so this checks it.
    public boolean hasHumanPlayers() {
        return playerList.stream().anyMatch(p -> !(p instanceof AIOpponent));
    }

    //__________________________SETTERS________________________//
    public void setBoard(Board board) {
        this.board = board;
    }

    public void setCatanBoardGameView(CatanBoardGameView view) {
        this.catanBoardGameView = view;
    }

    public void setMenuView(MenuView menuView) {
        this.menuView = menuView;
    }

    public void setHasRolledThisTurn(boolean b) {
        hasRolledThisTurn = b;
    }
    public void resetCounters() {
        turnCounter=0;
    }

    //_____________________________GETTERS______________________________//
    public GameController getGameController() {
        return gameController;
    }
    public DrawOrDisplay getDrawOrDisplay() {
        return drawOrDisplay;
    }

    public Player getCurrentPlayer() {
        return currentPlayer;
    }

    public List<Player> getPlayerList() {
        return playerList;
    }

    public boolean isInInitialPhase() {
        return initialPhase;
    }

    public boolean hasRolledDice() {
        return hasRolledThisTurn;
    }

    public boolean isWaitingForInitialRoad() {
        return waitingForInitialRoad;
    }

    public MenuView getMenuView() {
        return menuView;
    }

    public int getBoardRadius() {
        return boardRadius;
    }

    public Board getBoard() {
        return board;
    }

    public CatanBoardGameView getCatanBoardGameView() {
        return catanBoardGameView;
    }

    public List<DevelopmentCard.DevelopmentCardType> getShuffledDevelopmentCards() {
        return shuffledDevelopmentCards;
    }

    public BiggestArmyManager getBiggestArmy() {
        return biggestArmy;
    }

    public LongestRoadManager getLongestRoadManager() {
        return longestRoadManager;
    }
}