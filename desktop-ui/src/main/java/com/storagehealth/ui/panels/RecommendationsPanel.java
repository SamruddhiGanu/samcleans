package com.storagehealth.ui.panels;

import com.storagehealth.ui.service.ApiClientService;
import com.storagehealth.ui.SessionContext;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import java.util.List;
import java.util.stream.Collectors;

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
    private Long currentSessionId;
    private TextField sessionField;

    public RecommendationsPanel() {
        root = new VBox(14);
        root.setPadding(new Insets(24));
        root.getStyleClass().add("dashboard");

        summaryLabel = new Label("Select a type and click Load to view recommendations.");
        summaryLabel.getStyleClass().add("section-subtitle");

        table = buildTable();
        VBox.setVgrow(table, Priority.ALWAYS);

        root.getChildren().addAll(buildTitle(), summaryLabel, buildToolbar(), buildActionToolbar(), table);

        // Auto-populate and load when session changes from Dashboard
        SessionContext.get().sessionIdProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.longValue() > 0) {
                sessionField.setText(String.valueOf(newVal.longValue()));
                summaryLabel.setText("Session " + newVal + " ready — loading recommendations…");
                loadRecommendations(newVal.longValue());
            }
        });

        // If a session was already active when this panel was created
        long existing = SessionContext.get().getSessionId();
        if (existing > 0) {
            sessionField.setText(String.valueOf(existing));
        }
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

        sessionField = new TextField();
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

    private HBox buildActionToolbar() {
        Button analyzeBtn = new Button("📸  Analyze Images");
        analyzeBtn.getStyleClass().add("btn-secondary");
        analyzeBtn.setOnAction(e -> {
            if (currentSessionId == null) { statusMsg("Load a session first."); return; }
            statusMsg("Analyzing images (blur, color)...");
            Thread.ofVirtual().start(() -> {
                try {
                    api.analyzeImages(currentSessionId);
                    Platform.runLater(() -> statusMsg("Image analysis complete. Click Load to refresh."));
                } catch (Exception ex) { Platform.runLater(() -> statusMsg("Error: " + ex.getMessage())); }
            });
        });

        Button nearDupBtn = new Button("🖼️  Near Duplicates");
        nearDupBtn.getStyleClass().add("btn-secondary");
        nearDupBtn.setOnAction(e -> {
            if (currentSessionId == null) { statusMsg("Load a session first."); return; }
            statusMsg("Detecting near-duplicates...");
            Thread.ofVirtual().start(() -> {
                try {
                    api.detectNearDuplicates(currentSessionId);
                    Platform.runLater(() -> statusMsg("Near-duplicate detection complete. Click Load to refresh."));
                } catch (Exception ex) { Platform.runLater(() -> statusMsg("Error: " + ex.getMessage())); }
            });
        });

        Button cleanupBtn = new Button("🧹  Add Selected to Cleanup");
        cleanupBtn.getStyleClass().add("btn-primary");
        cleanupBtn.setOnAction(e -> handleAddToCleanup());

        HBox bar = new HBox(10, analyzeBtn, nearDupBtn, new Region(), cleanupBtn);
        HBox.setHgrow(bar.getChildren().get(2), Priority.ALWAYS);
        return bar;
    }

    private void statusMsg(String msg) {
        summaryLabel.setText(msg);
    }

    @SuppressWarnings("unchecked")
    private TableView<RecommendationRow> buildTable() {
        TableView<RecommendationRow> tv = new TableView<>();
        tv.setPlaceholder(new Label("No recommendations loaded."));
        tv.getStyleClass().add("data-table");

        TableColumn<RecommendationRow, Boolean> selCol = new TableColumn<>("");
        selCol.setCellValueFactory(new PropertyValueFactory<>("selected"));
        selCol.setCellFactory(tc -> new javafx.scene.control.cell.CheckBoxTableCell<>());
        selCol.setEditable(true);
        selCol.setPrefWidth(40);
        tv.setEditable(true);

        TableColumn<RecommendationRow, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        typeCol.setPrefWidth(140);

        TableColumn<RecommendationRow, String> fileCol = new TableColumn<>("File");
        fileCol.setCellValueFactory(new PropertyValueFactory<>("fileName"));
        fileCol.setPrefWidth(200);

        TableColumn<RecommendationRow, String> confCol = new TableColumn<>("Confidence");
        confCol.setCellValueFactory(new PropertyValueFactory<>("confidence"));
        confCol.setPrefWidth(90);

        TableColumn<RecommendationRow, String> sizeCol = new TableColumn<>("Recoverable");
        sizeCol.setCellValueFactory(new PropertyValueFactory<>("recoverableFormatted"));
        sizeCol.setPrefWidth(110);

        TableColumn<RecommendationRow, String> explCol = new TableColumn<>("Explanation");
        explCol.setCellValueFactory(new PropertyValueFactory<>("explanation"));
        explCol.setPrefWidth(320);

        tv.getColumns().addAll(selCol, typeCol, fileCol, confCol, sizeCol, explCol);
        return tv;
    }

    private void handleAddToCleanup() {
        List<Long> selectedFileIds = table.getItems().stream()
            .filter(RecommendationRow::isSelected)
            .map(RecommendationRow::getFileId)
            .collect(Collectors.toList());
        
        if (selectedFileIds.isEmpty()) {
            statusMsg("No recommendations selected.");
            return;
        }

        statusMsg("Initiating cleanup for " + selectedFileIds.size() + " files...");
        Thread.ofVirtual().start(() -> {
            try {
                api.initiateCleanup(selectedFileIds);
                Platform.runLater(() -> statusMsg("Added to cleanup. Switch to Cleanup tab."));
            } catch (Exception ex) { Platform.runLater(() -> statusMsg("Error: " + ex.getMessage())); }
        });
    }

    private void loadRecommendations(Long sessionId) {
        this.currentSessionId = sessionId;
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
