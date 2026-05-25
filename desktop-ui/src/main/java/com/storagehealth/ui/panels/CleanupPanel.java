package com.storagehealth.ui.panels;

import com.storagehealth.ui.service.ApiClientService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Cleanup tab — manage safe deletion and restoration of files.
 */
public class CleanupPanel {

    private final VBox root;
    private final ApiClientService api = new ApiClientService();

    private TableView<CleanupSessionRow> table;
    private Label statusLabel;
    private TextArea fileIdsArea;

    public CleanupPanel() {
        root = new VBox(14);
        root.setPadding(new Insets(24));
        root.getStyleClass().add("dashboard");

        statusLabel = new Label("Ready");
        statusLabel.getStyleClass().add("status-text");

        table = buildTable();
        VBox.setVgrow(table, Priority.ALWAYS);

        root.getChildren().addAll(
            buildTitle(),
            buildInitiateSection(),
            new Separator(),
            buildToolbar(),
            table,
            statusLabel
        );
    }

    public VBox getRoot() { return root; }

    private Label buildTitle() {
        Label lbl = new Label("Safe Cleanup & Restore");
        lbl.getStyleClass().add("section-title");
        return lbl;
    }

    private VBox buildInitiateSection() {
        VBox section = new VBox(8);
        
        Label lbl = new Label("Enter File IDs to clean up (comma separated):");
        lbl.getStyleClass().add("field-label");

        fileIdsArea = new TextArea();
        fileIdsArea.setPromptText("e.g. 1, 4, 12, 18");
        fileIdsArea.setPrefRowCount(2);
        
        Button initBtn = new Button("🧹 Initiate Cleanup");
        initBtn.getStyleClass().add("btn-primary");
        initBtn.setOnAction(e -> handleInitiate());

        section.getChildren().addAll(lbl, fileIdsArea, initBtn);
        return section;
    }

    private HBox buildToolbar() {
        Button refreshBtn = new Button("↻ Refresh Sessions");
        refreshBtn.getStyleClass().add("btn-secondary");
        refreshBtn.setOnAction(e -> loadSessions());

        HBox bar = new HBox(10, new Label("Recent Sessions:"), refreshBtn);
        bar.setPadding(new Insets(10, 0, 0, 0));
        return bar;
    }

    @SuppressWarnings("unchecked")
    private TableView<CleanupSessionRow> buildTable() {
        TableView<CleanupSessionRow> tv = new TableView<>();
        tv.setPlaceholder(new Label("No cleanup sessions found."));
        tv.getStyleClass().add("data-table");

        TableColumn<CleanupSessionRow, String> idCol = new TableColumn<>("Session ID");
        idCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().sessionId()));
        idCol.setPrefWidth(260);

        TableColumn<CleanupSessionRow, String> countCol = new TableColumn<>("Files");
        countCol.setCellValueFactory(cell -> new SimpleStringProperty(String.valueOf(cell.getValue().filesCount())));
        countCol.setPrefWidth(80);

        TableColumn<CleanupSessionRow, String> sizeCol = new TableColumn<>("Total Size");
        sizeCol.setCellValueFactory(cell -> new SimpleStringProperty(formatSize(cell.getValue().totalSize())));
        sizeCol.setPrefWidth(100);

        TableColumn<CleanupSessionRow, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().status()));
        statusCol.setPrefWidth(120);

        TableColumn<CleanupSessionRow, Void> actionCol = new TableColumn<>("Actions");
        actionCol.setPrefWidth(180);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button execBtn = new Button("Execute");
            private final Button undoBtn = new Button("Undo");
            private final HBox box = new HBox(6, execBtn, undoBtn);

            {
                execBtn.getStyleClass().add("btn-danger");
                undoBtn.getStyleClass().add("btn-secondary-sm");
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    CleanupSessionRow row = getTableView().getItems().get(getIndex());
                    execBtn.setDisable(!"ACTIVE".equals(row.status()));
                    undoBtn.setDisable(!"COMPLETED".equals(row.status()));

                    execBtn.setOnAction(e -> handleExecute(row.sessionId()));
                    undoBtn.setOnAction(e -> handleUndo(row.sessionId()));
                    setGraphic(box);
                }
            }
        });

        tv.getColumns().addAll(idCol, countCol, sizeCol, statusCol, actionCol);
        return tv;
    }

    private void handleInitiate() {
        String input = fileIdsArea.getText();
        if (input == null || input.isBlank()) return;
        
        try {
            List<Long> ids = Arrays.stream(input.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toList());

            if (ids.isEmpty()) return;

            statusLabel.setText("Initiating...");
            Thread.ofVirtual().start(() -> {
                try {
                    api.initiateCleanup(ids);
                    Platform.runLater(() -> {
                        statusLabel.setText("Cleanup initiated.");
                        fileIdsArea.clear();
                        loadSessions();
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> statusLabel.setText("Error: " + ex.getMessage()));
                }
            });
        } catch (NumberFormatException e) {
            statusLabel.setText("Invalid IDs format. Use numbers separated by commas.");
        }
    }

    private void handleExecute(String sessionId) {
        statusLabel.setText("Executing cleanup (moving files)...");
        Thread.ofVirtual().start(() -> {
            try {
                api.executeCleanup(sessionId);
                Platform.runLater(() -> {
                    statusLabel.setText("Execution successful.");
                    loadSessions();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> statusLabel.setText("Error: " + ex.getMessage()));
            }
        });
    }

    private void handleUndo(String sessionId) {
        statusLabel.setText("Restoring files...");
        Thread.ofVirtual().start(() -> {
            try {
                api.undoCleanup(sessionId);
                Platform.runLater(() -> {
                    statusLabel.setText("Restore successful.");
                    loadSessions();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> statusLabel.setText("Error: " + ex.getMessage()));
            }
        });
    }

    public void loadSessions() {
        statusLabel.setText("Loading sessions...");
        Thread.ofVirtual().start(() -> {
            try {
                var list = api.listCleanupSessions();
                Platform.runLater(() -> {
                    table.setItems(FXCollections.observableArrayList(list));
                    statusLabel.setText("Loaded " + list.size() + " sessions.");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> statusLabel.setText("Error: " + ex.getMessage()));
            }
        });
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
