package org.example.controller;

import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.DialogPane;
import org.example.catanboardgameapp.*;
import java.util.*;

public class TradeController {

    private final GameController gameController;
    private final DrawOrDisplay drawOrDisplay;

    //___________________________CONTROLLER__________________________________//

    // Initialize with references to game logic and UI
    public TradeController(GameController gameController) {
        this.gameController = gameController;
        this.drawOrDisplay = gameController.getGameplay().getDrawOrDisplay();
    }

    //___________________________FUNCTIONS__________________________________//

    // Sets up the trade button with trading logic and UI dialogs
    public void setupTradeButton(Button tradeButton) {
        tradeButton.setOnAction(e -> {
            // Block trading if a development card action is pending
            if (gameController.getGameplay().isActionBlockedByDevelopmentCard()) {
                drawOrDisplay.showFinishDevelopmentCardActionPopup();
                return;
            }
            Gameplay gameplay = gameController.getGameplay();

            // Block trading if it's the AI's turn or if dice haven't been rolled yet
            if (!gameplay.isBlockedByAITurn() && !gameplay.isInInitialPhase() && !gameplay.hasRolledDice()) {
                drawOrDisplay.rollDiceBeforeActionPopup("You must roll the dice before Trading!");
            }
            else {
                Map<String, Integer> bestRatios = new HashMap<>();
                List<Harbor> harbors = gameplay.getBoard().getHarbors();

                // Default 4:1 trade ratio with the bank
                for (String res : Arrays.asList("Ore", "Wood", "Brick", "Grain", "Wool")) {
                    bestRatios.put(res, 4);
                }
                // Update trade ratios based on harbors the player has access to
                for (Harbor harbor : harbors) {
                    if (harbor.usableBy(gameplay.getCurrentPlayer())) {
                        Harbor.HarborType type = harbor.getType();
                        // 3:1 harbor applies to all resources
                        if (type == Harbor.HarborType.GENERIC) {
                            bestRatios.replaceAll((r, v) -> Math.min(bestRatios.get(r), 3));
                        } else {
                            // 2:1 harbor for a specific resource
                            String specific = type.specific.getName();
                            bestRatios.put(specific, Math.min(bestRatios.get(specific), 2));
                        }
                    }
                }
                // Collect tradeable resources the player has enough of
                List<String> tradeableResources = new ArrayList<>();
                for (Map.Entry<String, Integer> entry : gameplay.getCurrentPlayer().getResources().entrySet()) {
                    String resource = entry.getKey();
                    int amount = entry.getValue();
                    if (amount >= bestRatios.getOrDefault(resource, 4)) {
                        tradeableResources.add(resource);
                    }
                }
                if (tradeableResources.isEmpty()) {
                    drawOrDisplay.showTradeError("You don't have enough resources to trade based on your harbors.");
                    return;
                }
                // Choose which resource to give, then (non-blocking) which to receive.
                gameplay.getCatanBoardGameView().showChoiceOverlay(
                        "Harbor Trade", "Select the resource you want to give:", tradeableResources,
                        giveResource -> {
                            if (giveResource == null) return;
                            int ratio = bestRatios.getOrDefault(giveResource, 4);

                            List<String> receiveOptions = new ArrayList<>(Arrays.asList("Ore", "Wood", "Brick", "Grain", "Wool"));
                            receiveOptions.remove(giveResource);

                            gameplay.getCatanBoardGameView().showChoiceOverlay(
                                    "Harbor Trade", "Select the resource you want to receive:", receiveOptions,
                                    receiveResource -> {
                                        if (receiveResource == null) return;
                                        if (!gameplay.canRemoveResource(giveResource, ratio)) {
                                            drawOrDisplay.showTradeError("You don't have enough " + giveResource + " to trade (requires " + ratio + ").");
                                            return;
                                        }
                                        gameplay.removeResource(giveResource, ratio);
                                        gameplay.addResource(receiveResource, 1);
                                        gameplay.getCatanBoardGameView().logToGameLog(gameplay.getCurrentPlayer() + " traded " + ratio + " " + giveResource + " for 1 " + receiveResource);
                                        gameplay.getCatanBoardGameView().refreshSidebar();
                                    });
                        });
            }
        });
    }
}