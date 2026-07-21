package org.example.catanboardgameapp;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.example.catanboardgameviews.CatanBoardGameView;
import org.example.controller.BuildController;
import java.io.InputStream;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

// Handles everything related to drawing on the board, settlements and cities, etc with updates
// and all displays and UI features seen in the game, popups, warnings etc.
public class DrawOrDisplay {

    // UI components
    private StackPane aiOverlayPane;
    private Label thinkingLabel;
    private ImageView thinkingImage;
    private RotateTransition rotateAnimation;

    // Dice faces decoded once and reused (index = dice value 2..12), so a roll does not
    // re-open and re-decode the PNGs every time.
    private final Image[] diceImageCache = new Image[13];

    // Click highlights
    private final List<Circle> vertexClickHighlights = new ArrayList<>();
    // Clickable road lines, kept so the board-level dispatcher can hit-test them.
    private final List<Line> edgeClickLines = new ArrayList<>();
    // Currently hovered targets, so the dispatcher can clear the highlight on move.
    private Circle hoverCircle;
    private Line hoverLine;
    // When true, every clickable settlement spot is painted a faint green (a debug aid).
    // Kept OFF in production: painting ~100 non-transparent discs makes JPro re-raster and
    // stream all of them; the spot under the cursor is shown by the hover highlight instead.
    private static final boolean DEBUG_CLICK_TARGETS = false;

    private final int boardRadius;
    private final Gameplay gameplay;

    //______________________________CONSTRUCTOR________________________________//
    public DrawOrDisplay(int boardRadius, Gameplay gameplay) {
        this.gameplay = gameplay;
        this.boardRadius = boardRadius;
    }

    //___________________________CLICK INITIALIZATION___________________________//
    public void initEdgesClickHandlers(Board board, BuildController controller) {
        Group edgeBaseLayer = controller.getGameController().getGameView().getEdgeBaseLayer();
        Group edgeClickLayer = controller.getGameController().getGameView().getEdgeClickLayer();

        for (Edge edge : board.getEdges()) {
            if (edge.isSeaOnly()) continue;
            Line visible = new Line(edge.getVertex1().getX(), edge.getVertex1().getY(),
                    edge.getVertex2().getX(), edge.getVertex2().getY());
            visible.setStroke(Color.WHITE);
            visible.setStrokeWidth(0.8 * (10.0 / boardRadius));
            edgeBaseLayer.getChildren().add(visible);

            Line clickable = new Line(edge.getVertex1().getX(), edge.getVertex1().getY(),
                    edge.getVertex2().getX(), edge.getVertex2().getY());
            double clickableWidth = 1.2 * (10.0 / boardRadius);
            clickable.setStrokeWidth(clickableWidth);
            clickable.setStroke(Color.GOLD);
            clickable.setOpacity(0);
            // Mouse-transparent: clicks/hover are routed by the board-level dispatcher,
            // which hit-tests in scene space so it is correct at any zoom/pan. JPro does
            // not invert the board's scale transform for its own node picking.
            clickable.setMouseTransparent(true);
            clickable.setOnMouseClicked(controller.createRoadClickHandler(edge)); // invoked by the dispatcher
            edgeClickLines.add(clickable);
            edgeClickLayer.getChildren().add(clickable);
        }
    }

