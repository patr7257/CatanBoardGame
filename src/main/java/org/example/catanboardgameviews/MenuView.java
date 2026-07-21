package org.example.catanboardgameviews;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import org.example.catanboardgameapp.AIOpponent;
import org.example.catanboardgameapp.GameConfig;
import org.example.controller.GameController;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;


public class MenuView {

    private final double GAME_WIDTH = 1050;
    private final double GAME_HEIGHT = 700;

    private int playerCount = 3;
    private int boardSize = 3;
    private int AIOpponentsCountEASY = 0;
    private int AIOpponentsCountMEDIUM = 0;
    private int AIOpponentsCountHARD = 0;
    private int maxRoads = 15;
    private int maxSettlements = 5;
    private int maxCities = 4;
    private int maxVictoryPoints = 10;
    private AIOpponent.ThinkingSpeed aiSpeed = AIOpponent.ThinkingSpeed.MEDIUM;
    private final int[] humanPlayers = {3}, boardSizeVal = {3}, easyAI = {0}, mediumAI = {0}, hardAI = {0},
            maxRoadsVal = {15}, maxSettlementsVal = {5}, maxCitiesVal = {4}, maxVictoryPointsVal = {10};

    private final GameController gameController;
    private final Stage primaryStage;

    //__________________________CONSTRUCTOR_____________________________//
    public MenuView(Stage primaryStage, GameController gameController) {
        this.primaryStage = primaryStage;
        this.gameController = gameController;
    }
    //__________________________MAIN MENU_____________________________//
    public void showMainMenu() {
        VBox menuLayout = createMenuLayout();
        gameController.setRoot(menuLayout); // swap root on the shared Scene (JPro-safe)
        primaryStage.setTitle("Catan Board Game");
    }

    private VBox createMenuLayout() {
        VBox menuLayout = new VBox(25);
        menuLayout.setAlignment(Pos.CENTER);
        menuLayout.setPadding(new Insets(40));
        String filename = "/backgrounds/menuViewBackG.png";
        InputStream stream = getClass().getResourceAsStream(filename);
        assert stream != null;
        Image backgroundImage = new Image(stream);
        BackgroundImage bgImage = new BackgroundImage(
                backgroundImage,
                BackgroundRepeat.NO_REPEAT,
                BackgroundRepeat.NO_REPEAT,
                BackgroundPosition.DEFAULT,
                new BackgroundSize(
                        1, 1, true, true, false, false
                )
        );

        menuLayout.setBackground(new Background(bgImage));
        Label titleLabel = new Label("CATAN BOARD GAME");
        titleLabel.setFont(Font.font("Georgia", FontWeight.BOLD, 52));
        titleLabel.setTextFill(Color.web("#F9D849"));
        titleLabel.setEffect(new DropShadow());

        Button playButton = createMenuButton("Start New Game", 220, 60);
        Button aiTestButton = createMenuButton("Start AI vs AI Test Match", 220, 60);
        Button optionsButton = createMenuButton("Options", 220, 60);
        Button creditsButton = createMenuButton("Credits", 220, 60);
        Button quitButton = createMenuButton("Quit Game", 220, 60);
        Button resumeButton = createMenuButton("Resume current Game", 220, 60);

        playButton.setOnAction(e -> startGame());
        aiTestButton.setOnAction(e -> startAITestMatch()); // <-- New action
        optionsButton.setOnAction(e -> showOptionsMenu(primaryStage));
        creditsButton.setOnAction(e -> showCreditsScreen(primaryStage));
        quitButton.setOnAction(e -> primaryStage.close());

        resumeButton.setDisable(!gameController.hasSavedSession());
        resumeButton.setOnAction(e -> gameController.resumeGame());

        menuLayout.getChildren().addAll(
                titleLabel, playButton, aiTestButton, optionsButton, creditsButton, quitButton, resumeButton
        );
        return menuLayout;
    }
    //__________________________GAME STARTERS_____________________________//
    private void startGame() {
        // Ensure previous game state and threads are reset
        gameController.resetGame();
        gameController.startGame(playerCount, boardSize, AIOpponentsCountEASY, AIOpponentsCountMEDIUM, AIOpponentsCountHARD);
    }

