package com.thepiratebrowser.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class MainActivity extends Activity {
    private static final String PREFS = "pirate_browser";
    private static final String TOKEN_KEY = "putio.oauth_token";
    private static final String STATE_QUERY = "state.query";
    private static final String STATE_LAST_QUERY = "state.last_query";
    private static final String STATE_CONTENT = "state.content";
    private static final String STATE_CONTENT_TITLE = "state.content_title";
    private static final String STATE_CONTENT_MESSAGE = "state.content_message";

    private final ExecutorService background = Executors.newCachedThreadPool();
    private final Handler main = new Handler(Looper.getMainLooper());

    private SharedPreferences preferences;
    private TorrentSearchService searchService;
    private final PutIoService putIoService = new PutIoService();
    private EditText queryField;
    private RecyclerView resultsList;
    private ResultAdapter resultsAdapter;
    private View emptyPanel;
    private TextView emptyTitle;
    private TextView emptyMessage;
    private Button retryButton;
    private ProgressBar progress;
    private TextView status;
    private Button searchButton;
    private Button sourcesButton;
    private Button connectButton;
    private final LatestRequestGate requestGate = new LatestRequestGate();
    private String lastQuery = "";
    private ContentState currentContentState = ContentState.DISCOVERY;
    private String currentContentTitle;
    private String currentContentMessage;
    private volatile long deviceLinkGeneration;
    private volatile Future<?> deviceLinkPolling;

    private static final int INK = Color.rgb(8, 18, 34);
    private static final int NAVY = Color.rgb(14, 30, 51);
    private static final int NAVY_RAISED = Color.rgb(22, 43, 68);
    private static final int PARCHMENT = Color.rgb(246, 238, 219);
    private static final int PARCHMENT_MUTED = Color.rgb(205, 197, 178);
    private static final int GOLD = Color.rgb(218, 166, 65);
    private static final int GOLD_PRESSED = Color.rgb(238, 190, 91);
    private static final int TEAL = Color.rgb(75, 156, 146);
    private static final int CORAL = Color.rgb(239, 112, 102);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        searchService = new TorrentSearchService(preferences);
        setContentView(buildScreen());
        updateConnectionButton();
        if (state != null) {
            lastQuery = state.getString(STATE_LAST_QUERY, "");
            queryField.setText(state.getString(STATE_QUERY, lastQuery));
            ContentState restored = contentState(
                    state.getString(STATE_CONTENT, ContentState.DISCOVERY.name())
            );
            String restoredTitle = state.getString(STATE_CONTENT_TITLE);
            String restoredMessage = state.getString(STATE_CONTENT_MESSAGE);
            if ((restored == ContentState.SEARCHING || restored == ContentState.RESULTS)
                    && !lastQuery.isEmpty()) {
                runSearch(lastQuery);
            } else if (restored != ContentState.DISCOVERY) {
                showContentState(restored, restoredTitle, restoredMessage);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putString(STATE_QUERY, queryField == null ? "" : queryField.getText().toString());
        state.putString(STATE_LAST_QUERY, lastQuery);
        state.putString(STATE_CONTENT, currentContentState.name());
        state.putString(STATE_CONTENT_TITLE, currentContentTitle);
        state.putString(STATE_CONTENT_MESSAGE, currentContentMessage);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onDestroy() {
        cancelDeviceLink();
        requestGate.destroy();
        main.removeCallbacksAndMessages(null);
        background.shutdownNow();
        super.onDestroy();
    }

    private View buildScreen() {
        boolean compactHeight = getResources().getConfiguration().screenHeightDp < 600;
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(INK);

        FrameLayout masthead = new FrameLayout(this);
        masthead.setBackgroundColor(NAVY);
        ImageView mastheadArt = new ImageView(this);
        mastheadArt.setImageResource(R.drawable.bg_discovery_chart);
        mastheadArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mastheadArt.setAlpha(0.20f);
        mastheadArt.setContentDescription(null);
        masthead.addView(mastheadArt, matchFrame());

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(16), dp(12), dp(16), dp(14));

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView mascot = new ImageView(this);
        mascot.setImageResource(R.drawable.pirate_penguin_art);
        mascot.setContentDescription("Pirate Browser penguin mascot");
        mascot.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int mascotSize = compactHeight ? 52 : 62;
        brandRow.addView(mascot, new LinearLayout.LayoutParams(
                dp(mascotSize), dp(mascotSize)));

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setPadding(dp(10), 0, 0, 0);
        TextView title = new TextView(this);
        title.setText("Pirate Browser");
        title.setTextSize(compactHeight ? 21 : 24);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        brand.addView(title);
        TextView subtitle = new TextView(this);
        subtitle.setText("One search. Every horizon.");
        subtitle.setTextSize(13);
        subtitle.setTextColor(PARCHMENT_MUTED);
        brand.addView(subtitle);
        int brandHeight = compactHeight
                ? 58
                : Math.round(64 * Math.min(
                        1.25f,
                        getResources().getConfiguration().fontScale
                ));
        brandRow.addView(brand, new LinearLayout.LayoutParams(0, dp(brandHeight), 1));
        header.addView(brandRow);

        LinearLayout utilities = new LinearLayout(this);
        utilities.setGravity(Gravity.CENTER_VERTICAL);
        utilities.setPadding(0, dp(8), 0, 0);
        sourcesButton = utilityButton("");
        sourcesButton.setOnClickListener(ignored -> showSources());
        utilities.addView(sourcesButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        connectButton = utilityButton("");
        connectButton.setOnClickListener(ignored -> showPutIoConnection());
        LinearLayout.LayoutParams connectParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        connectParams.setMarginStart(dp(10));
        utilities.addView(connectButton, connectParams);
        header.addView(utilities);
        masthead.addView(header, matchFrame());
        int mastheadHeight = compactHeight
                ? 132
                : Math.round(154 * Math.min(
                        1.25f,
                        getResources().getConfiguration().fontScale
                ));
        page.addView(masthead, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(mastheadHeight)));

        LinearLayout searchDeck = new LinearLayout(this);
        searchDeck.setOrientation(LinearLayout.VERTICAL);
        searchDeck.setPadding(dp(16), dp(14), dp(16), dp(14));
        searchDeck.setBackground(rounded(NAVY_RAISED, 0, 0));

        TextView eyebrow = bodyText("CHART A COURSE");
        eyebrow.setTextSize(11);
        eyebrow.setLetterSpacing(0.14f);
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        eyebrow.setTextColor(GOLD);
        searchDeck.addView(eyebrow);

        queryField = new EditText(this);
        queryField.setHint("Movies, shows, anime...");
        queryField.setHintTextColor(Color.rgb(107, 116, 127));
        queryField.setTextColor(INK);
        queryField.setTextSize(17);
        queryField.setSingleLine();
        queryField.setPadding(dp(16), 0, dp(16), 0);
        queryField.setBackground(rounded(PARCHMENT, dp(14), GOLD));
        queryField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        queryField.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                runSearch();
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams queryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        queryParams.setMargins(0, dp(8), 0, dp(10));
        searchDeck.addView(queryField, queryParams);

        searchButton = primaryButton("Search every source");
        searchButton.setOnClickListener(ignored -> runSearch());
        searchDeck.addView(searchButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        LinearLayout stateRow = new LinearLayout(this);
        stateRow.setGravity(Gravity.CENTER_VERTICAL);
        stateRow.setPadding(dp(2), dp(10), dp(2), 0);
        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        progress.setIndeterminateTintList(ColorStateList.valueOf(GOLD));
        stateRow.addView(progress, new LinearLayout.LayoutParams(dp(24), dp(24)));
        status = new TextView(this);
        status.setText("Ready to search all enabled sources.");
        status.setTextColor(PARCHMENT_MUTED);
        status.setTextSize(13);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        statusParams.setMarginStart(dp(8));
        stateRow.addView(status, statusParams);
        searchDeck.addView(stateRow);
        page.addView(searchDeck);

        FrameLayout content = new FrameLayout(this);
        content.setBackgroundColor(INK);
        resultsList = new RecyclerView(this);
        resultsList.setLayoutManager(new LinearLayoutManager(this));
        resultsList.setClipToPadding(false);
        resultsList.setPadding(dp(14), dp(14), dp(14), dp(24));
        resultsAdapter = new ResultAdapter();
        resultsList.setAdapter(resultsAdapter);
        content.addView(resultsList, matchFrame());

        emptyPanel = buildEmptyPanel();
        FrameLayout.LayoutParams emptyParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(300), Gravity.CENTER);
        emptyParams.setMargins(dp(16), dp(16), dp(16), dp(20));
        content.addView(emptyPanel, emptyParams);
        if (compactHeight) {
            page.addView(content, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(420)));
        } else {
            page.addView(content, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        }

        updateSourcesButton();
        showContentState(ContentState.DISCOVERY, null, null);
        if (!compactHeight) {
            return page;
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return scroll;
    }

    private void runSearch() {
        runSearch(queryField.getText().toString());
    }

    private void runSearch(String requestedQuery) {
        String query = requestedQuery.trim();
        if (query.isEmpty()) {
            queryField.setError("Enter a search");
            return;
        }
        lastQuery = query;
        if (enabledSourceCount() == 0) {
            status.setTextColor(CORAL);
            status.setText("Choose at least one source before searching.");
            showContentState(
                    ContentState.ERROR,
                    "No sources enabled",
                    "Choose at least one torrent source to start searching."
            );
            return;
        }
        LatestRequestGate.Ticket ticket = requestGate.begin(query);
        setBusy(true, "Charting " + enabledSourceCount() + " enabled sources...");
        resultsAdapter.submit(Collections.emptyList());
        showContentState(ContentState.SEARCHING, "Searching the horizon", query);
        InputMethodManager keyboard =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        keyboard.hideSoftInputFromWindow(queryField.getWindowToken(), 0);
        background.execute(() -> {
            TorrentSearchService.SearchOutcome outcome = searchService.search(query);
            postToUi(() -> {
                if (requestGate.accept(ticket)) {
                    showSearchOutcome(ticket.query, outcome);
                }
            });
        });
    }

    private void showSearchOutcome(String query, TorrentSearchService.SearchOutcome outcome) {
        resultsAdapter.submit(outcome.results);
        String message;
        if (outcome.results.isEmpty() && !outcome.failures.isEmpty()) {
            message = "No sources answered. Check your connection and try again.";
        } else if (outcome.results.isEmpty()) {
            message = "No results for \"" + query + "\".";
        } else if (!outcome.failures.isEmpty()) {
            message = outcome.results.size() + " results - "
                    + outcome.failures.size() + " source(s) unavailable";
        } else {
            message = outcome.results.size() + " results";
        }
        if (outcome.results.isEmpty()) {
            ContentState state = outcome.failures.isEmpty()
                    ? ContentState.NO_RESULTS : ContentState.ERROR;
            showContentState(
                    state,
                    state == ContentState.ERROR ? "The sea is rough" : "No treasure here",
                    message
            );
            resultsList.setVisibility(View.GONE);
        } else {
            showContentState(ContentState.RESULTS, null, null);
        }
        status.setTextColor(outcome.failures.isEmpty() ? PARCHMENT_MUTED : CORAL);
        setBusy(false, message);
    }

    private View buildEmptyPanel() {
        FrameLayout panel = new FrameLayout(this);
        panel.setBackground(rounded(NAVY_RAISED, dp(18), Color.rgb(55, 78, 105)));
        panel.setClipToOutline(true);

        ImageView art = new ImageView(this);
        art.setImageResource(R.drawable.bg_discovery_chart);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setAlpha(0.78f);
        art.setContentDescription(null);
        panel.addView(art, matchFrame());

        View scrim = new View(this);
        scrim.setBackgroundColor(Color.argb(118, 5, 14, 27));
        panel.addView(scrim, matchFrame());

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER);
        copy.setPadding(dp(28), dp(24), dp(28), dp(24));

        emptyTitle = bodyText("");
        emptyTitle.setTextColor(Color.WHITE);
        emptyTitle.setTextSize(24);
        emptyTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        emptyTitle.setGravity(Gravity.CENTER);
        copy.addView(emptyTitle);

        emptyMessage = bodyText("");
        emptyMessage.setTextColor(PARCHMENT_MUTED);
        emptyMessage.setTextSize(15);
        emptyMessage.setGravity(Gravity.CENTER);
        emptyMessage.setPadding(0, dp(8), 0, dp(16));
        emptyMessage.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        copy.addView(emptyMessage);

        retryButton = primaryButton("Try again");
        retryButton.setOnClickListener(ignored -> {
            if (enabledSourceCount() == 0) {
                showSources();
                return;
            }
            if (!lastQuery.isEmpty()) {
                queryField.setText(lastQuery);
                runSearch();
            }
        });
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(dp(164), dp(48));
        copy.addView(retryButton, retryParams);
        panel.addView(copy, matchFrame());
        return panel;
    }

    private void showContentState(ContentState state, String title, String message) {
        currentContentState = state;
        currentContentTitle = title;
        currentContentMessage = message;
        resultsList.setVisibility(View.GONE);
        emptyPanel.setVisibility(View.VISIBLE);
        retryButton.setVisibility(state == ContentState.ERROR ? View.VISIBLE : View.GONE);
        retryButton.setText(enabledSourceCount() == 0 ? "Choose sources" : "Try again");
        switch (state) {
            case DISCOVERY -> {
                emptyTitle.setText("Your next find is out there");
                emptyMessage.setText(
                        "Search every enabled source at once, then send a magnet straight to put.io."
                );
                emptyTitle.setTextColor(Color.WHITE);
            }
            case SEARCHING -> {
                emptyTitle.setText(title);
                emptyMessage.setText("Looking for \"" + message + "\" across every enabled source.");
                emptyTitle.setTextColor(GOLD);
            }
            case NO_RESULTS -> {
                emptyTitle.setText(title);
                emptyMessage.setText(message + "\nTry a shorter title or fewer keywords.");
                emptyTitle.setTextColor(Color.WHITE);
            }
            case ERROR -> {
                emptyTitle.setText(title);
                emptyMessage.setText(message);
                emptyTitle.setTextColor(CORAL);
            }
            case RESULTS -> {
                emptyPanel.setVisibility(View.GONE);
                resultsList.setVisibility(View.VISIBLE);
            }
        }
    }

    private ContentState contentState(String name) {
        try {
            return ContentState.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return ContentState.DISCOVERY;
        }
    }

    private int enabledSourceCount() {
        int count = 0;
        for (String source : TorrentSearchService.SOURCES) {
            if (searchService.enabled(source)) {
                count++;
            }
        }
        return count;
    }

    private void updateSourcesButton() {
        int count = enabledSourceCount();
        sourcesButton.setText("Sources  " + count + "/" + TorrentSearchService.SOURCES.size());
        sourcesButton.setContentDescription(
                count + " of " + TorrentSearchService.SOURCES.size() + " torrent sources enabled"
        );
        if (retryButton != null && emptyPanel != null
                && emptyPanel.getVisibility() == View.VISIBLE) {
            retryButton.setText(count == 0 ? "Choose sources" : "Try again");
        }
    }

    private enum ContentState {
        DISCOVERY,
        SEARCHING,
        NO_RESULTS,
        ERROR,
        RESULTS
    }

    private void addTransfer(TorrentResult result) {
        String token = oauthToken();
        if (token == null || token.trim().isEmpty()) {
            String handoff = "https://put.io/default/magnet?url=" + Uri.encode(result.magnet);
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(handoff)));
            return;
        }
        resultsAdapter.setActionState(result, ActionState.ADDING);
        background.execute(() -> {
            try {
                String message = putIoService.addTransfer(token, result.magnet);
                postToUi(() -> {
                    resultsAdapter.setActionState(result, ActionState.ADDED);
                    toast(message);
                });
            } catch (Exception error) {
                postToUi(() -> {
                    resultsAdapter.setActionState(result, ActionState.READY);
                    showError(error.getMessage());
                });
            }
        });
    }

    private void showSources() {
        List<CheckBox> boxes = new ArrayList<>();
        LinearLayout content = dialogContent();
        TextView explanation = bodyText(
                "Every enabled source is searched concurrently. Results are normalized and duplicates are collapsed.");
        explanation.setPadding(0, 0, 0, dp(8));
        content.addView(explanation);
        for (String source : TorrentSearchService.SOURCES) {
            CheckBox box = new CheckBox(this);
            box.setText(source);
            box.setTextColor(INK);
            box.setButtonTintList(ColorStateList.valueOf(GOLD));
            box.setChecked(searchService.enabled(source));
            boxes.add(box);
            content.addView(box);
        }

        new AlertDialog.Builder(this)
                .setTitle("Torrent sources")
                .setView(content)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    for (int i = 0; i < boxes.size(); i++) {
                        searchService.setEnabled(TorrentSearchService.SOURCES.get(i), boxes.get(i).isChecked());
                    }
                    updateSourcesButton();
                    toast("Torrent sources saved.");
                })
                .show();
    }

    private void showPutIoConnection() {
        if (!BuildConfig.PUTIO_OAUTH_TOKEN.trim().isEmpty()) {
            toast("put.io is already connected in this build.");
            return;
        }
        LinearLayout content = dialogContent();
        TextView instructions = bodyText(
                "Link this device through put.io, or paste an OAuth token manually. "
                        + "The token stays in this app's private local storage.");
        content.addView(instructions);

        Button deviceLink = primaryButton(BuildConfig.PUTIO_CLIENT_ID.trim().isEmpty()
                ? "Device link unavailable in this build"
                : "Link with put.io");
        deviceLink.setEnabled(!BuildConfig.PUTIO_CLIENT_ID.trim().isEmpty());
        LinearLayout.LayoutParams linkParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        linkParams.setMargins(0, dp(10), 0, dp(8));
        content.addView(deviceLink, linkParams);

        Button openPutIo = secondaryButton("Open put.io API apps");
        openPutIo.setTextColor(Color.rgb(23, 32, 51));
        openPutIo.setOnClickListener(ignored -> startActivity(
                new Intent(Intent.ACTION_VIEW, Uri.parse("https://app.put.io/oauth"))));
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(46));
        openParams.setMargins(0, dp(10), 0, dp(8));
        content.addView(openPutIo, openParams);

        TextView values = bodyText(
                "Website: https://github.com/Redmancometh/ThePirateBrowser\n"
                        + "Callback: http://127.0.0.1:8765/callback");
        values.setTextIsSelectable(true);
        content.addView(values);

        EditText token = new EditText(this);
        token.setHint("OAuth token");
        token.setSingleLine();
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        token.setText(preferences.getString(TOKEN_KEY, ""));
        content.addView(token, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Connect put.io")
                .setView(content)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Disconnect", null)
                .setPositiveButton("Test & save", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            deviceLink.setOnClickListener(view -> beginDeviceLink(dialog, deviceLink));
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
                preferences.edit().remove(TOKEN_KEY).apply();
                updateConnectionButton();
                dialog.dismiss();
                toast("put.io disconnected.");
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String candidate = token.getText().toString().trim();
                if (candidate.trim().isEmpty()) {
                    token.setError("Paste an OAuth token");
                    return;
                }
                Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                save.setEnabled(false);
                save.setText("Testing…");
                background.execute(() -> {
                    try {
                        putIoService.verifyToken(candidate);
                        postToUi(() -> {
                            if (!dialog.isShowing()) {
                                return;
                            }
                            preferences.edit().putString(TOKEN_KEY, candidate).apply();
                            updateConnectionButton();
                            dialog.dismiss();
                            toast("put.io connected.");
                        });
                    } catch (Exception error) {
                        postToUi(() -> {
                            if (!dialog.isShowing()) {
                                return;
                            }
                            save.setEnabled(true);
                            save.setText("Test & save");
                            token.setError(error.getMessage());
                        });
                    }
                });
            });
        });
        dialog.setOnDismissListener(ignored -> cancelDeviceLink());
        dialog.show();
    }

    private void beginDeviceLink(AlertDialog connectionDialog, Button linkButton) {
        long attempt = startDeviceLinkAttempt();
        linkButton.setEnabled(false);
        linkButton.setText("Getting code…");
        deviceLinkPolling = background.submit(() -> {
            try {
                PutIoService.DeviceCode code =
                        putIoService.requestDeviceCode(BuildConfig.PUTIO_CLIENT_ID);
                postToUi(() -> {
                    if (!isCurrentDeviceLink(attempt)) {
                        return;
                    }
                    linkButton.setEnabled(true);
                    linkButton.setText("Link with put.io");
                    AlertDialog approval = new AlertDialog.Builder(this)
                            .setTitle("Enter this code at put.io")
                            .setMessage(code.code + "\n\nThe app will finish connecting after you approve it.")
                            .setNegativeButton(
                                    "Cancel",
                                    (dialog, which) -> cancelDeviceLink(attempt)
                            )
                            .setPositiveButton("Open put.io/link", (dialog, which) ->
                                    startActivity(new Intent(Intent.ACTION_VIEW,
                                            Uri.parse("https://put.io/link"))))
                            .create();
                    approval.setOnCancelListener(ignored -> cancelDeviceLink(attempt));
                    approval.show();
                    deviceLinkPolling = background.submit(
                            () -> finishDeviceLink(attempt, code, connectionDialog, approval)
                    );
                });
            } catch (Exception error) {
                postToUi(() -> {
                    if (!isCurrentDeviceLink(attempt)) {
                        return;
                    }
                    linkButton.setEnabled(true);
                    linkButton.setText("Link with put.io");
                    showError(error.getMessage());
                });
            }
        });
    }

    private void finishDeviceLink(
            long attempt,
            PutIoService.DeviceCode code,
            AlertDialog connectionDialog,
            AlertDialog approvalDialog
    ) {
        try {
            String token = putIoService.waitForDeviceToken(code);
            postToUi(() -> completeDeviceLink(
                    attempt,
                    token,
                    connectionDialog,
                    approvalDialog
            ));
        } catch (Exception error) {
            if (error instanceof InterruptedException || !isCurrentDeviceLink(attempt)) {
                return;
            }
            postToUi(() -> {
                if (!isCurrentDeviceLink(attempt)) {
                    return;
                }
                if (approvalDialog.isShowing()) approvalDialog.dismiss();
                showError(error.getMessage());
            });
        }
    }

    private synchronized void completeDeviceLink(
            long attempt,
            String token,
            AlertDialog connectionDialog,
            AlertDialog approvalDialog
    ) {
        if (!isCurrentDeviceLink(attempt)) {
            return;
        }
        preferences.edit().putString(TOKEN_KEY, token).apply();
        deviceLinkGeneration++;
        deviceLinkPolling = null;
        approvalDialog.dismiss();
        connectionDialog.dismiss();
        updateConnectionButton();
        toast("put.io connected.");
    }

    private synchronized long startDeviceLinkAttempt() {
        cancelDeviceLink();
        return deviceLinkGeneration;
    }

    private synchronized boolean isCurrentDeviceLink(long attempt) {
        return requestGate.isAlive() && attempt == deviceLinkGeneration;
    }

    private synchronized void cancelDeviceLink(long attempt) {
        if (attempt == deviceLinkGeneration) {
            cancelDeviceLink();
        }
    }

    private synchronized void cancelDeviceLink() {
        deviceLinkGeneration++;
        if (deviceLinkPolling != null) {
            deviceLinkPolling.cancel(true);
            deviceLinkPolling = null;
        }
    }

    private enum ActionState {
        READY,
        ADDING,
        ADDED
    }

    private final class ResultAdapter extends RecyclerView.Adapter<ResultViewHolder> {
        private final List<TorrentResult> results = new ArrayList<>();
        private final Map<String, ActionState> actionStates = new HashMap<>();

        @Override
        public ResultViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ResultViewHolder();
        }

        @Override
        public void onBindViewHolder(ResultViewHolder holder, int position) {
            holder.bind(results.get(position));
        }

        @Override
        public int getItemCount() {
            return results.size();
        }

        void submit(List<TorrentResult> next) {
            results.clear();
            results.addAll(next);
            actionStates.keySet().retainAll(magnets(next));
            notifyDataSetChanged();
        }

        void setActionState(TorrentResult result, ActionState state) {
            actionStates.put(result.magnet, state);
            int index = indexOf(result.magnet);
            if (index >= 0) {
                notifyItemChanged(index);
            }
        }

        String actionLabel(TorrentResult result) {
            return switch (actionStates.getOrDefault(result.magnet, ActionState.READY)) {
                case ADDING -> "Adding…";
                case ADDED -> "Added to put.io";
                case READY -> hasToken() ? "Add to put.io" : "Open in put.io";
            };
        }

        boolean actionEnabled(TorrentResult result) {
            return actionStates.getOrDefault(result.magnet, ActionState.READY)
                    == ActionState.READY;
        }

        ActionState actionState(TorrentResult result) {
            return actionStates.getOrDefault(result.magnet, ActionState.READY);
        }

        private int indexOf(String magnet) {
            for (int i = 0; i < results.size(); i++) {
                if (results.get(i).magnet.equals(magnet)) {
                    return i;
                }
            }
            return -1;
        }

        private List<String> magnets(List<TorrentResult> values) {
            List<String> magnets = new ArrayList<>(values.size());
            for (TorrentResult value : values) {
                magnets.add(value.magnet);
            }
            return magnets;
        }
    }

    private final class ResultViewHolder extends RecyclerView.ViewHolder {
        private final LinearLayout container;
        private final TextView badge;
        private final TextView name;
        private final TextView metadata;
        private final Button add;

        ResultViewHolder() {
            super(new LinearLayout(MainActivity.this));
            container = (LinearLayout) itemView;
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(dp(16), dp(15), dp(16), dp(15));
            container.setBackground(rounded(PARCHMENT, dp(16), Color.rgb(224, 207, 171)));
            container.setElevation(dp(3));
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 0, dp(10));
            container.setLayoutParams(params);

            badge = bodyText("");
            badge.setTextSize(11);
            badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            badge.setTextColor(INK);
            badge.setGravity(Gravity.CENTER);
            badge.setPadding(dp(10), dp(4), dp(10), dp(4));
            badge.setBackground(rounded(GOLD, dp(20), 0));
            container.addView(badge, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            name = bodyText("");
            name.setTextSize(18);
            name.setTextColor(INK);
            name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setPadding(0, dp(9), 0, 0);
            container.addView(name);

            metadata = bodyText("");
            metadata.setTextColor(Color.rgb(68, 77, 84));
            metadata.setTextSize(13);
            metadata.setPadding(0, dp(7), 0, dp(12));
            container.addView(metadata);

            add = primaryButton("");
            container.addView(add, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        }

        void bind(TorrentResult result) {
            badge.setText(result.source.toUpperCase());
            name.setText(result.name);
            metadata.setText(result.metadata());
            add.setText(resultsAdapter.actionLabel(result));
            add.setContentDescription(resultsAdapter.actionLabel(result) + ": " + result.name);
            add.setEnabled(resultsAdapter.actionEnabled(result));
            add.setAlpha(resultsAdapter.actionEnabled(result) ? 1.0f : 0.72f);
            if (resultsAdapter.actionState(result) == ActionState.ADDED) {
                add.setTextColor(Color.WHITE);
                add.setBackground(actionBackground(TEAL, Color.rgb(93, 178, 166)));
            } else {
                add.setTextColor(INK);
                add.setBackground(actionBackground(GOLD, GOLD_PRESSED));
            }
            add.setOnClickListener(ignored -> addTransfer(result));
        }
    }

    private LinearLayout dialogContent() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(16), dp(20), dp(16));
        content.setBackground(rounded(PARCHMENT, dp(16), GOLD));
        return content;
    }

    private void updateConnectionButton() {
        boolean connected = hasToken();
        connectButton.setText(connected ? "put.io  Ready" : "put.io  Connect");
        connectButton.setTextColor(connected ? Color.WHITE : PARCHMENT);
        connectButton.setContentDescription(connected
                ? "put.io connected"
                : "Connect put.io");
        int visibleResults = resultsAdapter.getItemCount();
        if (visibleResults > 0) {
            resultsAdapter.notifyItemRangeChanged(0, visibleResults);
        }
    }

    private boolean hasToken() {
        String token = oauthToken();
        return token != null && !token.trim().isEmpty();
    }

    private String oauthToken() {
        String packaged = BuildConfig.PUTIO_OAUTH_TOKEN.trim();
        return packaged.isEmpty() ? preferences.getString(TOKEN_KEY, "") : packaged;
    }

    private void postToUi(Runnable action) {
        main.post(() -> {
            if (requestGate.isAlive()) {
                action.run();
            }
        });
    }

    private void setBusy(boolean busy, String message) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        searchButton.setEnabled(!busy);
        searchButton.setAlpha(busy ? 0.72f : 1.0f);
        searchButton.setText(busy ? "Searching..." : "Search every source");
        if (busy) {
            status.setTextColor(PARCHMENT_MUTED);
        }
        status.setText(message);
    }

    private Button primaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(INK);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setBackground(actionBackground(GOLD, GOLD_PRESSED));
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setBackground(actionBackground(NAVY_RAISED, Color.rgb(38, 65, 94)));
        return button;
    }

    private Button utilityButton(String text) {
        Button button = secondaryButton(text);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextSize(13);
        button.setMinHeight(dp(48));
        return button;
    }

    private Drawable actionBackground(int normal, int pressed) {
        GradientDrawable content = rounded(normal, dp(12), 0);
        return new RippleDrawable(
                ColorStateList.valueOf(pressed),
                content,
                rounded(Color.WHITE, dp(12), 0)
        );
    }

    private GradientDrawable rounded(int color, int radius, int strokeColor) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(radius);
        if (strokeColor != 0) {
            background.setStroke(dp(1), strokeColor);
        }
        return background;
    }

    private FrameLayout.LayoutParams matchFrame() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
    }

    private TextView bodyText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(15);
        view.setTextColor(Color.rgb(23, 32, 51));
        return view;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void showError(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Could not complete request")
                .setMessage(message == null ? "Unknown error" : message)
                .setPositiveButton("OK", null)
                .show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
