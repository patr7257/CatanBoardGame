package org.example.controller;

import javafx.event.EventHandler;
import javafx.scene.Group;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import org.example.catanboardgameapp.*;
import java.util.Optional;

public class BuildController {

    private final Group boardGroup;
    private final DrawOrDisplay drawOrDisplay;
    private final GameController gameController;
    private boolean confirmBeforeBuild = false; // Start with Build confirmation OFF

    //___________________________CONTROLLER__________________________________//
    // Initialize with references to game controller and display
    public BuildController(GameController gameController) {
        this.gameController = gameController;
        this.drawOrDisplay = gameController.getGameplay().getDrawOrDisplay();
        this.boardGroup = gameController.getGameView().getBoardGroup(); // or pass in if needed
    }

    //___________________________ ROAD PLACEMENT HANDLER ___________________________
    // Handles mouse clicks for placing roads
    public EventHandler<MouseEvent> createRoadClickHandler(Edge edge) {
        return event -> {
            // Handle free roads from Road Building card
            if (gameController.getGameplay().getDevelopmentCard().isPlacingFreeRoads()) {
                if (gameController.getGameplay().getCurrentPlayer().getRoads().size() >= 15) {
                    gameController.getGameplay().getDevelopmentCard().finishPlayingCard();
                    drawOrDisplay.showMaxRoadsReachedPopup();
                }
                if (gameController.getGameplay().isValidRoadPlacement(edge)) {
                    gameController.getGameView().logToGameLog("Place your free roads now");
                    Player player = gameController.getGameplay().getCurrentPlayer();
                    BuildResult result = gameController.getGameplay().placeFreeRoad(player, edge);
                    if (result == BuildResult.SUCCESS) {
                        buildRoad(edge, player); // This will draw it on screen
                        gameController.getGameView().logToGameLog("Placed a free road via Road Building card.");
                        gameController.getGameplay().getDevelopmentCard().decrementFreeRoads();

                        // Finish card action if both free roads are placed
                        if (!gameController.getGameplay().getDevelopmentCard().isPlacingFreeRoads()) {
                            gameController.getGameView().logToGameLog("Finished placing 2 free roads.");
                            gameController.getGameplay().getDevelopmentCard().finishPlayingCard();
                        }
                    }
                } else {
                    // Show red cross if placement is invalid
                    double midX = (edge.getVertex1().getX() + edge.getVertex2().getX()) / 2;
                    double midY = (edge.getVertex1().getY() + edge.getVertex2().getY()) / 2;
                    drawOrDisplay.drawErrorCross(boardGroup, midX, midY);
                }
                return;
            }
            // Block if a development card is still active
            if (gameController.getGameplay().isActionBlockedByDevelopmentCard()) {
                drawOrDisplay.showFinishDevelopmentCardActionPopup();
                return;
            }
            // Enforce dice roll before building
            if (!gameController.getGameplay().isInInitialPhase() && !gameController.getGameplay().hasRolledDice()) {
                drawOrDisplay.rollDiceBeforeActionPopup("You must roll the dice before building!");
                return;
            }

            // Block during AI turn
            if (gameController.getGameplay().isBlockedByAITurn()) return;

            Player currentPlayer = gameController.getGameplay().getCurrentPlayer();

            // Optional confirmation, now a non-blocking overlay (JPro-safe).
            if (isConfirmBeforeBuildEnabled()) {
                gameController.getGameView().showConfirmOverlay(
                        "Confirm Build", "Place a road here?", "Build", "Cancel",
                        () -> attemptBuildRoad(edge, currentPlayer), null);
            } else {
                attemptBuildRoad(edge, currentPlayer);
            }
        };
    }

    // Actually places the road and handles the result. Split out so it can run either
    // immediately or from the confirmation overlay's callback.
    private void attemptBuildRoad(Edge edge, Player currentPlayer) {
        BuildResult result = gameController.getGameplay().buildRoad(edge);
        switch (result) {
            case SUCCESS -> {
                buildRoad(edge, currentPlayer);
                // Proceed to next player if in initial phase
                if (gameController.getGameplay().isInInitialPhase()
                        && !gameController.getGameplay().isWaitingForInitialRoad()) {
                    gameController.getGameplay().nextPlayerTurn();
                }
                gameController.getGameplay().getCatanBoardGameView().refreshSidebar();
            }
            case TOO_MANY_ROADS -> drawOrDisplay.showMaxRoadsReachedPopup();

            case NOT_CONNECTED, INVALID_EDGE, INSUFFICIENT_RESOURCES -> {
                // Show red cross for failed placement
                double midX = (edge.getVertex1().getX() + edge.getVertex2().getX()) / 2;
                double midY = (edge.getVertex1().getY() + edge.getVertex2().getY()) / 2;
                drawOrDisplay.drawErrorCross(boardGroup, midX, midY);
            }
        }
    }

