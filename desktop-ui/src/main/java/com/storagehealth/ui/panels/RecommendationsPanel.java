package com.storagehealth.ui.panels;

import com.storagehealth.ui.service.ApiClientService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;

/**
 * Recommendations tab — shows actionable storage-cleanup suggestions
 * grouped by type (TEMP_FILE, OLD_SCREENSHOT, UNUSED_LARGE_FILE, STALE_DOWNLOAD).
 */
public class RecommendationsPanel {

    private final VBox root;
    private final ApiClientService api = new ApiClientService();
    private TableView<RecommendationRow> table;
    private Label summaryLabel;
    private ComboBox<String> typeFilter;

    public RecommendationsPanel() {
        root = new VBox(14);
        root.setPadding(new Insets(24));
        root.getStyleClass().add("dashboard");

        summaryLabel = new Label("Select a type and click Load to view recommendations.");
        summaryLabel.getStyleClass().add("section-subtitle");

        table = buildTable();
        VBox.setVgrow(table, Priority.ALWAYS);

        root.getChildren().addAll(buildTitle(), summaryLabel, buildToolbar(), table);
    }

    public VBox getRoot() { return root; }

    private Label buildTitle() {
        Label lbl = new Label("Storage Recommendations");
        lbl.getStyleClass().add("section-title");
        return lbl;
    }

    private HBox buildToolbar() {
        typeFilter = new ComboBox<>(FXCollections.observableArrayList(
            "ALL", "DUPLICATE", "TEMP_FILE", "OLD_SCREENSHOT",
            "UNUSED_LARGE_FILE", "STALE_DOWNLOAD"
        ));
        typeFilter.setValue("ALL");

        TextField sessionField = new TextField();
        sessionField.setPromptText("Session ID");
        sessionField.setPrefWidth(100);

        Button loadBtn = new Button("💡  Load");
        loadBtn.getStyleClass().add("btn-primary");
        loadBtn.setOnAction(e -> {
            String id = sessionField.getText().trim();
            if (id.isEmpty()) return;
            try { loadRecommendations(Long.parseLong(id)); }
            catch (NumberFormatException ex) { summaryLabel.setText("Invalid session ID."); }
        });

        Button generateBtn = new Button("⚙  Generate");
        generateBtn.getStyleClass().add("btn-secondary");
        generateBtn.setOnAction(e -> {
            String id = sessionField.getText().trim();
            if (id.isEmpty()) return;
            try {
                api.generateRecommendations(Long.parseLong(id));
                summaryLabel.setText("Generation triggered — click Load to refresh.");
            } catch (Exception ex) { summaryLabel.setText("Error: " + ex.getMessage()); }
        });

        return new HBox(10,
            new Label("Session:"), sessionField,
            new Label("Type:"), typeFilter,
            generateBtn, loadBtn);
    }

    @SuppressWarnings("unchecked")
    private TableView<RecommendationRow> buildTable() {
        TableView<RecommendationRow> tv = new TableView<>();
        tv.setPlaceholder(new Label("No recommendations loaded."));
        tv.getStyleClass().add("data-table");

        TableColumn<RecommendationRow, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        typeCol.setPrefWidth(160);

        TableColumn<RecommendationRow, String> fileCol = new TableColumn<>("File");
        fileCol.setCellValueFactory(new PropertyValueFactory<>("fileName"));
        fileCol.setPrefWidth(220);

        TableColumn<RecommendationRow, String> confCol = new TableColumn<>("Confidence");
        confCol.setCellValueFactory(new PropertyValueFactory<>("confidence"));
        confCol.setPrefWidth(90);

        TableColumn<RecommendationRow, String> sizeCol = new TableColumn<>("Recoverable");
        sizeCol.setCellValueFactory(new PropertyValueFactory<>("recoverableFormatted"));
        sizeCol.setPrefWidth(110);

        TableColumn<RecommendationRow, String> explCol = new TableColumn<>("Explanation");
        explCol.setCellValueFactory(new PropertyValueFactory<>("explanation"));
        HBox.setHgrow(explCol, Priority.ALWAYS);
        explCol.setPrefWidth(340);

        tv.getColumns().addAll(typeCol, fileCol, confCol, sizeCol, explCol);
        return tv;
    }

    private void loadRecommendations(Long sessionId) {
        summaryLabel.setText("Loading…");
        String type = "ALL".equals(typeFilter.getValue()) ? null : typeFilter.getValue();
        Thread.ofVirtual().start(() -> {
            try {
                var rows = api.getRecommendations(sessionId, type);
                Platform.runLater(() -> {
                    table.setItems(FXCollections.observableArrayList(rows));
                    summaryLabel.setText(rows.size() + " recommendation(s) loaded.");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> summaryLabel.setText("Error: " + ex.getMessage()));
            }
        });
    }
}
