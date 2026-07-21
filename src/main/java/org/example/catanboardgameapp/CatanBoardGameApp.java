package org.example.catanboardgameapp;

import com.jpro.webapi.WebAPI;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import org.example.catanboardgameviews.MenuView;
import org.example.controller.GameController;

//_______________________MAIN APPLICATION ENTRY POINT_________________________//
public class CatanBoardGameApp extends Application {

    // Concurrent-game cap. Under JPro (single server) every browser session is its own
    // Application instance running in the same JVM, so this shared limiter caps how many
    // games run at once across all visitors. The limit itself lives in
    // GameConfig.MAX_CONCURRENT_GAMES (raise it, and size the VPS up, to allow more).
    private static final ConcurrentGameLimiter GAME_LIMITER =
            new ConcurrentGameLimiter(GameConfig.MAX_CONCURRENT_GAMES);
    private boolean countsTowardLimit = false;

    // Application Start
    @Override
    public void start(Stage primaryStage) {
        // Enforce the cap: if every slot is taken, show a busy screen instead of starting a game.
        if (!GAME_LIMITER.tryAcquire()) {
            showServerBusy(primaryStage);
            return;
        }
        countsTowardLimit = true;

        // Release the slot as soon as JPro reports this browser session closing. This is more
        // reliable than stop() alone; releaseSlot() is idempotent so the two cannot double-free.
        // How quickly a disconnected tab is considered closed is set in resources/jpro.conf
        // (closeOnDisconnectAfter). A refresh within that window rejoins the same live game.
        if (WebAPI.isBrowser()) {
            WebAPI.getWebAPI(primaryStage).addInstanceCloseListener(this::releaseSlot);
        }

        // Initialize game controller (handles game logic and flow)
        GameController gameController = new GameController(primaryStage);

        // Initialize the menu view and link it to the controller
        MenuView menuView = new MenuView(primaryStage, gameController);
        gameController.setMenuView(menuView);

        // Create the single, persistent Scene for the whole app and set it on the
        // Stage exactly once. Every screen change swaps this Scene's root, never the
        // Scene itself, so JPro's browser input mapping stays bound and accurate.
        // No fixed Scene size: JPro sizes the Scene to the live browser viewport, and a
        // fixed size causes a letterbox/scale mismatch that offsets mouse input. The
        // desktop window size is set on the Stage instead (JPro ignores it in the browser).
        Scene scene = new Scene(new StackPane());
        primaryStage.setScene(scene);
        primaryStage.setTitle("Catan Board Game");
        primaryStage.setWidth(1050);
        primaryStage.setHeight(700);
        gameController.setScene(scene);

        // Display the main menu to the user
        menuView.showMainMenu();
        primaryStage.show();
    }

    // Release this session's slot when the browser session (or desktop window) ends.
    @Override
    public void stop() {
        releaseSlot();
    }

    // Give back this session's game slot exactly once (called from both the JPro instance-close
    // listener and stop(), whichever fires first).
    private synchronized void releaseSlot() {
        if (countsTowardLimit) {
            countsTowardLimit = false;
            GAME_LIMITER.release();
        }
    }

    // Shown when the concurrent-game cap is already reached. Uses the app's menu palette.
    private void showServerBusy(Stage primaryStage) {
        Label message = new Label(
                "Only one game of Catan can run at a time right now.\n" +
                "Please try again in a few minutes.");
        message.setFont(Font.font("Georgia", FontWeight.BOLD, 22));
        message.setTextFill(Color.web("#fceabb"));
        message.setWrapText(true);
        message.setAlignment(Pos.CENTER);
        message.setStyle("-fx-text-alignment: center;");

        StackPane root = new StackPane(message);
        root.setStyle("-fx-background-color: #6E2C00;");
        root.setPadding(new Insets(40));

        Scene scene = new Scene(root);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Catan Board Game - busy");
        primaryStage.setWidth(1050);
        primaryStage.setHeight(700);
        primaryStage.show();
    }

    // Launch Game Method
    public static void main(String[] args) {
        launch(args);
    }
}