    //___________________________ SETTLEMENT / CITY PLACEMENT HANDLER ___________________________
    // Handles mouse click on a vertex (for building settlement or upgrading to city)
    public EventHandler<MouseEvent> createSettlementClickHandler(Circle circle, Vertex vertex, BorderPane root) {
        return event -> {
            // Block if a development card is still active
            if (gameController.getGameplay().isActionBlockedByDevelopmentCard()) {
                drawOrDisplay.showFinishDevelopmentCardActionPopup();
                return;
            }
            // Don't allow clicking on cities
            if (vertex.isCity()) return;

            // Enforce dice roll in main phase
            if (!gameController.getGameplay().isInInitialPhase() && !gameController.getGameplay().hasRolledDice()) {
                drawOrDisplay.rollDiceBeforeActionPopup("You must roll the dice before building!");
                return;
            }
            Player currentPlayer = gameController.getGameplay().getCurrentPlayer();
            if (gameController.getGameplay().isBlockedByAITurn()) return;

            // Optional confirmation, now a non-blocking overlay (JPro-safe).
            if (isConfirmBeforeBuildEnabled()) {
                gameController.getGameView().showConfirmOverlay(
                        "Confirm Build", "Place a settlement or upgrade to a city here?", "Build", "Cancel",
                        () -> attemptBuildSettlementOrCity(circle, vertex, currentPlayer, root), null);
            } else {
                attemptBuildSettlementOrCity(circle, vertex, currentPlayer, root);
            }
        };
    }

    // Places a settlement (or upgrades to a city) and handles the result. Split out so it
    // can run immediately or from the confirmation overlay's callback.
    private void attemptBuildSettlementOrCity(Circle circle, Vertex vertex, Player currentPlayer, BorderPane root) {
        BuildResult result;
        if (gameController.getGameplay().isInInitialPhase()) {
            result = gameController.getGameplay().buildInitialSettlement(vertex);
        } else {
            result = gameController.getGameplay().buildSettlement(vertex);
        }
        switch (result) {
            case SUCCESS -> {
                vertex.setOwner(currentPlayer);
                drawOrDisplay.drawSettlement(circle, vertex, boardGroup);
                circle.setOnMouseClicked(createSettlementClickHandler(circle, vertex, root));
                gameController.getGameView().logToGameLog(currentPlayer + "  built a SETTLEMENT");
                gameController.getGameplay().getCatanBoardGameView().refreshSidebar();
            }
            case INSUFFICIENT_RESOURCES, INVALID_VERTEX -> {
                // Try to upgrade to city if settlement failed
                BuildResult cityResult = gameController.getGameplay().buildCity(vertex);
                if (cityResult == BuildResult.TOO_MANY_CITIES) {
                        drawOrDisplay.showMaxCitiesReachedPopup();
                        drawOrDisplay.drawErrorCross(boardGroup, vertex.getX(), vertex.getY());
                }
                if (cityResult == BuildResult.UPGRADED_TO_CITY) {
                    vertex.setOwner(currentPlayer);
                    gameController.getGameView().getSettlementLayer().getChildren().remove(circle);
                    drawOrDisplay.drawCity(vertex, gameController.getGameplay().getCatanBoardGameView().getBoardGroup());
                    gameController.getGameplay().getCatanBoardGameView().refreshSidebar();
                    gameController.getGameView().logToGameLog(currentPlayer + " built a CITY");

                } else {
                    // Neither worked
                    drawOrDisplay.drawErrorCross(boardGroup, vertex.getX(), vertex.getY());
                }
            }
            case TOO_MANY_SETTLEMENTS -> {
                drawOrDisplay.showMaxSettlementsReachedPopup();
            }
            default -> drawOrDisplay.drawErrorCross(boardGroup, vertex.getX(), vertex.getY());
        }
    }

    //___________________________HELPERS__________________________________//

    // Draws a road on the board and assigns ownership
    public void buildRoad(Edge edge, Player currentPlayer) {
        Line playerRoadLine = new Line(
                edge.getVertex1().getX(), edge.getVertex1().getY(),
                edge.getVertex2().getX(), edge.getVertex2().getY()
        );
        drawOrDisplay.drawRoad(playerRoadLine, currentPlayer, boardGroup);
        gameController.getGameView().getRoadLayer().getChildren().add(playerRoadLine);
    }
    // Toggles build confirmation on/off
    public void toggleConfirmBeforeBuild() {
        confirmBeforeBuild = !confirmBeforeBuild;
    }

    // Returns whether build confirmation is enabled
    public boolean isConfirmBeforeBuildEnabled() {
        return confirmBeforeBuild;
    }


    //___________________________GETTERS__________________________________//
    public GameController getGameController() {
        return gameController;
    }

}