    public void initVerticeClickHandlers(Board board, BuildController controller, BorderPane root) {
        Group settlementLayer = controller.getGameController().getGameView().getSettlementLayer();
        Group edgeClickLayer = controller.getGameController().getGameView().getEdgeClickLayer();

        for (Vertex vertex : board.getVertices()) {
            if (vertex.isSeaOnly()) continue;

            double visibleRadius = 10.0 / boardRadius;
            double clickableRadius = 20.0 / boardRadius;

            Circle visible = new Circle(vertex.getX(), vertex.getY(), visibleRadius);
            visible.setFill(Color.TRANSPARENT);
            visible.setStroke(Color.TRANSPARENT);
            settlementLayer.getChildren().add(visible);

            Circle clickable = new Circle(vertex.getX(), vertex.getY(), clickableRadius);
            // Mouse-transparent: clicks/hover routed by the board-level dispatcher (see above).
            clickable.setMouseTransparent(true);
            clickable.setOnMouseClicked(controller.createSettlementClickHandler(visible, vertex, root)); // invoked by dispatcher
            clickable.setFill(idleTargetFill());
            clickable.setStroke(idleTargetStroke());
            clickable.setStrokeWidth(0.3);
            vertexClickHighlights.add(clickable);
            edgeClickLayer.getChildren().add(clickable);
        }
    }
    //___________________________BOARD CLICK / HOVER DISPATCH___________________________//
    // JPro renders the board's zoom (boardGroup.setScaleX/Y) but does not invert that
    // scale when picking which node the mouse hit, so direct per-node clicks only work at
    // zoom 1.0. Instead we capture clicks/moves on the board wrapper and hit-test every
    // target against its LIVE scene position (localToScene reflects the current zoom+pan),
    // then invoke that target's stored handler. Correct at any resolution/zoom/pan/board size.
    public void dispatchBoardClick(MouseEvent event) {
        Circle vertex = findNearestVertexCircle(event.getSceneX(), event.getSceneY());
        if (vertex != null && vertex.getOnMouseClicked() != null) {
            vertex.getOnMouseClicked().handle(event);
            return;
        }
        Line edge = findNearestEdgeLine(event.getSceneX(), event.getSceneY());
        if (edge != null && edge.getOnMouseClicked() != null) {
            edge.getOnMouseClicked().handle(event);
        }
    }

    public void dispatchBoardHover(MouseEvent event) {
        // Resolve what the pointer is over now (a vertex takes priority over an edge).
        Circle vertex = findNearestVertexCircle(event.getSceneX(), event.getSceneY());
        Line edge = (vertex == null) ? findNearestEdgeLine(event.getSceneX(), event.getSceneY()) : null;

        // Nothing changed since the last move: do NOT touch the scene graph. Under JPro every
        // property write forces a server-side re-render + stream, so this guard is what stops
        // the board from repainting on every pixel of mouse movement (the main source of lag).
        if (vertex == hoverCircle && edge == hoverLine) return;

        // Clear the previous highlight.
        if (hoverCircle != null) {
            hoverCircle.setFill(idleTargetFill());
            hoverCircle.setStroke(idleTargetStroke());
            hoverCircle.setStrokeWidth(0.3);
            hoverCircle = null;
        }
        if (hoverLine != null) {
            hoverLine.setOpacity(0);
            hoverLine = null;
        }

        // Apply the new highlight in the current player's colour.
        Color playerColor = currentPlayerColor();
        if (vertex != null) {
            vertex.setFill(Color.color(playerColor.getRed(), playerColor.getGreen(), playerColor.getBlue(), 0.55));
            vertex.setStroke(playerColor);
            vertex.setStrokeWidth(2.0 / boardRadius);
            hoverCircle = vertex;
        } else if (edge != null) {
            edge.setStroke(playerColor);
            edge.setOpacity(0.7);
            hoverLine = edge;
        }
    }

    private Color currentPlayerColor() {
        Player current = gameplay.getCurrentPlayer();
        return current != null ? current.getColor() : Color.GOLD;
    }

    // Map a scene-space click into the board's UNSCALED local coordinates. JPro honors the
    // board group's translate (panning works) but ignores its scale when delivering input,
    // so we invert the parent chain + translate but NOT the scale. Targets are then compared
    // in their own unscaled local coordinates, which is correct at any in-game zoom.
    private Point2D boardLocalFromScene(double sceneX, double sceneY) {
        // Zoom now uses an explicit origin-pivot Scale transform (see CatanBoardGameView),
        // so the standard inverse is correct and bounds-independent.
        return gameplay.getCatanBoardGameView().getBoardGroup().sceneToLocal(sceneX, sceneY);
    }

    private Circle findNearestVertexCircle(double sceneX, double sceneY) {
        Point2D p = boardLocalFromScene(sceneX, sceneY);
        Circle best = null;
        double bestDist = Double.MAX_VALUE;
        for (Circle c : vertexClickHighlights) {
            double dist = Math.hypot(c.getCenterX() - p.getX(), c.getCenterY() - p.getY());
            double threshold = c.getRadius() * 1.4;
            if (dist <= threshold && dist < bestDist) {
                bestDist = dist;
                best = c;
            }
        }
        return best;
    }