    private void startAITestMatch() {  //REMOVE WHEN TESTING DONE!!!!
        System.out.println("AI VS AI TEST MATCH IS STARTING");

        // Set up AI-only test configuration
        playerCount = 0;
        boardSize = 3;
        AIOpponentsCountEASY = 1;
        AIOpponentsCountMEDIUM = 1;
        AIOpponentsCountHARD = 1;

        // Ensure any running threads and state are cleared
        gameController.resetGame();
        gameController.startGame(
                playerCount,
                boardSize,
                AIOpponentsCountEASY,
                AIOpponentsCountMEDIUM,
                AIOpponentsCountHARD
        );
    }

    //__________________________MENUS_____________________________//
    public void showOptionsMenu(Stage primaryStage) {
        VBox optionsLayout = new VBox(20);
        optionsLayout.setAlignment(Pos.CENTER);

        Image backgroundImage = new Image(Objects.requireNonNull(getClass().getResource("/backgrounds/optionsMenuBlue.png")).toExternalForm());
        BackgroundImage bgImage = new BackgroundImage(
                backgroundImage, BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT,
                BackgroundPosition.CENTER, new BackgroundSize(100, 100, false, false, false, true)
        );
        optionsLayout.setBackground(new Background(bgImage));

        Label optionsTitle = new Label("Game Options");
        optionsTitle.setFont(new Font("Georgia", 28));
        optionsTitle.setTextFill(Color.DARKRED);

        Label totalNote = new Label("Choose " + GameConfig.MIN_PLAYERS + "-" + GameConfig.MAX_PLAYERS + " Total Players");
        totalNote.setFont(Font.font("Georgia", 16));
        totalNote.setTextFill(Color.DARKRED);

        Font labelFont = Font.font("Georgia", FontWeight.BOLD, 14);
        Color fontColor = Color.DARKRED;

        Label[] labels = {
                new Label("Number of Human Players:"),
                new Label("Number of EASY AI:"),
                new Label("Number of MEDIUM AI:"),
                new Label("Number of HARD AI:"),
                new Label("Board Size (3-10):"),
                new Label("Max Roads per Player:"),
                new Label("Max Settlements per Player:"),
                new Label("Max Cities per Player:"),
                new Label("Max Victory Points per Player:"),
        };

        Label[] values = {
                new Label(String.valueOf(humanPlayers[0])),
                new Label(String.valueOf(easyAI[0])),
                new Label(String.valueOf(mediumAI[0])),
                new Label(String.valueOf(hardAI[0])),
                new Label(String.valueOf(boardSizeVal[0])),
                new Label(String.valueOf(maxRoadsVal[0])),
                new Label(String.valueOf(maxSettlementsVal[0])),
                new Label(String.valueOf(maxCitiesVal[0])),
                new Label(String.valueOf(maxVictoryPointsVal[0])),
        };
        for (int i = 0; i < labels.length; i++) {
            labels[i].setFont(labelFont);
            labels[i].setTextFill(fontColor);
            values[i].setFont(labelFont);
            values[i].setTextFill(fontColor);
        }
        CheckBox shufflePlayersCheckbox = new CheckBox("Shuffle Player Turn Order");
        shufflePlayersCheckbox.setFont(labelFont);
        shufflePlayersCheckbox.setTextFill(fontColor);
        shufflePlayersCheckbox.setSelected(true); // default ON
        ComboBox<AIOpponent.ThinkingSpeed> aiSpeedDropdown = new ComboBox<>();
        aiSpeedDropdown.getItems().addAll(AIOpponent.ThinkingSpeed.values());
        aiSpeedDropdown.setValue(aiSpeed);
        aiSpeedDropdown.setStyle("-fx-font-size: 14px;");

        aiSpeedDropdown.setOnAction(e -> {
            AIOpponent.ThinkingSpeed selected = aiSpeedDropdown.getValue();
            if (selected != null) {
                aiSpeed = selected;
            }
        });

        Runnable updateCounts = () -> {
            values[0].setText(String.valueOf(humanPlayers[0]));
            values[1].setText(String.valueOf(easyAI[0]));
            values[2].setText(String.valueOf(mediumAI[0]));
            values[3].setText(String.valueOf(hardAI[0]));
            values[4].setText(String.valueOf(boardSizeVal[0]));
            values[5].setText(String.valueOf(maxRoadsVal[0]));
            values[6].setText(String.valueOf(maxSettlementsVal[0]));
            values[7].setText(String.valueOf(maxCitiesVal[0]));
            values[8].setText(String.valueOf(maxVictoryPointsVal[0]));
        };

        Button[][] controls = new Button[9][2];
        for (int i = 0; i < 9; i++) {
            controls[i][0] = new Button("-");
            controls[i][1] = new Button("+");
            controls[i][0].setMinWidth(35);
            controls[i][1].setMinWidth(35);
        }

        controls[0][1].setOnAction(e -> { if (humanPlayers[0] + easyAI[0] + mediumAI[0] + hardAI[0] < GameConfig.MAX_PLAYERS) humanPlayers[0]++; updateCounts.run(); });
        controls[0][0].setOnAction(e -> { if (humanPlayers[0] > 0) humanPlayers[0]--; updateCounts.run(); });
        controls[1][1].setOnAction(e -> { if (humanPlayers[0] + easyAI[0] + mediumAI[0] + hardAI[0] < GameConfig.MAX_PLAYERS) easyAI[0]++; updateCounts.run(); });
        controls[1][0].setOnAction(e -> { if (easyAI[0] > 0) easyAI[0]--; updateCounts.run(); });
        controls[2][1].setOnAction(e -> { if (humanPlayers[0] + easyAI[0] + mediumAI[0] + hardAI[0] < GameConfig.MAX_PLAYERS) mediumAI[0]++; updateCounts.run(); });
        controls[2][0].setOnAction(e -> { if (mediumAI[0] > 0) mediumAI[0]--; updateCounts.run(); });
        controls[3][1].setOnAction(e -> { if (humanPlayers[0] + easyAI[0] + mediumAI[0] + hardAI[0] < GameConfig.MAX_PLAYERS) hardAI[0]++; updateCounts.run(); });
        controls[3][0].setOnAction(e -> { if (hardAI[0] > 0) hardAI[0]--; updateCounts.run(); });
        controls[4][1].setOnAction(e -> { if (boardSizeVal[0] < 10) boardSizeVal[0]++; updateCounts.run(); });
        controls[4][0].setOnAction(e -> { if (boardSizeVal[0] > 3) boardSizeVal[0]--; updateCounts.run(); });
        controls[5][1].setOnAction(e -> { if (maxRoadsVal[0] < 20) maxRoadsVal[0]++; updateCounts.run(); });
        controls[5][0].setOnAction(e -> { if (maxRoadsVal[0] > 1) maxRoadsVal[0]--; updateCounts.run(); });
        controls[6][1].setOnAction(e -> { if (maxSettlementsVal[0] < 10) maxSettlementsVal[0]++; updateCounts.run(); });
        controls[6][0].setOnAction(e -> { if (maxSettlementsVal[0] > 1) maxSettlementsVal[0]--; updateCounts.run(); });
        controls[7][1].setOnAction(e -> { if (maxCitiesVal[0] < 6) maxCitiesVal[0]++; updateCounts.run(); });
        controls[7][0].setOnAction(e -> { if (maxCitiesVal[0] > 1) maxCitiesVal[0]--; updateCounts.run(); });
        controls[8][1].setOnAction(e -> { if (maxVictoryPointsVal[0] < 16) maxVictoryPointsVal[0]++; updateCounts.run(); });
        controls[8][0].setOnAction(e -> { if (maxVictoryPointsVal[0] > 1) maxVictoryPointsVal[0]--; updateCounts.run(); });

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(15);
        grid.setAlignment(Pos.CENTER);
        for (int i = 0; i < 9; i++) {
            grid.add(labels[i], 0, i);
            grid.add(controls[i][0], 1, i);
            grid.add(values[i], 2, i);
            grid.add(controls[i][1], 3, i);
        }

        grid.add(shufflePlayersCheckbox, 2, 9);

        Label aiSpeedLabel = new Label("AI Thinking Speed:");
        aiSpeedLabel.setFont(labelFont);
        aiSpeedLabel.setTextFill(fontColor);
        grid.add(aiSpeedLabel, 0, labels.length);
        grid.add(aiSpeedDropdown, 2, labels.length);

        Button accept = acceptButton(aiSpeedDropdown);

        optionsLayout.getChildren().addAll(optionsTitle, totalNote, grid,shufflePlayersCheckbox, accept);
        gameController.setRoot(optionsLayout); // swap root on the shared Scene (JPro-safe)
    }

