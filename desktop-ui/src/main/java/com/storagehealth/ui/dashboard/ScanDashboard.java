package com.storagehealth.ui.dashboard;

import com.storagehealth.ui.service.ApiClientService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;

import java.io.File;

/**
 * Dashboard tab — the main landing view.
 *
 * <p>Sections:
 * <ol>
 *   <li>Control panel: scan path entry, Start / Cancel buttons, progress bar</li>
 *   <li>Health score cards: Overall, Duplicate Waste, Clutter, Organisation</li>
 *   <li>Storage breakdown bar chart by file type</li>
 * </ol>
 */
public class ScanDashboard {
    private final VBox root;
    private final ApiClientService api = new ApiClientService();

    // Controls updated during a scan
    private Label    statusLabel;
    private ProgressBar progressBar;
    private TextField pathField;
    private Button   startBtn;
    private Button   cancelBtn;

    // Score card labels — updated after scan completes
    private Label overallValueLabel;
    private Label dupValueLabel;
    private Label clutterValueLabel;
    private Label orgValueLabel;
    private Long  currentSessionId;

    public ScanDashboard() {
        root = new VBox(18);
        root.setPadding(new Insets(24));
        root.getStyleClass().add("dashboard");

        root.getChildren().addAll(
            buildTitle(),
            buildControlPanel(),
            buildScoreCards(),
            buildChart()
        );
    }

    public VBox getRoot() { return root; }

    // ---------------------------------------------------------------
    // Builder methods
    // ---------------------------------------------------------------

    private Label buildTitle() {
        Label lbl = new Label("Storage Health Dashboard");
        lbl.getStyleClass().add("section-title");
        return lbl;
    }

    private VBox buildControlPanel() {
        VBox panel = new VBox(10);
        panel.getStyleClass().add("card");
        panel.setPadding(new Insets(16));

        // Path row
        Label pathLbl = new Label("Directory to scan:");
        pathLbl.getStyleClass().add("field-label");

        pathField = new TextField(System.getProperty("user.home"));
        pathField.setPromptText("e.g. C:\\Users\\YourName\\Documents");
        HBox.setHgrow(pathField, Priority.ALWAYS);

        Button browseBtn = new Button("Browse…");
        browseBtn.getStyleClass().add("btn-secondary");
        browseBtn.setOnAction(e -> handleBrowse());

        HBox pathRow = new HBox(8, pathField, browseBtn);
        pathRow.setAlignment(Pos.CENTER_LEFT);

        // Button row
        startBtn  = new Button("▶  Start Scan");
        cancelBtn = new Button("⏹  Cancel");
        startBtn.getStyleClass().add("btn-primary");
        cancelBtn.getStyleClass().add("btn-danger");
        cancelBtn.setDisable(true);

        startBtn.setOnAction(e  -> handleStartScan());
        cancelBtn.setOnAction(e -> handleCancelScan());

        statusLabel = new Label("Ready");
        statusLabel.getStyleClass().add("status-text");
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        progressBar = new ProgressBar(0);
        progressBar.setVisible(false);
        progressBar.setPrefWidth(200);

        HBox btnRow = new HBox(10, startBtn, cancelBtn, statusLabel, progressBar);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        panel.getChildren().addAll(pathLbl, pathRow, btnRow);
        return panel;
    }