    private Line findNearestEdgeLine(double sceneX, double sceneY) {
        Point2D p = boardLocalFromScene(sceneX, sceneY);
        Line best = null;
        double bestDist = Double.MAX_VALUE;
        for (Line l : edgeClickLines) {
            double dist = pointToSegmentDistance(p.getX(), p.getY(),
                    l.getStartX(), l.getStartY(), l.getEndX(), l.getEndY());
            double threshold = l.getStrokeWidth() * 1.5;
            if (dist <= threshold && dist < bestDist) {
                bestDist = dist;
                best = l;
            }
        }
        return best;
    }

    private static double pointToSegmentDistance(double px, double py, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1;
        double len2 = dx * dx + dy * dy;
        if (len2 == 0) return Math.hypot(px - x1, py - y1);
        double t = ((px - x1) * dx + (py - y1) * dy) / len2;
        t = Math.max(0, Math.min(1, t));
        return Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
    }

    private Color idleTargetFill() {
        return DEBUG_CLICK_TARGETS ? Color.rgb(0, 255, 0, 0.2) : Color.TRANSPARENT;
    }

    private Color idleTargetStroke() {
        return DEBUG_CLICK_TARGETS ? Color.BLACK : Color.TRANSPARENT;
    }

    //_____________________________________FUNCTIONS_________________________________________//
    public Rectangle createBoxBehindDiceNumber(Text sample, double centerX, double centerY) {
        double padding = 5.0 / boardRadius * 10;
        double boxW = sample.getLayoutBounds().getWidth() + padding;
        double boxH = sample.getLayoutBounds().getHeight() + padding;
        Rectangle rectangle = new Rectangle(centerX - boxW / 2, centerY - boxH / 2, boxW, boxH);
        rectangle.setFill(Color.BEIGE);
        rectangle.setStroke(Color.BLACK);
        rectangle.setArcWidth(5);
        rectangle.setArcHeight(5);
        return rectangle;
    }

    public Polygon createTilePolygon(Tile tile) {
        Polygon polygon = new Polygon();
        for (Vertex v : tile.getVertices()) {
            polygon.getPoints().addAll(v.getX(), v.getY());
        }
        return polygon;
    }
    public Image loadDiceImage(int number) {
        if (number >= 0 && number < diceImageCache.length && diceImageCache[number] != null) {
            return diceImageCache[number];
        }
        String path = "/dice/dice" + number + ".png";
        InputStream stream = CatanBoardGameView.class.getResourceAsStream(path);
        if (stream == null) {
            System.err.println("Could not load image: " + path);
            return new WritableImage(1, 1); // harmless transparent placeholder (no error.png asset exists)
        }
        Image image = new Image(stream);
        if (number >= 0 && number < diceImageCache.length) {
            diceImageCache[number] = image;
        }
        return image;
    }
    public StackPane buildAIOverlay() {
        thinkingLabel = new Label("Waiting for AI...");
        thinkingLabel.setStyle("-fx-font-size: 26px; -fx-text-fill: white; -fx-font-weight: bold;");
        thinkingLabel.setOpacity(0.85);

        // Decode this 6.3 MB PNG down to ~240px at load (it is only shown at 120px), instead
        // of holding the full-resolution bitmap. The overlay spins continuously during AI
        // turns, so a smaller bitmap means far less to re-raster and stream each frame.
        Image img = new Image(getClass().getResource("/Icons/robot_think.png").toExternalForm(), 240, 240, true, true);
        thinkingImage = new ImageView(img);
        thinkingImage.setFitWidth(120);
        thinkingImage.setFitHeight(120);

        // Rotate animation
        rotateAnimation = new RotateTransition(Duration.seconds(4), thinkingImage);
        rotateAnimation.setByAngle(360);
        rotateAnimation.setCycleCount(Animation.INDEFINITE);
        rotateAnimation.setInterpolator(Interpolator.LINEAR);

        VBox content = new VBox(20, thinkingImage, thinkingLabel);
        content.setAlignment(Pos.CENTER);

        aiOverlayPane = new StackPane(content);
        aiOverlayPane.setStyle("-fx-background-color: rgba(0, 0, 0, 0.6);");
        aiOverlayPane.setVisible(false);
        aiOverlayPane.setMouseTransparent(true);

        return aiOverlayPane;
    }

