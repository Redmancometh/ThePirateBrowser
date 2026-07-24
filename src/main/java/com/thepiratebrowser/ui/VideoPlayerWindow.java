package com.thepiratebrowser.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.springframework.stereotype.Component;

@Component
public class VideoPlayerWindow {
    public void open(String title, String streamUrl) {
        Media media = new Media(streamUrl);
        MediaPlayer player = new MediaPlayer(media);
        MediaView mediaView = new MediaView(player);
        mediaView.setPreserveRatio(true);

        StackPane videoSurface = new StackPane(mediaView);
        videoSurface.getStyleClass().add("video-surface");
        mediaView.fitWidthProperty().bind(videoSurface.widthProperty());
        mediaView.fitHeightProperty().bind(videoSurface.heightProperty());

        Button playPause = new Button("Pause");
        Button stop = new Button("Stop");
        Button fullscreen = new Button("Fullscreen");
        Slider progress = new Slider();
        progress.setMin(0);
        progress.setMax(1);
        Slider volume = new Slider(0, 1, 0.8);
        volume.setPrefWidth(90);
        Label time = new Label("00:00 / 00:00");

        HBox.setHgrow(progress, Priority.ALWAYS);
        HBox controls = new HBox(8, playPause, stop, progress, time,
                new Label("Volume"), volume, fullscreen);
        controls.setAlignment(Pos.CENTER);
        controls.setPadding(new Insets(10));
        controls.getStyleClass().add("video-controls");

        BorderPane root = new BorderPane(videoSurface);
        root.setBottom(controls);
        Scene scene = new Scene(root, 1000, 650);
        scene.getStylesheets().add(getClass().getResource("/styles/application.css").toExternalForm());
        Stage stage = new Stage();
        stage.setTitle(title + " · Pirate Browser");
        stage.setMinWidth(720);
        stage.setMinHeight(480);
        stage.setScene(scene);

        playPause.setOnAction(event -> {
            if (player.getStatus() == MediaPlayer.Status.PLAYING) {
                player.pause();
            } else {
                player.play();
            }
        });
        stop.setOnAction(event -> player.stop());
        fullscreen.setOnAction(event -> stage.setFullScreen(!stage.isFullScreen()));
        volume.valueProperty().addListener((ignored, previous, current) ->
                player.setVolume(current.doubleValue()));
        player.setVolume(volume.getValue());

        player.statusProperty().addListener((ignored, previous, current) ->
                playPause.setText(current == MediaPlayer.Status.PLAYING ? "Pause" : "Play"));
        player.currentTimeProperty().addListener((ignored, previous, current) -> {
            Duration total = player.getTotalDuration();
            if (!progress.isValueChanging() && isUsable(total)) {
                progress.setValue(current.toMillis() / total.toMillis());
            }
            time.setText(format(current) + " / " + format(total));
        });
        progress.setOnMouseReleased(event -> {
            Duration total = player.getTotalDuration();
            if (isUsable(total)) {
                player.seek(total.multiply(progress.getValue()));
            }
        });
        player.setOnError(() -> showPlaybackError(stage, player.getError()));
        media.setOnError(() -> showPlaybackError(stage, media.getError()));
        stage.setOnCloseRequest(event -> player.dispose());

        stage.show();
        player.play();
    }

    private static boolean isUsable(Duration duration) {
        return duration != null && !duration.isUnknown() && !duration.isIndefinite()
                && duration.greaterThan(Duration.ZERO);
    }

    private static String format(Duration duration) {
        if (!isUsable(duration)) {
            return "00:00";
        }
        long seconds = Math.max(0, Math.round(duration.toSeconds()));
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainder = seconds % 60;
        return hours > 0
                ? "%d:%02d:%02d".formatted(hours, minutes, remainder)
                : "%02d:%02d".formatted(minutes, remainder);
    }

    private static void showPlaybackError(Stage owner, Throwable error) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(owner);
        alert.setTitle("Playback failed");
        alert.setHeaderText("Could not play this put.io video");
        alert.setContentText(error == null || error.getMessage() == null
                ? "The video format could not be played."
                : error.getMessage());
        alert.show();
    }
}
