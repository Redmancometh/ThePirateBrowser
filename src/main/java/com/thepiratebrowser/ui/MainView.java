package com.thepiratebrowser.ui;

import com.thepiratebrowser.model.LocalSettings;
import com.thepiratebrowser.model.MonitorUpdate;
import com.thepiratebrowser.model.PutIoFile;
import com.thepiratebrowser.model.PutIoFileListing;
import com.thepiratebrowser.model.PutIoTransfer;
import com.thepiratebrowser.model.SavedSearch;
import com.thepiratebrowser.model.TorrentResult;
import com.thepiratebrowser.service.ChromecastService;
import com.thepiratebrowser.service.LocalSettingsService;
import com.thepiratebrowser.service.PirateBayService;
import com.thepiratebrowser.service.PutIoService;
import com.thepiratebrowser.service.SearchMonitorService;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.net.URI;
import java.text.DecimalFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

@Component
@Lazy
public class MainView {
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter CHECK_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(ZoneId.systemDefault());
    private static final DecimalFormat SIZE_FORMAT = new DecimalFormat("0.##");

    private final LocalSettingsService settingsService;
    private final PirateBayService pirateBayService;
    private final PutIoService putIoService;
    private final ChromecastService chromecastService;
    private final SearchMonitorService monitorService;
    private final VideoPlayerWindow videoPlayer;
    private final ExecutorService executor;

    private final BorderPane root = new BorderPane();
    private final ObservableList<SavedSearch> savedSearches = FXCollections.observableArrayList();
    private final ObservableList<TorrentResult> results = FXCollections.observableArrayList();
    private final ObservableList<PutIoTransfer> transfers = FXCollections.observableArrayList();
    private final ObservableList<PutIoFile> putIoFiles = FXCollections.observableArrayList();
    private final Map<String, Long> lastAppliedMonitorSequences = new HashMap<>();

    private final ListView<SavedSearch> searchesList = new ListView<>(savedSearches);
    private final TableView<TorrentResult> resultsTable = new TableView<>(results);
    private final ListView<PutIoTransfer> transfersList = new ListView<>(transfers);
    private final ListView<PutIoFile> putIoFilesList = new ListView<>(putIoFiles);
    private final TextField queryField = new TextField();
    private final Spinner<Integer> minimumSeeders = new Spinner<>(0, 100_000, 0);
    private final Label statusLabel = new Label("Ready");
    private final Button searchButton = new Button("Search");
    private final Button sendButton = new Button("Send to put.io");
    private final Label selectedSearchStatus = new Label("Select a saved search.");
    private final Button toggleSearchButton = new Button("Pause");
    private final Button playPutIoButton = new Button("Play");
    private final Button castPutIoButton = new Button("Cast");
    private final Button upPutIoButton = new Button("Up");
    private final Label putIoFolderLabel = new Label("put.io files");
    private final Button showSearchesButton = new Button("Show saved searches");
    private final Button showTransfersButton = new Button("Show put.io");
    private final SplitPane contentSplit = new SplitPane();
    private final Timeline transferRefreshTimer = new Timeline();
    private final PauseTransition layoutSaveDelay = new PauseTransition(Duration.millis(400));
    private Node searchesPanel;
    private Node resultsPanel;
    private Node transfersPanel;
    private boolean applyingSavedSearchCriteria;
    private boolean restoringLayout;
    private boolean transferRefreshInProgress;
    private boolean filesRefreshInProgress;
    private boolean transferSubmissionActive;
    private String queuedTransferRefreshPrefix;
    private long currentPutIoDirectoryId;
    private long currentPutIoParentDirectoryId;
    private final RequestGeneration resultRequests = new RequestGeneration();

    public MainView(
            LocalSettingsService settingsService,
            PirateBayService pirateBayService,
            PutIoService putIoService,
            ChromecastService chromecastService,
            SearchMonitorService monitorService,
            VideoPlayerWindow videoPlayer,
            ExecutorService executor
    ) {
        this.settingsService = settingsService;
        this.pirateBayService = pirateBayService;
        this.putIoService = putIoService;
        this.chromecastService = chromecastService;
        this.monitorService = monitorService;
        this.videoPlayer = videoPlayer;
        this.executor = executor;
        layoutSaveDelay.setOnFinished(event -> settingsService.save());
        build();
    }

    public BorderPane root() {
        return root;
    }

    public void onShown() {
        refreshSavedSearches();
        minimumSeeders.getValueFactory().setValue(settingsService.get().getDefaultMinimumSeeders());
        if (!settingsService.get().getPutIoToken().isBlank()) {
            refreshTransfers();
            refreshPutIoFiles(0, false);
        }
    }