    //_____________________________________DRAWING_________________________________________//
    public void drawRoad(Line line, Player player, Group boardGroup) {
        line.setStroke(player.getColor());
        line.setStrokeWidth(1.5 * (10.0 / boardRadius));
        boardGroup.getChildren().add(line);
    }
    public void drawSettlement(Circle circle, Vertex vertex, Group boardGroup) {
        if (vertex.getOwner() != null) {
            circle.setFill(vertex.getOwner().getColor());
            circle.setRadius(20.0 / boardRadius);
        } else {
            System.out.println("SOMETHING WRONG DRAWING WITHOUT ANY PLAYER AS OWNER???");
            circle.setFill(Color.TRANSPARENT);
            circle.setRadius(10.0 / boardRadius);
        }

        // 1. Add circle
        boardGroup.getChildren().add(circle);

    }
    public void drawCity(Vertex vertex, Group boardGroup) {
        double radius = 24.0 / boardRadius;
        Polygon cityShape = new Polygon();
        double x = vertex.getX();
        double y = vertex.getY();

        // Example shape: a hexagon to represent a city
        double height = radius * Math.sqrt(3) / 2;
        double yOffset = 0.0; // pixels downward
        cityShape.getPoints().addAll(
                x,             y - radius + yOffset,
                x + height,    y - radius / 2 + yOffset,
                x + height,    y + radius / 2 + yOffset,
                x,             y + radius + yOffset,
                x - height,    y + radius / 2 + yOffset,
                x - height,    y - radius / 2 + yOffset
        );
        if (vertex.getOwner() != null) {
            cityShape.setFill(vertex.getOwner().getColor());
        } else {
            cityShape.setFill(Color.GRAY);
        }
        cityShape.setStroke(Color.BLACK);
        cityShape.setStrokeWidth(1.0);
        boardGroup.getChildren().add(cityShape);
    }


    public void drawHarbors(List<Tile> tiles, Group boardGroup) {
        for (Tile tile : tiles) {
            Harbor harbor = tile.getHarbor();
            if (harbor == null) continue;
            Edge edge = harbor.getEdge();
            Vertex v1 = edge.getVertex1();
            Vertex v2 = edge.getVertex2();
            Point2D center = tile.getCenter();
            double centerX = center.getX();
            double centerY = center.getY();
            String text = (harbor.getType().specific == null)
                    ? "3:1"
                    : "2:1\n" + harbor.getType().specific.getName().toUpperCase();

            Text label = new Text(text);
            label.setFont(Font.font("Arial", FontWeight.BOLD, 30.0 / boardRadius));
            label.setTextAlignment(TextAlignment.CENTER);

            Text sample = new Text("2:1\nWOOL");
            sample.setFont(label.getFont());
            Rectangle box = createBoxBehindDiceNumber(sample, centerX, centerY);

            label.setX(centerX - label.getLayoutBounds().getWidth() / 2);
            label.setY(centerY + label.getLayoutBounds().getHeight() / 3);

            double dockWidth = 8.0 / boardRadius;
            Line dock1 = new Line(v1.getX(), v1.getY(), centerX, centerY);
            Line dock2 = new Line(v2.getX(), v2.getY(), centerX, centerY);
            dock1.setStroke(Color.BLACK);
            dock2.setStroke(Color.BLACK);
            dock1.setStrokeWidth(dockWidth);
            dock2.setStrokeWidth(dockWidth);

            boardGroup.getChildren().addAll(dock1, dock2, box, label);
        }
    }
    public void drawErrorCross(Group boardGroup, double x, double y) {
        double size = 10.0 / boardRadius;
        Line line1 = new Line(x - size, y - size, x + size, y + size);
        Line line2 = new Line(x - size, y + size, x + size, y - size);

        line1.setStroke(Color.RED);
        line2.setStroke(Color.RED);
        line1.setStrokeWidth(2);
        line2.setStrokeWidth(2);

        Group error = new Group(line1, line2);
        boardGroup.getChildren().add(error);

        PauseTransition delay = new PauseTransition(Duration.seconds(1));
        delay.setOnFinished(e -> boardGroup.getChildren().remove(error));
        delay.play();
    }

