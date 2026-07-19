package org.example.catanboardgameapp;
import org.example.catanboardgameviews.CatanBoardGameView;
import org.example.controller.TradeController;
import java.util.*;
import java.util.stream.Collectors;

// Handles usage logic for all types of development cards in Catan, including player/AI logic.
public class DevelopmentCard {

    // Dependencies
    private final Gameplay gameplay;
    private final CatanBoardGameView view;
    private final DrawOrDisplay drawOrDisplay;
    private final CatanBoardGameView catanBoardGameView;

    // Game State Tracking Flags
    private final List<Player> playerList;
    private boolean placingFreeRoads = false;
    private boolean playingCard = false;
    private int freeRoadsLeft = 0;

    //_______________________________CONSTRUCTOR_________________________________//
    public DevelopmentCard(Gameplay gameplay, List<Player> playerList, CatanBoardGameView view, TradeController tradeController) {
        this.gameplay = gameplay;
        this.playerList = playerList;
        this.view = view;
        this.drawOrDisplay = gameplay.getDrawOrDisplay();
        this.catanBoardGameView = gameplay.getCatanBoardGameView();
    }

    //______________________BEHAVIORAL ENUM: CARD TYPE LOGIC_____________________//
    public enum DevelopmentCardType {

        // Take monopoly on a chosen resource by stealing every single resource of that type from everyone
        MONOPOLY("Monopoly") {
            @Override public void play(Player player, DevelopmentCard devCard) {
                devCard.playMonopolyCardAsPlayer(player);
            }
            @Override public void playAsAI(AIOpponent ai, DevelopmentCard devCard, Gameplay gameplay) {
                devCard.playMonopolyCardAsAI(ai);
            }
        },
        // Move the Robber to chosen Tile and steal from a player who owns the Tile.
        KNIGHT("Knight") {
            @Override public void play(Player player, DevelopmentCard devCard) {
                devCard.playKnightCardAsPlayer(player);
            }
            @Override public void playAsAI(AIOpponent ai, DevelopmentCard devCard, Gameplay gameplay) {
                devCard.playKnightCardAsAI(ai, gameplay);
            }
        },
        // Build 2 free roads immediately
        ROADBUILDING("Road Building") {
            @Override public void play(Player player, DevelopmentCard devCard) {
                devCard.playRoadBuildingCardAsPlayer(player);
            }
            @Override public void playAsAI(AIOpponent ai, DevelopmentCard devCard, Gameplay gameplay) {
                devCard.playRoadBuildingCardAsAI(ai, gameplay);
            }
        },
        // Receive two handpicked resources from the bank
        YEAROFPLENTY("Year Of Plenty") {
            @Override public void play(Player player, DevelopmentCard devCard) {
                devCard.playYearOfPlentyCardAsPlayer(player);
            }
            @Override public void playAsAI(AIOpponent ai, DevelopmentCard devCard, Gameplay gameplay) {
                devCard.playYearOfPlentyCardAsAI(ai, gameplay);
            }
        },
        // Get a free Victory Point
        VICTORYPOINT("Victory Point") {
            @Override public void play(Player player, DevelopmentCard devCard) {
                devCard.playVictoryPointCardAsPlayer(player);
            }
            @Override public void playAsAI(AIOpponent ai, DevelopmentCard devCard, Gameplay gameplay) {
                devCard.playVictoryPointCardAsAI(ai, gameplay);
            }
        };

        // Human Player play method
        public abstract void play(Player player, DevelopmentCard devCard);

        // AI Player play method
        public void playAsAI(AIOpponent ai, DevelopmentCard devCard, Gameplay gameplay) {
            throw new UnsupportedOperationException("AI play not implemented for " + this.name());
        }

        // Enum helpers
        private final String displayName;
        DevelopmentCardType(String displayName) {
            this.displayName = displayName;
        }
        public String getDisplayName() {
            return displayName;
        }
    }
    //______________________PLAYER & AI EXECUTION METHODS______________________//

    // ______________MONOPOLY______________//
    public void playMonopolyCardAsPlayer(Player player) {
        startPlayingCard();
        Player currentPlayer = gameplay.getCurrentPlayer();
        drawOrDisplay.showMonopolyDialog(chosenResource -> {
            if (chosenResource == null) return;
            int taken = monopolizeResource(chosenResource, currentPlayer);
            view.logToGameLog(player + " played a Monopoly Development Card and took " + taken + " " + chosenResource + " from other players!");
            view.refreshSidebar();
            finishPlayingCard();
        });
    }
    private void playMonopolyCardAsAI(AIOpponent ai) {
        String chosenResource = chooseSmartResourceToMonopoly(gameplay, ai);
        int taken = monopolizeResource(chosenResource, ai);
        view.logToGameLog("AI " + ai + " played a Monopoly Development Card and took " + taken + " " + chosenResource + " from other players!");
    }
    // ______________KNIGHT______________//
    private void playKnightCardAsPlayer(Player player) {
        view.logToGameLog(player + " played a Knight development card!");
        view.getRobber().activateRobber(false, player);
    }
    private void playKnightCardAsAI(AIOpponent ai, Gameplay gameplay) {
        view.logToGameLog("AI " + ai + " played a Knight development card!");
        view.getRobber().activateRobber(false, ai);
    }

