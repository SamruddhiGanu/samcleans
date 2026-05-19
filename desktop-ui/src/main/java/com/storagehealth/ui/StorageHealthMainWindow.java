package com.storagehealth.ui;

import com.storagehealth.ui.dashboard.ScanDashboard;
import com.storagehealth.ui.explorer.DuplicateExplorer;
import com.storagehealth.ui.panels.RecommendationsPanel;
import com.storagehealth.ui.panels.SettingsPanel;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * Root window composed of a {@link MenuBar}, a {@link TabPane} with four tabs,
 * and a status bar at the bottom.
 *
 * <p>Each tab panel is a self-contained component that manages its own lifecycle.
 */
public class StorageHealthMainWindow {

    private final BorderPane root;

    public StorageHealthMainWindow() {
        root = new BorderPane();
        root.getStyleClass().add("main-window");

        root.setTop(buildMenuBar());
        root.setCenter(buildTabPane());
        root.setBottom(buildStatusBar());
    }

    public BorderPane getRoot() {
        return root;
    }

    // ---------------------------------------------------------------
    // Menu bar
    // ---------------------------------------------------------------

    private MenuBar buildMenuBar() {
        MenuBar bar = new MenuBar();
        bar.getStyleClass().add("menu-bar");

        // File menu
        Menu fileMenu = new Menu("_File");
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setOnAction(e -> System.exit(0));
        fileMenu.getItems().add(exitItem);

        // Scan menu
        Menu scanMenu = new Menu("_Scan");
        MenuItem newScanItem = new MenuItem("New Scan…");
        newScanItem.setOnAction(e -> {
            // Delegate to dashboard tab
        });
        scanMenu.getItems().add(newScanItem);

        // Help menu
        Menu helpMenu = new Menu("_Help");
        MenuItem aboutItem = new MenuItem("About");
        aboutItem.setOnAction(e -> showAbout());
        helpMenu.getItems().add(aboutItem);

        bar.getMenus().addAll(fileMenu, scanMenu, helpMenu);
        return bar;
    }

    // ---------------------------------------------------------------
    // Tab pane
    // ---------------------------------------------------------------

    private TabPane buildTabPane() {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getStyleClass().add("main-tabs");

        Tab dashTab  = new Tab("📊  Dashboard",       new ScanDashboard().getRoot());
        Tab dupTab   = new Tab("🔁  Duplicates",       new DuplicateExplorer().getRoot());
        Tab recTab   = new Tab("💡  Recommendations",  new RecommendationsPanel().getRoot());
        Tab setTab   = new Tab("⚙️  Settings",          new SettingsPanel().getRoot());

        tabs.getTabs().addAll(dashTab, dupTab, recTab, setTab);
        return tabs;
    }

    // ---------------------------------------------------------------
    // Status bar
    // ---------------------------------------------------------------

    private HBox buildStatusBar() {
        HBox bar = new HBox(12);
        bar.setPadding(new Insets(6, 12, 6, 12));
        bar.getStyleClass().add("status-bar");

        Label status = new Label("Ready  •  Storage Health Ranker v1.0");
        status.getStyleClass().add("status-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label api = new Label("API: localhost:8080");
        api.getStyleClass().add("status-label-dim");

        bar.getChildren().addAll(status, spacer, api);
        return bar;
    }

    private void showAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About");
        alert.setHeaderText("Storage Health Ranker v1.0");
        alert.setContentText("A fully local AI-powered storage analysis tool.\n"
            + "Built with Java 21 · Spring Boot 3.2 · JavaFX 21 · SQLite");
        alert.showAndWait();
    }
}