    //_____________________________________CREATE DIALOGS AND POPUPS_________________________________________//
    public void showEndGamePopup(
            Player winner,
            List<Player> playerList,
            int turnCounter,
            double gameWidth,
            double gameHeight,
            Runnable onClose
    ) {
        CatanBoardGameView view = gameplay.getCatanBoardGameView();

        VBox content = new VBox(15);
        content.setPadding(new Insets(15));
        content.setAlignment(Pos.TOP_CENTER);
        content.setStyle("""
        -fx-background-color: linear-gradient(to bottom, #f9ecd1, #d2a86e);
        -fx-border-color: #8c5b1a;
        -fx-border-width: 2;
        -fx-border-radius: 10;
        -fx-background-radius: 10;
    """);

        Label header = new Label("Player " + winner.getPlayerId() + " has won the game! (It took them " + turnCounter + " turns)");
        header.setFont(Font.font("Georgia", FontWeight.BOLD, 18));
        header.setTextFill(Color.DARKGREEN);

        VBox playerStats = new VBox(12);
        playerStats.setPadding(new Insets(10));

        // Sort by score, descending
        List<Player> sortedPlayers = playerList.stream()
                .sorted((a, b) -> Integer.compare(b.getPlayerScore(), a.getPlayerScore()))
                .toList();

        for (Player player : sortedPlayers) {
            VBox box = new VBox(6);
            box.setPadding(new Insets(10));
            box.setStyle("""
            -fx-background-color: linear-gradient(to bottom, #f3e2c7, #e0b97d);
            -fx-border-color: #a86c1f;
            -fx-border-width: 1.5;
            -fx-background-radius: 8;
            -fx-border-radius: 8;
        """);

            String displayName = (player instanceof AIOpponent ai)
                    ? "AI Player " + ai.getPlayerId() + " (" + ai.getStrategyLevel().name() + ")"
                    : "Player " + player.getPlayerId();

            Text name = new Text(displayName + " : " + player.getPlayerScore() + " points");
            name.setFont(Font.font("Georgia", FontWeight.BOLD, 14));
            name.setFill(player.getColor());
            box.getChildren().add(name);

            //Base stats
            int resources = player.getResources().values().stream().mapToInt(Integer::intValue).sum();
            int devCards = player.getDevelopmentCards().values().stream().mapToInt(Integer::intValue).sum();

            Text resText = new Text("Current Resources: " + resources);
            Text devText = new Text("Current Development Cards: " + devCards);
            Text lroadText = new Text("Longest Road: " + player.getLongestRoad());
            Text knightText = new Text("Biggest Army: " + player.getPlayedKnights());
            Text cityText = new Text("Cities: " + player.getCities().size());
            Text settlementText = new Text("Settlements: " + player.getSettlements().size());
            Text roadText = new Text("Roads: " + player.getRoads().size());

            List<Text> baseStats = List.of(
                    resText, devText, lroadText, knightText,
                    cityText, settlementText, roadText
            );
            baseStats.forEach(stat -> stat.setFont(Font.font("Georgia", 12)));
            box.getChildren().addAll(baseStats);

            if (player instanceof AIOpponent ai) {
                VBox strategyBox = new VBox(4);
                for (Map.Entry<AIOpponent.Strategy, Integer> entry : ai.getStrategyUsageMap().entrySet()) {
                    Text stat = new Text("* " + entry.getKey().name() + ": " + entry.getValue() + " times");
                    stat.setFont(Font.font("Georgia", 12));
                    strategyBox.getChildren().add(stat);
                }

                TitledPane togglePane = new TitledPane("Strategy Usage", strategyBox);
                togglePane.setExpanded(false);
                togglePane.setFont(Font.font("Georgia", FontWeight.NORMAL, 12));
                box.getChildren().add(togglePane);
            }
            playerStats.getChildren().add(box);
        }
        ScrollPane scrollPane = new ScrollPane(playerStats);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(500);
        scrollPane.setStyle("""
        -fx-background: transparent;
        -fx-border-color: #a86c1f;
        -fx-border-radius: 8;
    """);
        Button closeBtn = new Button("Back to Main Menu");
        closeBtn.setFont(Font.font("Georgia", FontWeight.BOLD, 14));
        closeBtn.setStyle("""
        -fx-background-color: linear-gradient(to bottom, #d8b173, #a86c1f);
        -fx-text-fill: black;
    """);
        closeBtn.setOnAction(e -> {
            view.hideOverlay();
            if (onClose != null) onClose.run();
        });
        content.getChildren().addAll(header, scrollPane, closeBtn);
        content.setMaxWidth(560);
        view.showOverlay(content);
    }