    public void showCreditsScreen(Stage primaryStage) {
        VBox creditsLayout = new VBox(20);
        creditsLayout.setAlignment(Pos.CENTER);
        creditsLayout.setPadding(new Insets(20));

        Image backgroundImage = new Image(Objects.requireNonNull(getClass().getResource("/backgrounds/optionsMenuBlue.png")).toExternalForm());
        BackgroundImage bgImage = new BackgroundImage(
                backgroundImage, BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT,
                BackgroundPosition.CENTER, new BackgroundSize(100, 100, false, false, false, true)
        );
        creditsLayout.setBackground(new Background(bgImage));

        Label title = new Label("Game Credits");
        title.setFont(new Font("Georgia", 28));
        title.setTextFill(Color.DARKRED);

        List<String> names = List.of("Johan", "Kajsa", "Lizette", "Patrick");
        VBox nameBox = new VBox(5);
        nameBox.setAlignment(Pos.CENTER);
        for (String name : names) {
            Label label = new Label("• " + name + " - Game Developer");
            label.setFont(Font.font("Georgia", FontWeight.NORMAL, 16));
            label.setTextFill(Color.DARKRED);
            nameBox.getChildren().add(label);
        }

        // Trademark disclaimer: Catan is a trademark of Catan GmbH; this is a
        // non-commercial student project, not affiliated with or endorsed by them.
        Label disclaimer = new Label(
                "Catan and Settlers of Catan are trademarks of Catan GmbH. " +
                "This is a non-commercial student project, not affiliated with " +
                "or endorsed by Catan GmbH.");
        disclaimer.setFont(Font.font("Georgia", FontWeight.NORMAL, 12));
        disclaimer.setTextFill(Color.DARKRED);
        disclaimer.setWrapText(true);
        disclaimer.setMaxWidth(520);
        disclaimer.setAlignment(Pos.CENTER);
        disclaimer.setStyle("-fx-text-alignment: center;");

        Button backButton = createMenuButton("Back", 180, 50);
        backButton.setOnAction(e -> showMainMenu());

        creditsLayout.getChildren().addAll(title, nameBox, disclaimer, backButton);
        gameController.setRoot(creditsLayout); // swap root on the shared Scene (JPro-safe)
    }

