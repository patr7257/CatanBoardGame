package org.example.catanboardgameapp;

import javafx.scene.Group;
import javafx.scene.shape.Circle;
import org.example.catanboardgameviews.CatanBoardGameView;
import java.util.*;
import java.util.List;

public class Robber {

    // Configs
    private final Gameplay gameplay;
    private final DrawOrDisplay drawOrDisplay;
    private final CatanBoardGameView catanBoardGameView;

    // Robber visuals and flags
    private Tile currentTile;
    private final Board board;
    private Circle robberCircle;
    private List<Circle> activeRobberHighlights = new ArrayList<>();

    //______________________________CONSTRUCTOR_____________________________//
    public Robber(Tile startingTile, Gameplay gameplay, CatanBoardGameView catanBoardGameView, Group boardGroup) {
        this.currentTile = startingTile;
        this.gameplay = gameplay;
        this.drawOrDisplay = gameplay.getDrawOrDisplay();
        this.catanBoardGameView = catanBoardGameView;
        this.board = gameplay.getBoard();
        this.robberCircle = drawOrDisplay.createRobberCircle();
        drawOrDisplay.drawNewRobberCircle(startingTile, boardGroup, robberCircle, false);
    }

    //________________________ROBBER PLACEMENT LOGIC________________________//
    // Main Robber function, ONLY function being called from outside this class
    public void activateRobber(boolean sevenRolled, Player player) {
        hideButtons();
        if (sevenRolled) {
            // Discards are now non-blocking; place the robber only once they finish.
            whoShouldDiscardCards(() -> proceedWithRobberPlacement(player));
        } else {                                // Knight Development Card
            player.increasePlayedKnights();
            gameplay.getBiggestArmy()
                    .calculateAndUpdateBiggestArmy(player);
            proceedWithRobberPlacement(player);
        }
    }

    // Robber placement (AI or human), run after any 7-roll discards complete.
    private void proceedWithRobberPlacement(Player player) {
        // AI Player Logic
        if (player instanceof AIOpponent ai) {
            AIHandleRobberMechanics(ai);
            showButtons();
            catanBoardGameView.runOnFX(catanBoardGameView::refreshSidebar);
        }
        else {
            // Human Player Logic
            catanBoardGameView.runOnFX(() -> {
                catanBoardGameView.logToGameLog(
                        player + " please place the Robber on a highlighted tile");

                // draw highlights; when the user clicks, *this* lambda runs
                activeRobberHighlights = drawOrDisplay.createAndDrawRobberHighlights(
                        catanBoardGameView.getBoardGroup(),
                        activeRobberHighlights,
                        board,
                        chosenTile -> {
                            // Robber actually moves
                            moveTo(chosenTile);
                            drawOrDisplay.drawNewRobberCircle(chosenTile, catanBoardGameView.getBoardGroup(), robberCircle, false);

                            // Steal phase
                            List<Player> victims = getPotentialVictims(
                                    chosenTile, gameplay.getCurrentPlayer());
                            if (victims.isEmpty()) {
                                catanBoardGameView.logToGameLog("Bad Robber placement! No players to steal from.");
                                showButtons();
                                catanBoardGameView.refreshSidebar();
                            } else {
                                catanBoardGameView.logToGameLog(player + " Placed the robber on a new Tile!");
                                drawOrDisplay.showRobberVictimDialog(victims, victim -> {
                                    if (victim != null) {
                                        boolean success = stealResourceFrom(victim, player);
                                        if (!success) {
                                            catanBoardGameView.logToGameLog(
                                                    "Failed to steal a resource from " + victim);
                                        }
                                    }
                                    showButtons();
                                    catanBoardGameView.refreshSidebar();
                                });
                            }
                        });
            });
        }
    }