    public void showBuildingCostsPopup() {
        InputStream imageStream = CatanBoardGameView.class.getResourceAsStream("/UI/catanBuildingCosts.png");
        if (imageStream == null) {
            System.err.println("Could not load building_costs.png");
            return;
        }
        ImageView imageView = new ImageView(new Image(imageStream));
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(400);

        CatanBoardGameView view = gameplay.getCatanBoardGameView();
        VBox card = view.overlayCard("Building Costs", null);
        Button close = view.overlayButton("Close");
        close.setOnAction(e -> view.hideOverlay());
        card.getChildren().addAll(imageView, close);
        view.showOverlay(card);
    }

    // Non-blocking robber-victim picker. Invokes onChosen with the selected player.
    public void showRobberVictimDialog(List<Player> victims, Consumer<Player> onChosen) {
        if (victims == null || victims.isEmpty()) {
            if (onChosen != null) onChosen.accept(null);
            return;
        }
        ComboBox<Player> comboBox = new ComboBox<>();
        comboBox.getItems().addAll(victims);
        comboBox.getSelectionModel().selectFirst();

        CatanBoardGameView view = gameplay.getCatanBoardGameView();
        VBox card = view.overlayCard("Steal a resource",
                "Select a player with a settlement or city on this tile:");
        Button steal = view.overlayButton("Steal");
        steal.setOnAction(e -> {
            Player chosen = comboBox.getValue();
            view.hideOverlay();
            if (onChosen != null) onChosen.accept(chosen);
        });
        card.getChildren().addAll(comboBox, steal);
        view.showOverlay(card);
    }

    public void notEnoughResourcesPopup(String message) {
        showCustomPopup("Not enough resources", message, true);
    }

    public void rollDiceBeforeActionPopup(String message) {
        showCustomPopup("Action Blocked", message, true);
    }

    public void showAITurnPopup() {
        showAlert(Alert.AlertType.INFORMATION,"AI Turn in Progress",null,"Wait for AI to finish turn before making moves",null);
    }

    public void showTradeError(String message) {
        showAlert(Alert.AlertType.ERROR, "Trade Error", null, message,null);
    }

    public void showMaxRoadsReachedPopup() {
        showAlert(Alert.AlertType.ERROR, "Max Roads Reached", "You already built 15 roads", "You cannot build more than 15 roads in the game.",null);
    }
    public void showMaxSettlementsReachedPopup() {
        showAlert(Alert.AlertType.ERROR, "Max Settlements Reached", "You already built 5 settlements", "You cannot build more than 5 settlements in the game.",null);
    }

    public void showMaxCitiesReachedPopup() {
        showAlert(Alert.AlertType.ERROR, "Max Cities Reached", "You already built 4 cities", "You cannot build more than 4 roads in the game.", null);
    }

    public void showFailToBuyDevelopmentCardPopup() {
        showAlert(Alert.AlertType.ERROR, "Insufficient Resources", "You do not have enough resources to buy a development card", "You need 1 grain, 1 wool, and 1 ore", null);
    }

    public void showNoMoreDevelopmentCardToBuyPopup() {
        showAlert(Alert.AlertType.ERROR, "No More Development Cards", "There are no more development cards left in the game.", "Buy something else.", null);
    }

    public void showFinishDevelopmentCardActionPopup() {
        showAlert(Alert.AlertType.WARNING, "Action Required: Development Card", "Complete your development card action first.", "You must finish using your current development card before performing any other actions.", null);
    }

    public void showDiscardDialog(Player player, int toDiscard, Map<String, Integer> playerResources,
                                  Gameplay gameplay, Consumer<Map<String, Integer>> onConfirm) {
        List<String> resources = new ArrayList<>(playerResources.keySet());
        showResourceSelectionDialog(
                "Discard Resources",
                player + ", you must discard " + toDiscard + " resource cards.",
                resources,
                toDiscard,
                false,       // allowAutoSelection
                null,                        // auto-selection function
                playerResources,
                onConfirm
        );
    }