    //__________________________HELPER METHODS_____________________________//
    private Button createMenuButton(String text, int width, int height) {
        Button button = new Button(text);
        button.setPrefSize(width, height);
        button.setFont(new Font("Georgia", 20));
        button.setStyle("-fx-background-color: #6E2C00; -fx-text-fill: #fceabb; -fx-background-radius: 10;");
        button.setOnMouseEntered(e -> button.setStyle("-fx-background-color: #873600; -fx-text-fill: #fceabb; -fx-background-radius: 10;"));
        button.setOnMouseExited(e -> button.setStyle("-fx-background-color: #6E2C00; -fx-text-fill: #fceabb; -fx-background-radius: 10;"));
        return button;
    }

    private Button acceptButton(ComboBox<AIOpponent.ThinkingSpeed> aiSpeedDropdown) {
        Button accept = new Button("Accept Changes");
        accept.setStyle(" -fx-font-size: 18px; -fx-background-color: white; -fx-text-fill: #7b1e1e; -fx-padding: 10 20 10 20; -fx-background-radius: 8; -fx-border-radius: 8;");
        accept.setOnAction(e -> {
            int total = humanPlayers[0] + easyAI[0] + mediumAI[0] + hardAI[0];
            if (total < GameConfig.MIN_PLAYERS || total > GameConfig.MAX_PLAYERS) {
                System.out.println("Total players must be between " + GameConfig.MIN_PLAYERS + " and " + GameConfig.MAX_PLAYERS + ".");
                return;
            }
            playerCount = humanPlayers[0];
            boardSize = boardSizeVal[0];
            AIOpponentsCountEASY = easyAI[0];
            AIOpponentsCountMEDIUM = mediumAI[0];
            AIOpponentsCountHARD = hardAI[0];
            maxRoads = maxRoadsVal[0];
            maxSettlements = maxSettlementsVal[0];
            maxCities = maxCitiesVal[0];
            maxVictoryPoints = maxVictoryPointsVal[0];
            aiSpeed = aiSpeedDropdown.getValue();
            showMainMenu();
        });
        return accept;
    }

    //__________________________GETTERS_____________________________//

    public AIOpponent.ThinkingSpeed getSelectedAISpeed() {
        return aiSpeed;
    }

    public double getGAME_WIDTH() {
        return GAME_WIDTH;
    }

    public double getGAME_HEIGHT() {
        return GAME_HEIGHT;
    }

    public int getMaxRoads() {
        return maxRoads;
    }

    public int getMaxSettlements() {
        return maxSettlements;
    }

    public int getMaxCities() {
        return maxCities;
    }

    public int getMaxVictoryPoints() {
        return maxVictoryPoints;
    }

}
