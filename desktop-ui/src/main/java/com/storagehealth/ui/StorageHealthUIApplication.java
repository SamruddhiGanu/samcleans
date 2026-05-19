package com.storagehealth.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * JavaFX entry point for the Storage Health Ranker desktop application.
 *
 * <p>The UI starts independently of the Spring Boot backend — the backend
 * runs as an embedded server on {@code localhost:8080} which the UI calls
 * via an HTTP client service.
 */
public class StorageHealthUIApplication extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            StorageHealthMainWindow mainWindow = new StorageHealthMainWindow();
            Scene scene = new Scene(mainWindow.getRoot(), 1280, 820);

            // Load the dark theme stylesheet
            String css = getClass().getResource("/styles/dark-theme.css").toExternalForm();
            scene.getStylesheets().add(css);

            primaryStage.setTitle("Storage Health Ranker");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(960);
            primaryStage.setMinHeight(640);
            primaryStage.setOnCloseRequest(e -> {
                handleClose();
                e.consume();
            });

            primaryStage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleClose() {
        // Persist any unsaved state here before exit
        System.exit(0);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