    // Non-blocking monopoly picker. Invokes onChosen with the selected resource name.
    public void showMonopolyDialog(Consumer<String> onChosen) {
        List<String> resources = Arrays.asList("Ore", "Wood", "Brick", "Grain", "Wool");
        ComboBox<String> comboBox = new ComboBox<>();
        comboBox.getItems().addAll(resources);
        comboBox.getSelectionModel().selectFirst();

        CatanBoardGameView view = gameplay.getCatanBoardGameView();
        VBox card = view.overlayCard("Monopoly", "Select a resource to monopolize:");
        Button confirm = view.overlayButton("Confirm");
        confirm.setOnAction(e -> {
            String chosen = comboBox.getValue();
            view.hideOverlay();
            if (onChosen != null) onChosen.accept(chosen);
        });
        card.getChildren().addAll(comboBox, confirm);
        view.showOverlay(card);
    }

    public void showYearOfPlentyDialog(Map<String, Integer> playerResources, Consumer<Map<String, Integer>> onConfirm) {
        List<String> resources = Arrays.asList("Ore", "Wood", "Brick", "Grain", "Wool");
        showResourceSelectionDialog(
                "Year of Plenty",
                "Select exactly 2 resources to gain from the bank:",
                resources,
                2,
                false,
                null,
                null,          // unlimited, you can pick any resource
                onConfirm
        );
    }

    //___________________________________POPUP HELPER FUNCTIONS____________________________________//
    // Non-blocking info/acknowledge popup via the in-scene overlay (JPro-safe).
    public void showAlert(Alert.AlertType type, String title, String header, String content, Runnable onClose) {
        String message = (header != null && !header.isBlank() ? header + "\n\n" : "")
                + (content != null ? content : "");
        Platform.runLater(() -> gameplay.getCatanBoardGameView().showInfoOverlay(title, message, onClose));
    }

    public void showCustomPopup(String title, String message, boolean runLater) {
        Runnable task = () -> gameplay.getCatanBoardGameView().showInfoOverlay(title, message, null);
        if (runLater) {
            Platform.runLater(task);
        } else {
            task.run();
        }
    }
    // Non-blocking resource selection overlay (multiple +/- counters). Invokes onConfirm
    // with the chosen resource->count map when the player confirms.
    private void showResourceSelectionDialog(
            String title,
            String message,
            List<String> resources,
            int maxSelection,
            boolean allowAutoSelection,
            Supplier<Map<String, Integer>> autoSelectionSupplier,
            Map<String, Integer> ownedResourceMap,
            Consumer<Map<String, Integer>> onConfirm
    ) {
        CatanBoardGameView view = gameplay.getCatanBoardGameView();

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        grid.getColumnConstraints().addAll(
                new ColumnConstraints(100),
                new ColumnConstraints(),
                new ColumnConstraints(),
                new ColumnConstraints(),
                new ColumnConstraints(50)
        );

        Map<String, Integer> selection = new HashMap<>();
        Map<String, Text> counterTexts = new HashMap<>();
        Button confirmButton = view.overlayButton("Confirm");
        confirmButton.setDisable(true);

        int row = 0;
        for (String resource : resources) {
            selection.put(resource, 0);

            Text label = new Text(resource);
            Button minus = new Button("-");
            Button plus = new Button("+");
            Text counter = new Text("0");

            counter.setWrappingWidth(30);
            counter.setTextAlignment(TextAlignment.CENTER);
            counterTexts.put(resource, counter);

            int owned = ownedResourceMap != null ? ownedResourceMap.getOrDefault(resource, 0) : -1;
            Text ownedLabel = new Text(owned >= 0 ? "(" + owned + ")" : "");
            ownedLabel.setWrappingWidth(30);
            ownedLabel.setTextAlignment(TextAlignment.CENTER);

            minus.setDisable(true);
            plus.setDisable(owned == 0 && owned >= 0);

            plus.setOnAction(e -> {
                int current = selection.get(resource);
                if ((owned < 0 || current < owned) && getTotalSelected(selection) < maxSelection) {
                    current++;
                    selection.put(resource, current);
                    counter.setText(String.valueOf(current));
                    minus.setDisable(false);
                    if (owned >= 0 && current == owned) plus.setDisable(true);
                    confirmButton.setDisable(getTotalSelected(selection) != maxSelection);
                }
            });

            minus.setOnAction(e -> {
                int current = selection.get(resource);
                if (current > 0) {
                    current--;
                    selection.put(resource, current);
                    counter.setText(String.valueOf(current));
                    plus.setDisable(owned >= 0 && current == owned);
                    minus.setDisable(current == 0);
                    confirmButton.setDisable(getTotalSelected(selection) != maxSelection);
                }
            });

            grid.addRow(row++, label, minus, counter, plus, ownedLabel);
        }

        confirmButton.setOnAction(e -> {
            Map<String, Integer> chosen = new HashMap<>(selection);
            gameplay.resumeGame(true);
            view.hideOverlay();
            if (onConfirm != null) onConfirm.accept(chosen);
        });

        HBox buttons = new HBox(10, confirmButton);
        buttons.setAlignment(Pos.CENTER);

        if (allowAutoSelection && autoSelectionSupplier != null) {
            Button autoButton = view.overlayButton("Auto-Discard");
            autoButton.setOnAction(e -> {
                Map<String, Integer> autoResult = autoSelectionSupplier.get();
                if (autoResult != null && !autoResult.isEmpty()) {
                    gameplay.resumeGame(true);
                    view.hideOverlay();
                    if (onConfirm != null) onConfirm.accept(new HashMap<>(autoResult));
                }
            });
            buttons.getChildren().add(autoButton);
        }

        VBox card = view.overlayCard(title, message);
        card.getChildren().addAll(grid, buttons);
        view.showOverlay(card);
    }