    // ______________ROADBUILDING______________//
    private void playRoadBuildingCardAsPlayer(Player player) {
        view.logToGameLog(player + " played a Road Builder Development Card!");
        startPlayingCard();
        this.placingFreeRoads = true;
        this.freeRoadsLeft = 2;
    }

    private void playRoadBuildingCardAsAI(AIOpponent ai, Gameplay gameplay) {
        view.logToGameLog("AI " + ai + " played a Road Builder Development Card!");
        int placed = 0;
        for (Edge edge : gameplay.getBoard().getEdges()) {
            if (placed == 2) break;
            if (gameplay.isValidRoadPlacement(edge)) {
                if (gameplay.placeFreeRoad(ai, edge) == BuildResult.SUCCESS) {
                    placed++;
                }
            }
        }
    }

    // ______________YEAR OF PLENTY______________//
    private void playYearOfPlentyCardAsPlayer(Player player) {
        startPlayingCard();
        Player currentPlayer = gameplay.getCurrentPlayer();
        if (currentPlayer instanceof AIOpponent ai && ai.getStrategyLevel() == AIOpponent.StrategyLevel.HARD) {
            applyYearOfPlenty(player, currentPlayer, ai.chooseResourcesForYearOfPlenty());
        } else {
            drawOrDisplay.showYearOfPlentyDialog(currentPlayer.getResources(),
                    selected -> applyYearOfPlenty(player, currentPlayer, selected));
        }
    }

    private void applyYearOfPlenty(Player player, Player currentPlayer, Map<String, Integer> selected) {
        if (selected != null) {
            addResourcesToPlayer(currentPlayer, selected);
            String gained = selected.entrySet().stream()
                    .map(entry -> entry.getValue() + " " + entry.getKey())
                    .collect(Collectors.joining(", "));
            catanBoardGameView.logToGameLog(player + " used Year of Plenty Development Card and received " + gained + ".");
            catanBoardGameView.refreshSidebar();
            finishPlayingCard();
        }
    }

    private void playYearOfPlentyCardAsAI(AIOpponent ai, Gameplay gameplay) {
        Map<String, Integer> selected = ai.chooseResourcesForYearOfPlenty();
        selected.forEach((res, amt) -> ai.getResources().merge(res, amt, Integer::sum));
        String gained = selected.entrySet().stream()
                .map(e -> "+ " + e.getValue() + " " + e.getKey())
                .collect(Collectors.joining(", "));
        view.logToGameLog("AI " + ai + " used Year of Plenty Development Card and received " + gained + ".");
    }

    // ______________VICTORY POINT______________//
    private void playVictoryPointCardAsPlayer(Player player) {
        gameplay.increasePlayerScore(player);
        view.logToGameLog(player + " played a Victory Point Development Card and gained 1 point!");
    }

    private void playVictoryPointCardAsAI(AIOpponent ai, Gameplay gameplay) {
        gameplay.increasePlayerScore(ai);
        gameplay.getCatanBoardGameView().runOnFX(() ->
                gameplay.getCatanBoardGameView().logToGameLog("AI " + ai + " played a Victory Point Development Card and gained 1 point!")
        );
    }

    //__________________________STATE + LOGIC UTILITIES__________________________//
    public void startPlayingCard() {
        this.playingCard = true;
    }

    public void finishPlayingCard() {
        this.playingCard = false;
    }

    public boolean isPlayingCard() {
        return playingCard;
    }

    public boolean isPlacingFreeRoads() {
        return placingFreeRoads;
    }

    public void decrementFreeRoads() {
        if (freeRoadsLeft > 0) freeRoadsLeft--;
        if (freeRoadsLeft == 0) placingFreeRoads = false;
    }

    public void addResourcesToPlayer(Player player, Map<String, Integer> added) {
        added.forEach((res, amt) ->
                player.getResources().merge(res, amt, Integer::sum)
        );
    }

