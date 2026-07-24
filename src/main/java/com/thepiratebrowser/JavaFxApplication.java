package com.thepiratebrowser;

import com.thepiratebrowser.ui.MainView;
import com.thepiratebrowser.service.LocalSettingsService;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class JavaFxApplication extends Application {
    @Override
    public void start(Stage stage) {
        MainView mainView = PirateBrowserApplication.context().getBean(MainView.class);
        Scene scene = new Scene(mainView.root(), 1280, 760);
        scene.getStylesheets().add(getClass().getResource("/styles/application.css").toExternalForm());

        stage.setTitle("Pirate Browser");
        stage.setMinWidth(980);
        stage.setMinHeight(620);
        stage.setScene(scene);
        stage.getIcons().add(new Image(
                getClass().getResourceAsStream("/images/pirate-penguin.png")));
        stage.show();
        mainView.onShown();
        writeSmokeMarkerIfRequested();
    }

    @Override
    public void stop() {
        PirateBrowserApplication.closeContext();
    }

    private void writeSmokeMarkerIfRequested() {
        String markerPath = System.getenv("PIRATE_BROWSER_SMOKE_MARKER");
        if (markerPath == null || markerPath.isBlank()) {
            return;
        }
        LocalSettingsService settingsService =
                PirateBrowserApplication.context().getBean(LocalSettingsService.class);
        var settings = settingsService.get();
        String marker = String.join(System.lineSeparator(),
                "READY",
                "settingsFile=" + settingsService.settingsFile(),
                "tokenConfigured=" + !settings.getPutIoToken().isBlank(),
                "clientSecretConfigured=" + !settings.getPutIoClientSecret().isBlank());
        try {
            Files.writeString(Path.of(markerPath), marker);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write packaging smoke marker", exception);
        }
    }
}