    private HBox buildScoreCards() {
        overallValueLabel  = scoreValueLabel("—",   "#4ade80");
        dupValueLabel      = scoreValueLabel("—",   "#fb923c");
        clutterValueLabel  = scoreValueLabel("—",   "#60a5fa");
        orgValueLabel      = scoreValueLabel("—",   "#c084fc");

        VBox overallCard  = scoreCard("Overall Health",    overallValueLabel,  "#4ade80");
        VBox dupCard      = scoreCard("Duplicate Waste",   dupValueLabel,      "#fb923c");
        VBox clutterCard  = scoreCard("Clutter",           clutterValueLabel,  "#60a5fa");
        VBox orgCard      = scoreCard("Organisation",      orgValueLabel,      "#c084fc");

        HBox row = new HBox(16, overallCard, dupCard, clutterCard, orgCard);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private VBox scoreCard(String title, Label valueLabel, String accentColor) {
        VBox card = new VBox(8);
        card.getStyleClass().add("score-card");
        card.setStyle("-fx-border-color: " + accentColor + ";");
        card.setPrefWidth(220);
        card.setPadding(new Insets(16));

        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("card-title");

        card.getChildren().addAll(titleLbl, valueLabel);
        return card;
    }

    private Label scoreValueLabel(String initial, String color) {
        Label lbl = new Label(initial);
        lbl.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 36px; -fx-font-weight: bold;");
        return lbl;
    }

    private BarChart<String, Number> buildChart() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis   yAxis = new NumberAxis();
        xAxis.setLabel("File Type");
        yAxis.setLabel("Size (GB)");

        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle("Storage Breakdown by File Type");
        chart.getStyleClass().add("storage-chart");
        chart.setLegendVisible(false);
        chart.setAnimated(true);
        VBox.setVgrow(chart, Priority.ALWAYS);

        // Placeholder data — replaced by real data after a scan completes
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.getData().addAll(
            new XYChart.Data<>("Images",    0),
            new XYChart.Data<>("Videos",    0),
            new XYChart.Data<>("Documents", 0),
            new XYChart.Data<>("Archives",  0),
            new XYChart.Data<>("Temp",      0),
            new XYChart.Data<>("Other",     0)
        );
        chart.getData().add(series);
        return chart;
    }

    // ---------------------------------------------------------------
    // Event handlers
    // ---------------------------------------------------------------

    private void handleBrowse() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Directory to Scan");
        chooser.setInitialDirectory(new File(System.getProperty("user.home")));
        File dir = chooser.showDialog(root.getScene().getWindow());
        if (dir != null) pathField.setText(dir.getAbsolutePath());
    }

    private void handleStartScan() {
        String path = pathField.getText().trim();
        if (path.isEmpty()) {
            setStatus("⚠ Please select a directory first.", "status-warning");
            return;
        }

        setStatus("Starting scan…", "status-active");
        startBtn.setDisable(true);
        cancelBtn.setDisable(false);
        progressBar.setVisible(true);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);

        Thread.ofVirtual().start(() -> {
            try {
                currentSessionId = api.startScan(path, "Dashboard Scan");
                Platform.runLater(() -> setStatus("Scan running (session " + currentSessionId + ")", "status-active"));

                // Poll until complete
                boolean done = false;
                while (!done) {
                    Thread.sleep(2000);
                    String status = api.getScanStatus(currentSessionId);
                    done = "COMPLETED".equals(status) || "FAILED".equals(status) || "PAUSED".equals(status);
                }

                final Long finalSessionId = currentSessionId;
                Platform.runLater(() -> {
                    setStatus("✔ Scan complete.", "status-ok");
                    startBtn.setDisable(false);
                    cancelBtn.setDisable(true);
                    progressBar.setVisible(false);
                    refreshScores(finalSessionId);
                });

            } catch (Exception ex) {
                Platform.runLater(() -> {
                    setStatus("✘ " + ex.getMessage(), "status-error");
                    startBtn.setDisable(false);
                    cancelBtn.setDisable(true);
                    progressBar.setVisible(false);
                });
            }
        });
    }

    private void handleCancelScan() {
        if (currentSessionId != null) {
            api.cancelScan(currentSessionId);
        }
        setStatus("Cancellation requested…", "status-warning");
        cancelBtn.setDisable(true);
    }

    private void refreshScores(Long sessionId) {
        Thread.ofVirtual().start(() -> {
            try {
                double[] scores = api.getHealthScores(sessionId);
                Platform.runLater(() -> {
                    overallValueLabel.setText(String.format("%.0f", scores[0]));
                    dupValueLabel.setText(String.format("%.0f%%", 100 - scores[1]));
                    clutterValueLabel.setText(String.format("%.0f%%", 100 - scores[2]));
                    orgValueLabel.setText(String.format("%.0f", scores[3]));
                });
            } catch (Exception ex) {
                Platform.runLater(() -> setStatus("Could not load health scores.", "status-warning"));
            }
        });
    }

    private void setStatus(String msg, String cssClass) {
        statusLabel.setText(msg);
        statusLabel.getStyleClass().removeAll("status-text", "status-active", "status-ok",
            "status-error", "status-warning");
        statusLabel.getStyleClass().add(cssClass);
    }
}