    // Handles all logic for AI Robber usage
    private void AIHandleRobberMechanics(AIOpponent ai) {
        Tile chosenTile = AIChooseBestRobberTile(ai);
        Player victim = AIChooseVictimToStealFrom(chosenTile, ai, ai.getStrategyLevel());
        if (victim != null) {
            stealResourceFrom(victim, ai);
        }
        moveTo(chosenTile); // Move robber to new Tile
        // Draw the new robber circle
        drawOrDisplay.drawNewRobberCircle(chosenTile, catanBoardGameView.getBoardGroup(), robberCircle, false);
    }

    private Tile AIChooseBestRobberTile(AIOpponent ai) {
        AIOpponent.StrategyLevel level = ai.getStrategyLevel();
        Tile chosenTile;
        // EASY AI: Random placement
        if (level == AIOpponent.StrategyLevel.EASY) {
            List<Tile> candidates = board.getTiles().stream()
                    .filter(t -> !t.isSea() && t != currentTile)
                    .toList();
            catanBoardGameView.logToGameLog(ai + " (" + level + ") placed the robber randomly!");
            chosenTile = candidates.get(new Random().nextInt(candidates.size()));
        }
        // MEDIUM/HARD: Smart Placement System
        else {
            List<Tile> validTargets = board.getTiles().stream()
                    .filter(t -> !t.isSea() && t != currentTile)
                    .toList();

            Tile bestTile = null;
            int bestScore = Integer.MIN_VALUE;

            for (Tile tile : validTargets) {
                int score = 0;
                boolean blocksSelf = tile.getVertices().stream()
                        .anyMatch(v -> v.getOwner() == ai);
                if (blocksSelf) {
                    continue; // MEDIUM/HARD: never block self
                }
                for (Vertex v : tile.getVertices()) {
                    Player owner = v.getOwner();
                    if (owner == null || owner == ai) continue;
                    int weight = v.isCity() ? 2 : 1;
                    int diceValue = ai.getSettlementDiceValue(v, gameplay);
                    score += diceValue * weight;
                    if (level == AIOpponent.StrategyLevel.HARD && ai.lateGame() && owner.getPlayerScore() >= 7) {
                        score += diceValue * weight * 2; // Threat multiplier
                    }
                }
                if (score > bestScore) {
                    bestScore = score;
                    bestTile = tile;
                }
            }
            catanBoardGameView.logToGameLog(ai + " (" + level + ") placed robber on best possible tile!");
            chosenTile = bestTile != null ? bestTile : validTargets.get(0);
        }
        return chosenTile;
    }

    private Player AIChooseVictimToStealFrom(Tile chosenTile, AIOpponent ai, AIOpponent.StrategyLevel level) {
        // Determine valid victims
        List<Player> victims = getPotentialVictims(chosenTile, ai).stream()
                .filter(p -> p.getTotalResourceCount() > 0)
                .toList();
        if (!victims.isEmpty()) {
            Player victim;
            if (level == AIOpponent.StrategyLevel.HARD) {
                victim = AIHardChooseBestRobberVictim(ai, victims);
            } else {
                victim = victims.stream()
                        .max(Comparator.comparingInt(Player::getTotalResourceCount))
                        .orElse(victims.get(0));
            }
            return victim;
        }
        catanBoardGameView.logToGameLog("AI did not find a victim to steal from");
        return null;
    }

    // Handle 7-roll discards. AI discards immediately; human discards are shown one at a
    // time via non-blocking overlays, then onComplete runs (robber placement continues).
    private void whoShouldDiscardCards(Runnable onComplete) {
        List<Player> humansToDiscard = new ArrayList<>();
        for (Player player : gameplay.getPlayerList()) {
            int totalCards = player.getResources().values().stream().mapToInt(Integer::intValue).sum();
            if (totalCards <= 7) {
                continue;   // no discard needed
            }
            if (player instanceof AIOpponent ai) {
                Map<String, Integer> discarded = AIChooseCardsToDiscard(ai);
                if (discarded != null) {
                    discardResources(player, discarded);
                }
                catanBoardGameView.runOnFX(catanBoardGameView::refreshSidebar);
            } else {
                humansToDiscard.add(player);
            }
        }
        processHumanDiscards(humansToDiscard, 0, onComplete);
    }

