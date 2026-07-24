package com.thepiratebrowser.ui;

import com.thepiratebrowser.service.LocalSettingsService;
import com.thepiratebrowser.service.PutIoService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Component
@Lazy
public class PutIoSetupWizard {
    static final String PUT_IO_APPS_URL = "https://app.put.io/oauth";
    static final String APPLICATION_WEBSITE =
            "https://github.com/Redmancometh/ThePirateBrowser";
    static final String CALLBACK_URL = "http://127.0.0.1:8765/callback";

    private final LocalSettingsService settingsService;
    private final PutIoService putIoService;
    private final ExecutorService executor;

    public PutIoSetupWizard(
            LocalSettingsService settingsService,
            PutIoService putIoService,
            ExecutorService executor
    ) {
        this.settingsService = settingsService;
        this.putIoService = putIoService;
        this.executor = executor;
    }

    public Optional<String> show(Window owner) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Connect put.io");
        dialog.setHeaderText("put.io setup — step 1 of 3");
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setResizable(true);

        ButtonType backType = new ButtonType("Back", ButtonBar.ButtonData.BACK_PREVIOUS);
        ButtonType nextType = new ButtonType("Next", ButtonBar.ButtonData.NEXT_FORWARD);
        dialog.getDialogPane().getButtonTypes().addAll(backType, nextType, ButtonType.CANCEL);

        PasswordField tokenField = new PasswordField();
        tokenField.setPromptText("Paste the OAuth token from put.io");
        tokenField.setPrefColumnCount(42);
        Label message = new Label();
        message.setWrapText(true);
        message.getStyleClass().add("hint");

        VBox content = new VBox(12);
        content.setPadding(new Insets(10));
        content.setPrefWidth(620);
        dialog.getDialogPane().setContent(content);

        Button back = (Button) dialog.getDialogPane().lookupButton(backType);
        Button next = (Button) dialog.getDialogPane().lookupButton(nextType);
        int[] page = {0};

        Runnable render = () -> {
            back.setDisable(page[0] == 0);
            next.setText(page[0] == 2 ? "Test & save" : "Next");
            dialog.setHeaderText("put.io setup — step " + (page[0] + 1) + " of 3");
            message.setText("");
            content.getChildren().setAll(switch (page[0]) {
                case 0 -> registrationPage(message);
                case 1 -> tokenPage(tokenField, message);
                default -> confirmationPage(tokenField, message);
            });
        };

        back.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            page[0] = Math.max(0, page[0] - 1);
            render.run();
        });
        next.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            if (page[0] == 0) {
                page[0] = 1;
                render.run();
                Platform.runLater(tokenField::requestFocus);
                return;
            }
            if (page[0] == 1) {
                if (tokenField.getText().trim().isBlank()) {
                    message.setText("Paste the OAuth token before continuing.");
                    return;
                }
                page[0] = 2;
                render.run();
                return;
            }

            String candidate = tokenField.getText().trim();
            back.setDisable(true);
            next.setDisable(true);
            next.setText("Testing…");
            message.setText("Checking the token with put.io…");
            CompletableFuture.supplyAsync(() -> putIoService.validateAccount(candidate), executor)
                    .whenComplete((username, error) -> Platform.runLater(() -> {
                        if (error != null) {
                            Throwable cause = error.getCause() == null ? error : error.getCause();
                            message.setText("Connection failed: " + safeMessage(cause));
                            back.setDisable(false);
                            next.setDisable(false);
                            next.setText("Test & save");
                            return;
                        }
                        settingsService.get().setPutIoToken(candidate);
                        try {
                            settingsService.save();
                        } catch (RuntimeException exception) {
                            message.setText("Token worked, but could not be saved: "
                                    + safeMessage(exception));
                            back.setDisable(false);
                            next.setDisable(false);
                            next.setText("Test & save");
                            return;
                        }
                        dialog.setResult(username);
                        dialog.close();
                    }));
        });

        render.run();
        return dialog.showAndWait();
    }

    private Node[] registrationPage(Label message) {
        Label instructions = wrapped("""
                Sign in to put.io, open API, and choose Create App. Give it a unique name such as \
                “The Pirate Browser - your name”. Use the values below if the form asks for a \
                website and callback URL. This client uses the token generated on the Secrets \
                page, so the callback address is not contacted.""");
        Button open = new Button("Open put.io API apps");
        open.setOnAction(event -> openBrowser(message));
        return new Node[]{
                instructions,
                open,
                valueRow("Application website", APPLICATION_WEBSITE),
                valueRow("Callback URL", CALLBACK_URL),
                message
        };
    }

    private Node[] tokenPage(PasswordField tokenField, Label message) {
        Label instructions = wrapped("""
                Save the new app, then click its key icon to open the Secrets page. Copy the OAuth \
                token—not the client secret—and paste it below.""");
        return new Node[]{instructions, new Label("OAuth token"), tokenField, message};
    }

    private Node[] confirmationPage(PasswordField tokenField, Label message) {
        String ending = tokenField.getText().length() <= 4
                ? "••••"
                : "••••" + tokenField.getText().substring(tokenField.getText().length() - 4);
        return new Node[]{
                wrapped("The browser will test this token against your put.io account before "
                        + "saving it locally."),
                new Label("Token ending in " + ending),
                wrapped("Click Test & save. If put.io rejects it, go Back and paste the OAuth "
                        + "token again."),
                message
        };
    }

    private HBox valueRow(String labelText, String value) {
        Label label = new Label(labelText);
        label.setMinWidth(125);
        TextField field = new TextField(value);
        field.setEditable(false);
        HBox.setHgrow(field, Priority.ALWAYS);
        Button copy = new Button("Copy");
        copy.setOnAction(event -> {
            ClipboardContent clipboard = new ClipboardContent();
            clipboard.putString(value);
            Clipboard.getSystemClipboard().setContent(clipboard);
        });
        HBox row = new HBox(8, label, field, copy);
        return row;
    }

    private void openBrowser(Label message) {
        try {
            if (!Desktop.isDesktopSupported()) {
                throw new IllegalStateException("Desktop browser integration is unavailable.");
            }
            Desktop.getDesktop().browse(URI.create(PUT_IO_APPS_URL));
            message.setText("Opened put.io in your default browser.");
        } catch (Exception exception) {
            message.setText("Could not open the browser. Visit " + PUT_IO_APPS_URL);
        }
    }

    private static Label wrapped(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        return label;
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? "Unknown error" : message;
    }
}