    private void build() {
        root.getStyleClass().add("app");
        root.setTop(buildHeader());
        root.setCenter(buildContent());
        root.setBottom(buildStatusBar());

        queryField.setPromptText("Search The Pirate Bay");
        queryField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                runSearch();
            }
        });
        searchButton.setDefaultButton(true);
        searchButton.setOnAction(event -> runSearch());
        sendButton.setDisable(true);
        sendButton.setOnAction(event -> sendSelected());

        searchesList.getSelectionModel().selectedItemProperty().addListener((ignored, previous, selected) -> {
            if (selected != null) {
                applyingSavedSearchCriteria = true;
                try {
                    queryField.setText(selected.getQuery());
                    minimumSeeders.getValueFactory().setValue(selected.getMinimumSeeders());
                } finally {
                    applyingSavedSearchCriteria = false;
                }
                refreshSelectedSearchStatus();
                runMonitoredCheck(selected);
            } else {
                selectedSearchStatus.setText("Select a saved search.");
                toggleSearchButton.setDisable(true);
            }
        });
        resultsTable.getSelectionModel().selectedItemProperty().addListener(
                (ignored, previous, selected) ->
                        sendButton.setDisable(selected == null || transferSubmissionActive));
        queryField.textProperty().addListener((ignored, previous, value) -> leaveSavedModeIfCriteriaDiverged());
        minimumSeeders.valueProperty().addListener(
                (ignored, previous, value) -> leaveSavedModeIfCriteriaDiverged());
    }

    private Node buildHeader() {
        Label title = new Label("THE PIRATE BROWSER");
        title.getStyleClass().add("title");
        Label subtitle = new Label("saved searches → put.io");
        subtitle.getStyleClass().add("subtitle");
        Button settings = new Button("Settings");
        settings.getStyleClass().add("secondary-button");
        settings.setOnAction(event -> showSettings());
        configureRestoreButton(showSearchesButton, () -> setSearchesPanelVisible(true));
        configureRestoreButton(showTransfersButton, () -> setTransfersPanelVisible(true));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, title, subtitle, spacer,
                showSearchesButton, showTransfersButton, settings);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("header");
        return header;
    }

    private Node buildContent() {
        searchesPanel = buildSearchesPanel();
        resultsPanel = buildResultsPanel();
        transfersPanel = buildTransfersPanel();
        rebuildContentSplit();
        return contentSplit;
    }

    private Node buildSearchesPanel() {
        Node label = panelHeader("SAVED SEARCHES", () -> setSearchesPanelVisible(false));
        searchesList.setPlaceholder(new Label("No saved searches yet."));
        searchesList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(SavedSearch item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText((item.isEnabled() ? "●  " : "○  ") + item);
                    setTooltip(new Tooltip(item.getQuery()));
                }
            }
        });

        Button add = new Button("+ Add");
        add.setMaxWidth(Double.MAX_VALUE);
        add.setOnAction(event -> showAddSearch());
        Button edit = new Button("Edit");
        edit.getStyleClass().add("secondary-button");
        edit.setMaxWidth(Double.MAX_VALUE);
        edit.setOnAction(event -> showEditSearch());
        toggleSearchButton.getStyleClass().add("secondary-button");
        toggleSearchButton.setMaxWidth(Double.MAX_VALUE);
        toggleSearchButton.setDisable(true);
        toggleSearchButton.setOnAction(event -> toggleSelectedSearch());
        Button remove = new Button("Remove");
        remove.getStyleClass().add("secondary-button");
        remove.setMaxWidth(Double.MAX_VALUE);
        remove.setOnAction(event -> removeSelectedSearch());
        Button checkNow = new Button("Check now");
        checkNow.setMaxWidth(Double.MAX_VALUE);
        checkNow.setOnAction(event -> {
            SavedSearch selected = searchesList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                runMonitoredCheck(selected);
            }
        });

        HBox primaryActions = new HBox(8, add, edit);
        HBox secondaryActions = new HBox(8, toggleSearchButton, remove);
        for (Node button : List.of(add, edit, toggleSearchButton, remove)) {
            HBox.setHgrow(button, Priority.ALWAYS);
        }
        selectedSearchStatus.getStyleClass().add("hint");
        selectedSearchStatus.setWrapText(true);

        VBox box = new VBox(10, label, searchesList, selectedSearchStatus,
                checkNow, primaryActions, secondaryActions);
        VBox.setVgrow(searchesList, Priority.ALWAYS);
        box.getStyleClass().add("panel");
        return box;
    }

    private Node buildResultsPanel() {
        Label label = sectionLabel("SEARCH RESULTS");
        HBox searchBar = new HBox(8,
                queryField,
                new Label("Min seeders"),
                minimumSeeders,
                searchButton);
        searchBar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(queryField, Priority.ALWAYS);

        configureResultsTable();

        Button open = new Button("Open Pirate Bay page");
        open.getStyleClass().add("secondary-button");
        open.setOnAction(event -> openSelectedPage());
        HBox actions = new HBox(8, sendButton, open);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox box = new VBox(10, label, searchBar, resultsTable, actions);
        VBox.setVgrow(resultsTable, Priority.ALWAYS);
        box.getStyleClass().addAll("panel", "results-panel");
        return box;
    }

    private void configureResultsTable() {
        TableColumn<TorrentResult, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(cell -> new SimpleStringProperty(
                (cell.getValue().newMatch() ? "NEW  " : "") + cell.getValue().name()));
        name.setPrefWidth(390);

        TableColumn<TorrentResult, Number> size = new TableColumn<>("Size");
        size.setCellValueFactory(cell -> new ReadOnlyLongWrapper(cell.getValue().size()));
        size.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : formatSize(value.longValue()));
            }
        });
        size.setPrefWidth(80);

        TableColumn<TorrentResult, Number> seeders = new TableColumn<>("SE");
        seeders.setCellValueFactory(cell -> new ReadOnlyIntegerWrapper(cell.getValue().seeders()));
        seeders.setPrefWidth(55);

        TableColumn<TorrentResult, Number> leechers = new TableColumn<>("LE");
        leechers.setCellValueFactory(cell -> new ReadOnlyIntegerWrapper(cell.getValue().leechers()));
        leechers.setPrefWidth(55);

        TableColumn<TorrentResult, String> added = new TableColumn<>("Added");
        added.setCellValueFactory(cell -> new SimpleStringProperty(DATE_FORMAT.format(cell.getValue().added())));
        added.setPrefWidth(100);

        resultsTable.getColumns().addAll(name, size, seeders, leechers, added);
        resultsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        resultsTable.setPlaceholder(new Label("Run a search or select a saved search."));
        resultsTable.setRowFactory(table -> {
            TableRow<TorrentResult> row = new TableRow<>();
            row.itemProperty().addListener((ignored, previous, item) ->
                    row.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("new-result"),
                            item != null && item.newMatch()));
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    sendSelected();
                }
            });
            return row;
        });
    }

    private Node buildTransfersPanel() {
        Node label = panelHeader("PUT.IO", () -> setTransfersPanelVisible(false));
        transfersList.setPlaceholder(new Label("No active transfers."));
        transfersList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(PutIoTransfer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                String progress = item.percentDone() > 0 ? " · " + item.percentDone() + "%" : "";
                String error = item.errorMessage().isBlank() ? "" : "\n" + item.errorMessage();
                setText(item.name() + "\n" + (item.isDone() ? "DONE" : item.status())
                        + progress + error);
            }
        });
        Button refresh = new Button("Refresh");
        refresh.setMaxWidth(Double.MAX_VALUE);
        refresh.setOnAction(event -> refreshTransfers());
        VBox transfersBox = new VBox(8, transfersList, refresh);
        VBox.setVgrow(transfersList, Priority.ALWAYS);

        configurePutIoFileBrowser();
        HBox fileActions = new HBox(6, upPutIoButton, playPutIoButton, castPutIoButton);
        HBox.setHgrow(playPutIoButton, Priority.ALWAYS);
        HBox.setHgrow(castPutIoButton, Priority.ALWAYS);
        playPutIoButton.setMaxWidth(Double.MAX_VALUE);
        castPutIoButton.setMaxWidth(Double.MAX_VALUE);
        Button refreshFiles = new Button("Refresh files");
        refreshFiles.setMaxWidth(Double.MAX_VALUE);
        refreshFiles.setOnAction(event -> refreshPutIoFiles(currentPutIoDirectoryId, true));
        VBox filesBox = new VBox(8, putIoFolderLabel, putIoFilesList, fileActions, refreshFiles);
        VBox.setVgrow(putIoFilesList, Priority.ALWAYS);

        Tab transfersTab = new Tab("Transfers", transfersBox);
        transfersTab.setClosable(false);
        Tab filesTab = new Tab("Files", filesBox);
        filesTab.setClosable(false);
        TabPane tabs = new TabPane(transfersTab, filesTab);
        VBox.setVgrow(tabs, Priority.ALWAYS);

        VBox box = new VBox(10, label, tabs);
        box.getStyleClass().add("panel");
        return box;
    }

    private void configurePutIoFileBrowser() {
        putIoFolderLabel.getStyleClass().add("hint");
        putIoFilesList.setPlaceholder(new Label("No files in this folder."));
        putIoFilesList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(PutIoFile item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                String prefix = item.isDirectory() ? "Folder · " : item.isVideo() ? "Video · " : "";
                String detail = item.isDirectory() ? "" : "\n" + formatSize(item.size());
                setText(prefix + item.name() + detail);
                setTooltip(new Tooltip(item.contentType()));
            }
        });
        playPutIoButton.setDisable(true);
        castPutIoButton.setDisable(true);
        playPutIoButton.setOnAction(event -> playSelectedPutIoFile());
        castPutIoButton.setOnAction(event -> castSelectedPutIoFile());
        upPutIoButton.setDisable(true);
        upPutIoButton.getStyleClass().add("secondary-button");
        upPutIoButton.setOnAction(event ->
                refreshPutIoFiles(currentPutIoParentDirectoryId, true));
        putIoFilesList.getSelectionModel().selectedItemProperty().addListener(
                (ignored, previous, selected) -> {
                    boolean playable = selected != null && selected.isVideo();
                    playPutIoButton.setDisable(!playable);
                    castPutIoButton.setDisable(!playable);
                });
        putIoFilesList.setOnMouseClicked(event -> {
            if (event.getClickCount() != 2) {
                return;
            }
            PutIoFile selected = putIoFilesList.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            if (selected.isDirectory()) {
                refreshPutIoFiles(selected.id(), true);
            } else if (selected.isVideo()) {
                playSelectedPutIoFile();
            }
        });
    }

    private void refreshPutIoFiles(long directoryId, boolean announce) {
        if (filesRefreshInProgress) {
            return;
        }
        filesRefreshInProgress = true;
        if (announce) {
            statusLabel.setText("Loading put.io files…");
        }
        CompletableFuture.supplyAsync(() -> putIoService.files(directoryId), executor)
                .whenComplete((listing, error) -> Platform.runLater(() -> {
                    filesRefreshInProgress = false;
                    if (error != null) {
                        Throwable cause = error.getCause() == null ? error : error.getCause();
                        showError("Could not load put.io files", cause.getMessage());
                        return;
                    }
                    applyPutIoListing(listing);
                    if (announce) {
                        statusLabel.setText(listing.files().size() + " item(s) in "
                                + listing.directoryName());
                    }
                }));
    }

    private void applyPutIoListing(PutIoFileListing listing) {
        currentPutIoDirectoryId = listing.directoryId();
        currentPutIoParentDirectoryId = listing.parentDirectoryId();
        putIoFolderLabel.setText(listing.directoryId() == 0
                ? "put.io files"
                : "Folder · " + listing.directoryName());
        upPutIoButton.setDisable(listing.directoryId() == 0);
        putIoFiles.setAll(listing.files());
    }

    private void playSelectedPutIoFile() {
        PutIoFile selected = putIoFilesList.getSelectionModel().getSelectedItem();
        if (selected == null || !selected.isVideo()) {
            return;
        }
        try {
            videoPlayer.open(selected.name(), putIoService.hlsStreamUrl(selected.id()));
            statusLabel.setText("Playing “" + selected.name() + "”");
        } catch (RuntimeException exception) {
            showError("Could not play video", exception.getMessage());
        }
    }

    private void castSelectedPutIoFile() {
        PutIoFile selected = putIoFilesList.getSelectionModel().getSelectedItem();
        if (selected == null || !selected.isVideo()) {
            return;
        }
        try {
            chromecastService.openSender(
                    selected.name(), putIoService.hlsStreamUrl(selected.id()));
            statusLabel.setText("Opened Chromecast picker for “" + selected.name() + "”");
        } catch (RuntimeException exception) {
            showError("Could not start Chromecast", exception.getMessage());
        }
    }

    private Node buildStatusBar() {
        statusLabel.getStyleClass().add("status");
        HBox box = new HBox(statusLabel);
        box.getStyleClass().add("status-bar");
        return box;
    }

    private void runSearch() {
        String query = queryField.getText().trim();
        if (query.isBlank()) {
            showError("Search", "Enter something to search for.");
            return;
        }
        SavedSearch selected = searchesList.getSelectionModel().getSelectedItem();
        if (selected != null
                && selected.getQuery().equals(query)
                && selected.getMinimumSeeders() == minimumSeeders.getValue()) {
            runMonitoredCheck(selected);
            return;
        }
        long request = resultRequests.begin();
        setBusy(true, "Searching for “" + query + "”…");
        runResultRequest(
                request,
                () -> pirateBayService.search(query, minimumSeeders.getValue()),
                found -> {
                    results.setAll(found);
                    setBusy(false, found.size() + " results");
                },
                "Search failed"
        );
    }

    private void runMonitoredCheck(SavedSearch search) {
        long request = resultRequests.begin();
        setBusy(true, "Checking “" + search + "”…");
        runResultRequest(
                request,
                () -> monitorService.check(search, request),
                ignored -> searchButton.setDisable(false),
                "Saved search failed"
        );
    }

    private void sendSelected() {
        TorrentResult selected = resultsTable.getSelectionModel().getSelectedItem();
        if (selected == null || transferSubmissionActive) {
            return;
        }
        transferSubmissionActive = true;
        sendButton.setDisable(true);
        statusLabel.setText("Sending “" + selected.name() + "” to put.io…");
        runAsync(
                () -> putIoService.add(selected),
                name -> refreshTransfers("Added “" + name + "” to put.io"),
                "Could not add transfer",
                () -> {
                    transferSubmissionActive = false;
                    sendButton.setDisable(
                            resultsTable.getSelectionModel().getSelectedItem() == null);
                }
        );
    }

    private void refreshTransfers() {
        refreshTransfers(null);
    }

    private void refreshTransfers(String successPrefix) {
        if (transferRefreshInProgress) {
            if (successPrefix != null) {
                statusLabel.setText(successPrefix);
                queuedTransferRefreshPrefix = successPrefix;
            }
            return;
        }
        transferRefreshInProgress = true;
        statusLabel.setText(successPrefix == null
                ? "Loading put.io transfers…"
                : successPrefix + " · refreshing transfers…");
        runAsync(
                putIoService::transfers,
                loaded -> {
                    applyTransfers(loaded);
                    statusLabel.setText((successPrefix == null ? "" : successPrefix + " · ")
                            + loaded.size() + " put.io transfers");
                },
                "Could not load transfers",
                () -> {
                    transferRefreshInProgress = false;
                    if (queuedTransferRefreshPrefix != null) {
                        String queuedPrefix = queuedTransferRefreshPrefix;
                        queuedTransferRefreshPrefix = null;
                        refreshTransfers(queuedPrefix);
                    }
                }
        );
    }

    private void refreshTransfersQuietly() {
        if (transferRefreshInProgress || settingsService.get().getPutIoToken().isBlank()) {
            return;
        }
        transferRefreshInProgress = true;
        CompletableFuture.supplyAsync(putIoService::transfers, executor)
                .whenComplete((loaded, error) -> Platform.runLater(() -> {
                    transferRefreshInProgress = false;
                    if (error == null) {
                        applyTransfers(loaded);
                        statusLabel.setText(loaded.size() + " put.io transfers · updated automatically");
                    } else {
                        transferRefreshTimer.stop();
                        Throwable cause = error.getCause() == null ? error : error.getCause();
                        statusLabel.setText("put.io automatic refresh failed: " + cause.getMessage());
                    }
                }));
    }

    private void applyTransfers(List<PutIoTransfer> loaded) {
        boolean previouslyActive = transfers.stream().anyMatch(transfer -> !transfer.isDone());
        transfers.setAll(loaded);
        boolean currentlyActive = loaded.stream().anyMatch(transfer -> !transfer.isDone());
        configureTransferAutoRefresh(currentlyActive);
        if (previouslyActive && !currentlyActive) {
            refreshPutIoFiles(currentPutIoDirectoryId, false);
        }
    }

    private void configureTransferAutoRefresh(boolean hasActiveTransfers) {
        transferRefreshTimer.stop();
        transferRefreshTimer.getKeyFrames().clear();
        LocalSettings settings = settingsService.get();
        if (!settings.isPutIoAutoRefreshEnabled() || settings.getPutIoToken().isBlank()
                || !hasActiveTransfers) {
            return;
        }
        transferRefreshTimer.getKeyFrames().add(new KeyFrame(
                Duration.seconds(settings.getPutIoRefreshIntervalSeconds()),
                event -> refreshTransfersQuietly()));
        transferRefreshTimer.setCycleCount(Timeline.INDEFINITE);
        transferRefreshTimer.play();
    }

    private void refreshSavedSearches() {
        String selectedId = Optional.ofNullable(searchesList.getSelectionModel().getSelectedItem())
                .map(SavedSearch::getId).orElse(null);
        savedSearches.setAll(settingsService.searches());
        if (selectedId != null) {
            savedSearches.stream().filter(search -> search.getId().equals(selectedId)).findFirst()
                    .ifPresent(searchesList.getSelectionModel()::select);
        }
        refreshSelectedSearchStatus();
    }

    private void showAddSearch() {
        Dialog<SavedSearch> dialog = new Dialog<>();
        dialog.setTitle("Add saved search");
        dialog.setHeaderText("Monitor a Pirate Bay search while this app is running.");
        dialog.initOwner(window());

        TextField name = new TextField();
        name.setPromptText("Display name");
        TextField query = new TextField(queryField.getText());
        query.setPromptText("Search query");
        Spinner<Integer> seeders = new Spinner<>(0, 100_000,
                settingsService.get().getDefaultMinimumSeeders());
        VBox fields = new VBox(8,
                new Label("Name"), name,
                new Label("Query"), query,
                new Label("Minimum seeders"), seeders);
        fields.setPadding(new Insets(8));
        dialog.getDialogPane().setContent(fields);

        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        Node saveButton = dialog.getDialogPane().lookupButton(save);
        saveButton.disableProperty().bind(query.textProperty().isEmpty());
        dialog.setResultConverter(button -> button == save
                ? new SavedSearch(name.getText().trim(), query.getText().trim(), seeders.getValue())
                : null);
        dialog.showAndWait().ifPresent(search -> {
            settingsService.addSearch(search);
            refreshSavedSearches();
            searchesList.getSelectionModel().select(search);
            statusLabel.setText("Saved “" + search + "”");
        });
    }

    private void removeSelectedSearch() {
        SavedSearch selected = searchesList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        ButtonType remove = new ButtonType("Remove", ButtonBar.ButtonData.OK_DONE);
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Remove the saved search “" + selected + "”?", remove, ButtonType.CANCEL);
        confirm.initOwner(window());
        confirm.setHeaderText(null);
        confirm.showAndWait().filter(remove::equals).ifPresent(button -> {
            settingsService.removeSearch(selected.getId());
            refreshSavedSearches();
            statusLabel.setText("Removed saved search");
        });
    }

    private void showEditSearch() {
        SavedSearch selected = searchesList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Edit saved search");
        dialog.setHeaderText("Update this monitored search.");
        dialog.initOwner(window());

        TextField name = new TextField(selected.getName());
        TextField query = new TextField(selected.getQuery());
        Spinner<Integer> seeders = new Spinner<>(0, 100_000, selected.getMinimumSeeders());
        VBox fields = new VBox(8,
                new Label("Name"), name,
                new Label("Query"), query,
                new Label("Minimum seeders"), seeders);
        fields.setPadding(new Insets(8));
        dialog.getDialogPane().setContent(fields);

        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(save).disableProperty().bind(query.textProperty().isEmpty());
        dialog.setResultConverter(button -> button == save);
        dialog.showAndWait().filter(Boolean::booleanValue).ifPresent(ignored -> {
            String updatedQuery = query.getText().trim();
            int updatedSeeders = seeders.getValue();
            boolean criteriaChanged = !selected.getQuery().equals(updatedQuery)
                    || selected.getMinimumSeeders() != updatedSeeders;
            String updatedName = name.getText().trim();
            statusLabel.setText("Updating “" + selected + "”…");
            runAsync(
                    () -> {
                        monitorService.updateSearch(selected, search -> {
                            search.setName(updatedName);
                            search.setQuery(updatedQuery);
                            search.setMinimumSeeders(updatedSeeders);
                            if (criteriaChanged) {
                                search.getSeenResultIds().clear();
                                search.setLastChecked(null);
                            }
                        });
                        return selected;
                    },
                    updated -> {
                        refreshSavedSearches();
                        searchesList.getSelectionModel().select(updated);
                        statusLabel.setText("Updated “" + updated + "”"
                                + (criteriaChanged ? " and reset its baseline" : ""));
                    },
                    "Could not update saved search");
        });
    }

    private void toggleSelectedSearch() {
        SavedSearch selected = searchesList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        boolean enable = !selected.isEnabled();
        toggleSearchButton.setDisable(true);
        statusLabel.setText((enable ? "Enabling “" : "Pausing “") + selected + "”…");
        runAsync(
                () -> {
                    monitorService.updateSearch(selected, search -> search.setEnabled(enable));
                    return selected;
                },
                updated -> {
                    searchesList.refresh();
                    refreshSelectedSearchStatus();
                    statusLabel.setText((updated.isEnabled() ? "Monitoring “" : "Paused “")
                            + updated + "”");
                },
                "Could not change monitoring state",
                this::refreshSelectedSearchStatus);
    }

    private void refreshSelectedSearchStatus() {
        SavedSearch selected = searchesList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        toggleSearchButton.setDisable(false);
        toggleSearchButton.setText(selected.isEnabled() ? "Pause" : "Enable");
        String lastChecked = selected.getLastChecked() == null
                ? "Baseline not established"
                : "Last checked " + CHECK_FORMAT.format(selected.getLastChecked());
        selectedSearchStatus.setText((selected.isEnabled() ? "Monitoring · " : "Paused · ") + lastChecked);
    }

    private void leaveSavedModeIfCriteriaDiverged() {
        if (applyingSavedSearchCriteria) {
            return;
        }
        SavedSearch selected = searchesList.getSelectionModel().getSelectedItem();
        if (selected != null
                && (!selected.getQuery().equals(queryField.getText().trim())
                || selected.getMinimumSeeders() != minimumSeeders.getValue())) {
            searchesList.getSelectionModel().clearSelection();
            selectedSearchStatus.setText("Free-form search · saved monitoring continues in background");
        }
    }

    private void configureRestoreButton(Button button, Runnable action) {
        button.getStyleClass().addAll("secondary-button", "panel-restore-button");
        button.setOnAction(event -> action.run());
    }

    private Node panelHeader(String title, Runnable collapseAction) {
        Label label = sectionLabel(title);
        Button collapse = new Button("Hide");
        collapse.getStyleClass().addAll("secondary-button", "panel-toggle-button");
        collapse.setTooltip(new Tooltip("Collapse this panel"));
        collapse.setOnAction(event -> collapseAction.run());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, label, spacer, collapse);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private void setSearchesPanelVisible(boolean visible) {
        LocalSettings settings = settingsService.get();
        if (settings.isSavedSearchesPanelVisible() == visible) {
            return;
        }
        captureDividerPreferences();
        settings.setSavedSearchesPanelVisible(visible);
        settingsService.save();
        rebuildContentSplit();
    }

    private void setTransfersPanelVisible(boolean visible) {
        LocalSettings settings = settingsService.get();
        if (settings.isPutIoPanelVisible() == visible) {
            return;
        }
        captureDividerPreferences();
        settings.setPutIoPanelVisible(visible);
        settingsService.save();
        rebuildContentSplit();
    }

    private void rebuildContentSplit() {
        LocalSettings settings = settingsService.get();
        contentSplit.getItems().clear();
        if (settings.isSavedSearchesPanelVisible()) {
            contentSplit.getItems().add(searchesPanel);
        }
        contentSplit.getItems().add(resultsPanel);
        if (settings.isPutIoPanelVisible()) {
            contentSplit.getItems().add(transfersPanel);
        }
        showSearchesButton.setManaged(!settings.isSavedSearchesPanelVisible());
        showSearchesButton.setVisible(!settings.isSavedSearchesPanelVisible());
        showTransfersButton.setManaged(!settings.isPutIoPanelVisible());
        showTransfersButton.setVisible(!settings.isPutIoPanelVisible());

        restoringLayout = true;
        Platform.runLater(() -> {
            applyDividerPreferences();
            installDividerListeners();
            restoringLayout = false;
        });
    }

    private void applyDividerPreferences() {
        LocalSettings settings = settingsService.get();
        if (settings.isSavedSearchesPanelVisible() && settings.isPutIoPanelVisible()) {
            contentSplit.setDividerPositions(
                    settings.getSavedSearchesPanelRatio(),
                    1.0 - settings.getPutIoPanelRatio());
        } else if (settings.isSavedSearchesPanelVisible()) {
            contentSplit.setDividerPosition(0, settings.getSavedSearchesPanelRatio());
        } else if (settings.isPutIoPanelVisible()) {
            contentSplit.setDividerPosition(0, 1.0 - settings.getPutIoPanelRatio());
        }
    }

    private void installDividerListeners() {
        for (SplitPane.Divider divider : contentSplit.getDividers()) {
            divider.positionProperty().addListener((ignored, previous, current) -> {
                if (!restoringLayout) {
                    captureDividerPreferences();
                    layoutSaveDelay.playFromStart();
                }
            });
        }
    }

    private void captureDividerPreferences() {
        LocalSettings settings = settingsService.get();
        double[] positions = contentSplit.getDividerPositions();
        if (settings.isSavedSearchesPanelVisible() && settings.isPutIoPanelVisible()
                && positions.length == 2) {
            settings.setSavedSearchesPanelRatio(positions[0]);
            settings.setPutIoPanelRatio(1.0 - positions[1]);
        } else if (settings.isSavedSearchesPanelVisible() && positions.length == 1) {
            settings.setSavedSearchesPanelRatio(positions[0]);
        } else if (settings.isPutIoPanelVisible() && positions.length == 1) {
            settings.setPutIoPanelRatio(1.0 - positions[0]);
        }
    }

    private void showSettings() {
        LocalSettings settings = settingsService.get();
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Settings");
        dialog.setHeaderText("Local application settings");
        dialog.initOwner(window());

        PasswordField token = new PasswordField();
        token.setText(settings.getPutIoToken());
        token.setPromptText("put.io OAuth token");
        TextField apiBase = new TextField(settings.getPirateBayApiBaseUrl());
        Spinner<Integer> interval = new Spinner<>(1, 1440, settings.getMonitorIntervalMinutes());
        Spinner<Integer> defaultSeeders = new Spinner<>(0, 100_000, settings.getDefaultMinimumSeeders());
        CheckBox autoRefresh = new CheckBox("Automatically refresh put.io transfers");
        autoRefresh.setSelected(settings.isPutIoAutoRefreshEnabled());
        Spinner<Integer> transferInterval = new Spinner<>(
                1, 60, settings.getPutIoRefreshIntervalSeconds());
        transferInterval.disableProperty().bind(autoRefresh.selectedProperty().not());
        CheckBox showSavedSearches = new CheckBox("Show saved searches panel at startup");
        showSavedSearches.setSelected(settings.isSavedSearchesPanelVisible());
        CheckBox showPutIo = new CheckBox("Show put.io panel at startup");
        showPutIo.setSelected(settings.isPutIoPanelVisible());

        Label settingsPath = new Label("Saved at " + settingsService.settingsFile());
        settingsPath.getStyleClass().add("hint");
        settingsPath.setWrapText(true);
        VBox fields = new VBox(8,
                new Label("put.io token"), token,
                new Label("Pirate Bay API base URL"), apiBase,
                new Label("Monitor interval (minutes)"), interval,
                new Label("Default minimum seeders"), defaultSeeders,
                new Separator(),
                autoRefresh,
                new Label("put.io refresh interval (seconds)"), transferInterval,
                new Separator(),
                new Label("Panel preferences"),
                showSavedSearches,
                showPutIo,
                new Separator(), settingsPath);
        fields.setPadding(new Insets(8));
        fields.setPrefWidth(480);
        dialog.getDialogPane().setContent(fields);

        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        dialog.setResultConverter(button -> button == save);
        dialog.showAndWait().filter(Boolean::booleanValue).ifPresent(ignored -> {
            settings.setPutIoToken(token.getText().trim());
            settings.setPirateBayApiBaseUrl(apiBase.getText().trim());
            settings.setMonitorIntervalMinutes(interval.getValue());
            settings.setDefaultMinimumSeeders(defaultSeeders.getValue());
            settings.setPutIoAutoRefreshEnabled(autoRefresh.isSelected());
            settings.setPutIoRefreshIntervalSeconds(transferInterval.getValue());
            settings.setSavedSearchesPanelVisible(showSavedSearches.isSelected());
            settings.setPutIoPanelVisible(showPutIo.isSelected());
            settingsService.save();
            rebuildContentSplit();
            configureTransferAutoRefresh(
                    transfers.stream().anyMatch(transfer -> !transfer.isDone()));
            statusLabel.setText("Settings saved; checking put.io…");
            if (!settings.getPutIoToken().isBlank()) {
                runAsync(putIoService::validateAccount,
                        username -> statusLabel.setText("Connected to put.io as " + username),
                        "put.io connection failed");
            }
        });
    }

    private void openSelectedPage() {
        TorrentResult selected = resultsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        try {
            Desktop.getDesktop().browse(URI.create("https://thepiratebay.org/description.php?id=" + selected.id()));
        } catch (Exception exception) {
            showError("Could not open page", exception.getMessage());
        }
    }

    @EventListener
    public void onMonitorUpdate(MonitorUpdate update) {
        Platform.runLater(() -> {
            if ((update.uiRequestGeneration() > 0
                    && !resultRequests.isCurrent(update.uiRequestGeneration()))
                    || (update.uiRequestGeneration() == 0 && resultRequests.hasActiveRequest())) {
                return;
            }
            SavedSearch current = settingsService.findSearch(update.searchId()).orElse(null);
            long lastApplied = lastAppliedMonitorSequences.getOrDefault(update.searchId(), 0L);
            if (!update.isCurrentFor(current, lastApplied)) {
                return;
            }
            lastAppliedMonitorSequences.put(update.searchId(), update.sequence());
            SavedSearch selected = searchesList.getSelectionModel().getSelectedItem();
            if (selected != null && selected.getId().equals(update.searchId())) {
                setBusy(false, update.error() == null ? update.results().size() + " results" : "Error");
                if (update.error() == null) {
                    results.setAll(update.results());
                }
            }
            if (update.error() != null) {
                statusLabel.setText(update.searchName() + ": " + update.error());
            } else if (update.newResultCount() > 0) {
                statusLabel.setText(update.newResultCount() + " new result(s) for “"
                        + update.searchName() + "”");
            }
            searchesList.refresh();
            refreshSelectedSearchStatus();
        });
    }

    private <T> void runAsync(Supplier<T> operation, java.util.function.Consumer<T> success, String errorTitle) {
        runAsync(operation, success, errorTitle, () -> { });
    }

    private <T> void runAsync(
            Supplier<T> operation,
            java.util.function.Consumer<T> success,
            String errorTitle,
            Runnable completion
    ) {
        CompletableFuture.supplyAsync(operation, executor)
                .whenComplete((value, error) -> Platform.runLater(() -> {
                    try {
                        if (error != null) {
                            setBusy(false, errorTitle);
                            Throwable cause = error.getCause() == null ? error : error.getCause();
                            showError(errorTitle, cause.getMessage());
                            sendButton.setDisable(resultsTable.getSelectionModel().getSelectedItem() == null);
                        } else {
                            success.accept(value);
                        }
                    } finally {
                        completion.run();
                    }
                }));
    }

    private <T> void runResultRequest(
            long request,
            Supplier<T> operation,
            java.util.function.Consumer<T> success,
            String errorTitle
    ) {
        CompletableFuture.supplyAsync(operation, executor)
                .whenComplete((value, error) -> Platform.runLater(() -> {
                    if (!resultRequests.isCurrent(request)) {
                        return;
                    }
                    try {
                        if (error != null) {
                            setBusy(false, errorTitle);
                            Throwable cause = error.getCause() == null ? error : error.getCause();
                            showError(errorTitle, cause.getMessage());
                        } else {
                            success.accept(value);
                        }
                    } finally {
                        resultRequests.complete(request);
                    }
                }));
    }

    private void setBusy(boolean busy, String status) {
        searchButton.setDisable(busy);
        statusLabel.setText(status);
    }

    private Window window() {
        return root.getScene() == null ? null : root.getScene().getWindow();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(window());
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message == null || message.isBlank() ? "Unknown error" : message);
        alert.show();
    }

    private static Label sectionLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int unit = -1;
        do {
            value /= 1024;
            unit++;
        } while (value >= 1024 && unit < units.length - 1);
        return SIZE_FORMAT.format(value) + " " + units[unit];
    }
}