    // Show each human's discard overlay sequentially, then run onComplete.
    private void processHumanDiscards(List<Player> humans, int index, Runnable onComplete) {
        if (index >= humans.size()) {
            if (onComplete != null) catanBoardGameView.runOnFX(onComplete);
            return;
        }
        Player player = humans.get(index);
        Map<String, Integer> playerResources = new HashMap<>(player.getResources());
        int totalCards = playerResources.values().stream().mapToInt(Integer::intValue).sum();
        int toDiscard = totalCards / 2;
        if (toDiscard == 0) {
            processHumanDiscards(humans, index + 1, onComplete);
            return;
        }
        gameplay.pauseGame(true);
        catanBoardGameView.runOnFX(() ->
                drawOrDisplay.showDiscardDialog(player, toDiscard, playerResources, gameplay, discarded -> {
                    if (discarded != null) {
                        StringBuilder discardText = new StringBuilder(player + " discarded: ");
                        discarded.forEach((res, amt) -> discardText.append(amt).append(" ").append(res).append(", "));
                        if (discardText.length() >= 2) discardText.setLength(discardText.length() - 2);
                        gameplay.getCatanBoardGameView().logToGameLog(discardText.toString());
                        discardResources(player, discarded);
                    }
                    catanBoardGameView.refreshSidebar();
                    processHumanDiscards(humans, index + 1, onComplete);
                })
        );
    }

    public Player AIHardChooseBestRobberVictim(AIOpponent ai, List<Player> victims) {
        AIOpponent.Strategy strategy = ai.determineStrategy(false);
        Set<String> neededResources = ai.getNeededResourcesForStrategy(strategy);
        return victims.stream()
                .max(Comparator.comparingInt(v -> ai.countHelpfulCards(v, neededResources)))
                .orElse(null);
    }

    private List<Player> getPotentialVictims(Tile tile, Player currentPlayer) {
        Set<Player> victims = new HashSet<>();
        for (Vertex v : tile.getVertices()) {
            Player owner = v.getOwner();
            if (owner != null && owner != currentPlayer) victims.add(owner);
        }
        return new ArrayList<>(victims);
    }

    public boolean stealResourceFrom(Player victim, Player thief) {
        List<String> pool = new ArrayList<>();
        victim.getResources().forEach((res, count) -> {
            for (int i = 0; i < count; i++) pool.add(res);
        });
        if (pool.isEmpty()) {
            catanBoardGameView.logToGameLog(victim + " had no Resources to steal");
            return false;
        }
        // Shuffle resources and steal random one
        Collections.shuffle(pool);
        String stolen = pool.get(0);
        victim.getResources().put(stolen, victim.getResources().get(stolen) - 1);
        thief.getResources().put(stolen, thief.getResources().getOrDefault(stolen, 0) + 1);
        catanBoardGameView.logToGameLog(thief + " stole 1 " + stolen + " from Player " + victim);
//        if (gameplay.isGamePaused()) {
//
//        }
//        catanBoardGameView.logToGameLog("AI " + thief + " has ended their turn.");
//        catanBoardGameView.logToGameLog("_____________________________________________________\n");
        return true;
    }

    //_____________________________DISCARD LOGIC_____________________________________//
    // Function that actually removes the resources from Players
    public void discardResources(Player player, Map<String, Integer> discarded) {
        discarded.forEach((res, amt) -> {
            int current = player.getResources().getOrDefault(res, 0);
            player.getResources().put(res, Math.max(0, current - amt));
        });
    }