    //__________________________AI OVERLAY FUNCTIONS______________________________//
    public void setThinkingMessage(String text) {
        thinkingLabel.setText(text);
    }
    public void startThinkingAnimation() {
        rotateAnimation.playFromStart();
    }
    public void stopThinkingAnimation() {
        rotateAnimation.stop();
    }
    public void pauseThinkingAnimation(DrawOrDisplay draw) {
        if (rotateAnimation != null) {
            draw.rotateAnimation.pause();
        }
    }
    public void resumeThinkingAnimation(DrawOrDisplay draw) {
        if (rotateAnimation != null) {
            draw.rotateAnimation.play();
        }
    }

    //________________________________ROBBER HELPERS_______________________________//
    // Removing old robber and drawing the new one
    public void drawNewRobberCircle(Tile newTile, Group boardGroup, Circle robberCircle, boolean isHighlighting) {
        // 1) JavaFX thread
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> drawNewRobberCircle(newTile, boardGroup, robberCircle, false));
            return;
        }
        // 2) Remove the circle if it’s already part of the board (unless highlighting all tiles)
        if (!isHighlighting) {boardGroup.getChildren().remove(robberCircle);}

        // 3) Move the circle to the centre of the destination tile
        Point2D center = newTile.getCenter();
        robberCircle.setCenterX(center.getX());
        robberCircle.setCenterY(center.getY());

        // 4) Add it back so it becomes visible at the new position
        boardGroup.getChildren().add(robberCircle);
    }

    // Creates the Robber Circle object, does not draw anything
    public Circle createRobberCircle() {
        double radius      = 40.0 / boardRadius;   // scales with board size
        double strokeWidth = 12.0 / boardRadius;
        Circle robberCircle = new Circle(radius);// centre defaults to (0, 0)
        robberCircle.setFill(Color.TRANSPARENT);
        robberCircle.setStroke(Color.BLACK);
        robberCircle.setStrokeWidth(strokeWidth);
        return robberCircle;   // still not attached or positioned
    }

    public List<Circle> createAndDrawRobberHighlights(Group boardGroup,
                                                      List<Circle> highlights,
                                                      Board board,
                                                      Consumer<Tile> onTileChosen) {
        // FX thread
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() ->
                    createAndDrawRobberHighlights(boardGroup, highlights, board, onTileChosen));
            return highlights;
        }
        // clear previous overlay
        boardGroup.getChildren().removeAll(highlights);
        highlights.clear();

        // one highlight per land tile
        for (Tile tile : board.getTiles()) {
            if (tile.isSea()) continue;
            Circle ring = createRobberCircle();
            drawNewRobberCircle(tile, boardGroup, ring, true);
            ring.setOnMouseClicked(e -> {
                // (a) clean up overlay
                boardGroup.getChildren().removeAll(highlights);
                highlights.clear();
                // (b) hand control back to game logic
                onTileChosen.accept(tile);
            });
            highlights.add(ring);
        }
        return highlights;
    }

    //___________________________________GETTERS____________________________________//
    public StackPane getOverlayPane() {
        return aiOverlayPane;
    }
    private int getTotalSelected(Map<String, Integer> map) {
        return map.values().stream().mapToInt(Integer::intValue).sum();
    }
}
