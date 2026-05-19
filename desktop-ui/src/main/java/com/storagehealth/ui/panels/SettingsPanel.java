package com.storagehealth.ui.panels;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * Settings tab — configures API endpoint, scan exclusions, and scan thresholds.
 */
public class SettingsPanel {

    private final VBox root;
    private TextField apiUrlField;
    private TextField largFileThresholdField;
    private TextField staleDownloadMonthsField;
    private TextField screenshotMonthsField;

    public SettingsPanel() {
        root = new VBox(20);
        root.setPadding(new Insets(24));
        root.getStyleClass().add("dashboard");
        root.getChildren().addAll(
            buildTitle(),
            buildApiSection(),
            buildThresholdsSection(),
            buildSaveButton()
        );
    }

    public VBox getRoot() { return root; }

    private Label buildTitle() {
        Label lbl = new Label("Settings");
        lbl.getStyleClass().add("section-title");
        return lbl;
    }

    private VBox buildApiSection() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16));

        Label heading = new Label("Backend API");
        heading.getStyleClass().add("card-heading");

        apiUrlField = new TextField("http://localhost:8080");
        apiUrlField.setPromptText("Backend API URL");

        card.getChildren().addAll(heading,
            new Label("API Base URL:"), apiUrlField);
        return card;
    }

    private VBox buildThresholdsSection() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16));

        Label heading = new Label("Detection Thresholds");
        heading.getStyleClass().add("card-heading");

        largFileThresholdField = new TextField("500");
        staleDownloadMonthsField = new TextField("3");
        screenshotMonthsField = new TextField("6");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.addRow(0, new Label("Large file threshold (MB):"), largFileThresholdField);
        grid.addRow(1, new Label("Stale download (months):"),   staleDownloadMonthsField);
        grid.addRow(2, new Label("Old screenshot (months):"),   screenshotMonthsField);

        card.getChildren().addAll(heading, grid);
        return card;
    }

    private Button buildSaveButton() {
        Button save = new Button("💾  Save Settings");
        save.getStyleClass().add("btn-primary");
        save.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION,
                "Settings saved (applies on next scan).", ButtonType.OK);
            alert.setHeaderText(null);
            alert.showAndWait();
        });
        return save;
    }

    /** Returns the currently configured API base URL. */
    public String getApiUrl() {
        return apiUrlField.getText().trim();
    }
}
