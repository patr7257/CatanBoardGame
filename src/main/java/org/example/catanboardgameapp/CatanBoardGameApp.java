package org.example.catanboardgameapp;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.example.catanboardgameviews.MenuView;
import org.example.controller.GameController;

//_______________________MAIN APPLICATION ENTRY POINT_________________________//
public class CatanBoardGameApp extends Application {

    // Application Start
    @Override
    public void start(Stage primaryStage) {
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

    // Launch Game Method
    public static void main(String[] args) {
        launch(args);
    }
}