    // Helper for Player and AI to play Monopoly Card
    public int monopolizeResource(String resource, Player player) {
        int totalTaken = 0;
        for (Player other : playerList) {
            if (!other.equals(player)) {
                int amount = other.getResources().getOrDefault(resource, 0);
                if (amount > 0) {
                    other.getResources().put(resource, 0);
                    totalTaken += amount;
                }
            }
        }
        player.getResources().merge(resource, totalTaken, Integer::sum);
        return totalTaken;
    }

    // Second Monopoly helper function
    public String chooseSmartResourceToMonopoly(Gameplay gameplay, AIOpponent ai) {
        //getting all opponents
        List<Player> opponents = gameplay.getPlayerList().stream()
                .filter(p -> p != ai)
                .toList();
        // finding there resources
        Map<String, Integer> totalOpponentResources = new HashMap<>();

        for (Player p : opponents) {
            for (Map.Entry<String, Integer> entry : p.getResources().entrySet()) {
                totalOpponentResources.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }
        // Calculate how much the player has of each resource
        int oreHave = ai.getResources().getOrDefault("Ore", 0);
        int grainHave = ai.getResources().getOrDefault("Grain", 0);
        int woolHave = ai.getResources().getOrDefault("Wool", 0);
        int woodHave = ai.getResources().getOrDefault("Wood", 0);
        int brickHave = ai.getResources().getOrDefault("Brick", 0);

        // Calculate how much you can steal in total of each resource
        int oreFromOpponents = totalOpponentResources.getOrDefault("Ore", 0);
        int grainFromOpponents = totalOpponentResources.getOrDefault("Grain", 0);
        int woolFromOpponents = totalOpponentResources.getOrDefault("Wool", 0);
        int woodFromOpponents = totalOpponentResources.getOrDefault("Wood", 0);
        int brickFromOpponents = totalOpponentResources.getOrDefault("Brick", 0);

        // Check if we can steal enough to build a full city
        int oreNeed = Math.max(0, 3 - oreHave);
        int grainNeed = Math.max(0, 2 - grainHave);
        if ((oreFromOpponents >= oreNeed) && grainNeed == 0) {
            return "Ore";
        } else if (grainFromOpponents >= grainNeed && oreNeed == 0) {
            return "Grain";
        }
        // Check if we can steal enough to build a settlement
        grainNeed = Math.max(0, 1 - grainHave);
        int woodNeed = Math.max(0, 1 - woodHave);
        int woolNeed = Math.max(0, 1 - woolHave);
        int brickNeed = Math.max(0, 1 - brickHave);
        if (grainNeed > 0 && grainNeed <= grainFromOpponents
                && woodNeed == 0 && woolNeed == 0 && brickNeed == 0) {
            return "Grain";
        } else if (woodNeed > 0 && woodNeed <= woodFromOpponents
                && grainNeed == 0 && woolNeed == 0 && brickNeed == 0) {
            return "Wood";
        } else if (woolNeed > 0 && woolNeed <= woolFromOpponents
                && grainNeed == 0 && woodNeed == 0 && brickNeed == 0) {
            return "Wool";
        } else if (brickNeed > 0 && brickNeed <= brickFromOpponents
                && grainNeed == 0 && woolNeed == 0 && woodNeed == 0) {
            return "Brick";
        }
        // If not, pick the one with the largest trading potential
        List<Harbor.HarborType> harborTypes = new ArrayList<>();
        for (Harbor harbor : gameplay.getBoard().getHarbors()) {
            if (harbor.usableBy(ai)) {
                harborTypes.add(harbor.getType());
            }
        }
        String bestTradeResource = null;
        int bestTradeValue = 0;

        for (String resource : List.of("Ore", "Grain", "Wool", "Wood", "Brick")) {
            int available = totalOpponentResources.getOrDefault(resource, 0);
            int tradeRatio = 4; // default (not tradable)

            // Check for specific 2:1 harbor
            for (Harbor.HarborType type : harborTypes) {
                if (type.specific != null && type.specific.name().equalsIgnoreCase(resource)) {
                    tradeRatio = 2;
                    break;
                }
            }
            // If no 2:1 harbor, check for generic 3:1
            if (tradeRatio == 4) {
                for (Harbor.HarborType type : harborTypes) {
                    if (type == Harbor.HarborType.GENERIC) {
                        tradeRatio = 3;
                        break;
                    }
                }
            }
            // If we have a usable harbor (2:1 or 3:1), compute trades
            if (tradeRatio < 4) {
                int tradeCount = available / tradeRatio;
                if (tradeCount > bestTradeValue) {
                    bestTradeValue = tradeCount;
                    bestTradeResource = resource;
                }
            }
        }
        // fallback only if literally no one has any resources
        return Objects.requireNonNullElseGet(bestTradeResource, () -> totalOpponentResources.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Ore"));
    }
}