    // AI automatically discards cards
    public Map<String, Integer> AIChooseCardsToDiscard(AIOpponent ai) {
        Map<String, Integer> resources = new HashMap<>(ai.getResources());
        int total = resources.values().stream().mapToInt(Integer::intValue).sum();
        int toDiscard = total / 2;
        if (toDiscard == 0) return null;

        Map<String, Integer> discardMap = new HashMap<>();
        Map<String, Integer> priorityScores = new HashMap<>();
        // Resources AI needs for next strategy (e.g. city/settlement/dev card)
        AIOpponent.Strategy strategy = ai.determineStrategy(false);
        Set<String> neededResources = ai.getNeededResourcesForStrategy(strategy);

        for (String res : resources.keySet()) {
            int amountOwned = resources.getOrDefault(res, 0);
            int productionScore = 0;

            // Production score: total dice weight from settlements
            productionScore = ai.getProductionScore(res);

            // Base score: more owned = more discardable
            int score = amountOwned * 3;

            // Subtract based on production (more produced → less discardable)
            score -= productionScore * 2;

            // Penalize if resource is needed for strategy
            if (neededResources.contains(res)) {
                score -= 8;
            }
            // Penalize if resource is easy to trade (2:1 or 3:1 harbor = valuable)
            int ratio = ai.getBestTradeRatio(res, ai);
            if (ratio <= 2) score -= 6;
            else if (ratio == 3) score -= 3;
            priorityScores.put(res, score);
        }
        // Sort by lowest score (most discardable first)
        List<String> discardOrder = priorityScores.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .toList();

        for (String res : discardOrder) {
            if (toDiscard == 0) break;
            int available = resources.get(res);
            int discard = Math.min(available, toDiscard);
            if (discard > 0) {
                discardMap.put(res, discard);
                toDiscard -= discard;
            }
        }
        // Optional log
        StringBuilder discardText = new StringBuilder(ai + " auto-discarded: ");
        discardMap.forEach((res, amt) -> discardText.append(amt).append(" ").append(res).append(", "));
        if (!discardMap.isEmpty()) {
            discardText.setLength(discardText.length() - 2); // remove trailing comma
            gameplay.getCatanBoardGameView().logToGameLog(discardText.toString());
        }
        return discardMap;
    }

    // Robber Logic: auto-discard for human players
    public Map<String, Integer> autoDiscardCardsHuman(Player player) {
        Map<String, Integer> resourcesCopy = new HashMap<>(player.getResources());
        int total = resourcesCopy.values().stream().mapToInt(Integer::intValue).sum();
        int toDiscard = total / 2;
        if (toDiscard == 0) return null;
        Map<String, Integer> discardMap = new HashMap<>();

        // Simple heuristic: discard from most abundant resource
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(resourcesCopy.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue())); // highest first

        for (Map.Entry<String, Integer> entry : sorted) {
            if (toDiscard == 0) break;
            String res = entry.getKey();
            int available = entry.getValue();
            int discard = Math.min(available, toDiscard);
            if (discard > 0) {
                discardMap.put(res, discard);
                toDiscard -= discard;
            }
        }
        // Log discards
        StringBuilder log = new StringBuilder(player + " auto discarded: ");
        discardMap.forEach((res, amt) -> log.append(amt).append(" ").append(res).append(", "));
        if (!discardMap.isEmpty()) {
            log.setLength(log.length() - 2); // remove trailing comma
            gameplay.getCatanBoardGameView().logToGameLog(log.toString());
        }
        return discardMap;
    }

    //___________________________HELPER FUNCTIONS________________________________//
    private void hideButtons() {
        catanBoardGameView.hideDiceButton();
        catanBoardGameView.hideTurnButton();
    }
    private void showButtons() {
        if (gameplay.hasRolledDice()) {
            catanBoardGameView.showTurnButton();
        }
        else {
            catanBoardGameView.showDiceButton();
        }
    }

    public void moveTo(Tile newTile) {
        this.currentTile = newTile;
    }
}