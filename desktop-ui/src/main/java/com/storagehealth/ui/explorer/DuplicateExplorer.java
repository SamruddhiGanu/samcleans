package com.storagehealth.ui.explorer;

import com.storagehealth.ui.service.ApiClientService;
import com.storagehealth.ui.SessionContext;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;

/**
 * Duplicates tab — displays a summary and a sortable table of duplicate file groups.
 * Each row shows the hash prefix, file count, total size, and recoverable space.
 * A "View" button (placeholder) will open a detail dialog in Phase 4.
 */
public class DuplicateExplorer {

    private final VBox root;
    private final ApiClientService api = new ApiClientService();

    private TableView<DuplicateGroupRow> table;
    private Label summaryLabel;
    private TextField sessionField;

    public DuplicateExplorer() {
        root = new VBox(14);
        root.setPadding(new Insets(24));
        root.getStyleClass().add("dashboard");

        summaryLabel = new Label("Run a scan then click 'Detect Duplicates' to populate this view.");
        summaryLabel.getStyleClass().add("section-subtitle");

        HBox toolbar = buildToolbar();
        table = buildTable();
        VBox.setVgrow(table, Priority.ALWAYS);

        root.getChildren().addAll(
            buildTitle(),
            summaryLabel,
            toolbar,
            table
        );

        // When dashboard completes a scan, pre-fill the session field automatically
        SessionContext.get().sessionIdProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.longValue() > 0) {
                sessionField.setText(String.valueOf(newVal.longValue()));
                summaryLabel.setText("Session " + newVal + " ready — click Detect Duplicates.");
            }
        });
        
        long currentSession = SessionContext.get().getSessionId();
        if (currentSession > 0) {
            sessionField.setText(String.valueOf(currentSession));
        }
    }

    public VBox getRoot() { return root; }

    // ---------------------------------------------------------------
    // Builder helpers
    // ---------------------------------------------------------------

    private Label buildTitle() {
        Label lbl = new Label("Duplicate File Explorer");
        lbl.getStyleClass().add("section-title");
        return lbl;
    }

    private HBox buildToolbar() {
        Button detectBtn = new Button("🔍  Detect Duplicates");
        detectBtn.getStyleClass().add("btn-primary");

        sessionField = new TextField();
        sessionField.setPromptText("Session ID (auto-filled after scan)");
        sessionField.setPrefWidth(130);

        detectBtn.setOnAction(e -> {
            String idStr = sessionField.getText().trim();
            if (idStr.isEmpty()) return;
            try {
                Long sessionId = Long.parseLong(idStr);
                runDetection(sessionId);
            } catch (NumberFormatException ex) {
                summaryLabel.setText("Invalid session ID.");
            }
        });

        HBox bar = new HBox(10, new Label("Session ID:"), sessionField, detectBtn);
        bar.setPadding(new Insets(0, 0, 4, 0));
        return bar;
    }

    @SuppressWarnings("unchecked")
    private TableView<DuplicateGroupRow> buildTable() {
        TableView<DuplicateGroupRow> tv = new TableView<>();
        tv.setPlaceholder(new Label("No duplicates detected yet."));
        tv.getStyleClass().add("data-table");

        TableColumn<DuplicateGroupRow, String> hashCol = new TableColumn<>("Hash (prefix)");
        hashCol.setCellValueFactory(new PropertyValueFactory<>("hashPrefix"));
        hashCol.setPrefWidth(160);

        TableColumn<DuplicateGroupRow, Integer> countCol = new TableColumn<>("Copies");
        countCol.setCellValueFactory(new PropertyValueFactory<>("fileCount"));
        countCol.setPrefWidth(80);

        TableColumn<DuplicateGroupRow, String> sizeCol = new TableColumn<>("Total Size");
        sizeCol.setCellValueFactory(new PropertyValueFactory<>("formattedTotalSize"));
        sizeCol.setPrefWidth(120);

        TableColumn<DuplicateGroupRow, String> recCol = new TableColumn<>("Recoverable");
        recCol.setCellValueFactory(new PropertyValueFactory<>("formattedRecoverable"));
        recCol.setPrefWidth(120);

        TableColumn<DuplicateGroupRow, Void> actionCol = new TableColumn<>("Action");
        actionCol.setPrefWidth(80);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("View");
            { btn.getStyleClass().add("btn-secondary-sm"); }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
                if (!empty) {
                    btn.setOnAction(e -> {
                        DuplicateGroupRow row = getTableView().getItems().get(getIndex());
                        showDetailDialog(row);
                    });
                }
            }
        });

        tv.getColumns().addAll(hashCol, countCol, sizeCol, recCol, actionCol);
        return tv;
    }

    // ---------------------------------------------------------------
    // Event / async handlers
    // ---------------------------------------------------------------

    private void runDetection(Long sessionId) {
        summaryLabel.setText("Detecting duplicates…");
        Thread.ofVirtual().start(() -> {
            try {
                var result = api.detectDuplicates(sessionId);
                Platform.runLater(() -> {
                    table.setItems(FXCollections.observableArrayList(result));
                    long totalRecoverable = result.stream()
                        .mapToLong(DuplicateGroupRow::getRecoverableSpace).sum();
                    summaryLabel.setText(String.format(
                        "Found %d duplicate group(s) — %s recoverable space",
                        result.size(), formatSize(totalRecoverable)));
                });
            } catch (Exception ex) {
                Platform.runLater(() -> summaryLabel.setText("Error: " + ex.getMessage()));
            }
        });
    }

    private void showDetailDialog(DuplicateGroupRow row) {
        Alert dlg = new Alert(Alert.AlertType.INFORMATION);
        dlg.setTitle("Duplicate Group Detail");
        dlg.setHeaderText("Hash: " + row.getHashPrefix());
        dlg.setContentText(String.format(
            "Copies: %d\nTotal Size: %s\nRecoverable: %s",
            row.getFileCount(),
            row.getFormattedTotalSize(),
            row.getFormattedRecoverable()));
        dlg.showAndWait();
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
