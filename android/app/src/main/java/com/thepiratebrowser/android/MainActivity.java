package com.thepiratebrowser.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String PREFS = "pirate_browser";
    private static final String TOKEN_KEY = "putio.oauth_token";

    private final ExecutorService background = Executors.newCachedThreadPool();
    private final Handler main = new Handler(Looper.getMainLooper());

    private SharedPreferences preferences;
    private TorrentSearchService searchService;
    private final PutIoService putIoService = new PutIoService();
    private EditText queryField;
    private LinearLayout resultsContainer;
    private ProgressBar progress;
    private TextView status;
    private Button searchButton;
    private Button connectButton;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        searchService = new TorrentSearchService(preferences);
        setContentView(buildScreen());
        updateConnectionButton();
    }

    @Override
    protected void onDestroy() {
        background.shutdownNow();
        super.onDestroy();
    }

    private View buildScreen() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.rgb(245, 241, 232));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(12), dp(12), dp(12));
        header.setBackgroundColor(Color.rgb(16, 24, 39));

        TextView title = new TextView(this);
        title.setText("☠  Pirate Browser");
        title.setTextSize(20);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));

        Button sourcesButton = secondaryButton("Sources");
        sourcesButton.setOnClickListener(ignored -> showSources());
        header.addView(sourcesButton);

        connectButton = secondaryButton("Connect put.io");
        connectButton.setOnClickListener(ignored -> showPutIoConnection());
        LinearLayout.LayoutParams connectParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        connectParams.setMarginStart(dp(8));
        header.addView(connectButton, connectParams);
        page.addView(header);

        LinearLayout searchBar = new LinearLayout(this);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setPadding(dp(14), dp(14), dp(14), dp(8));

        queryField = new EditText(this);
        queryField.setHint("Search movies, shows, anime…");
        queryField.setSingleLine();
        queryField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        queryField.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                runSearch();
                return true;
            }
            return false;
        });
        searchBar.addView(queryField, new LinearLayout.LayoutParams(0, dp(56), 1));

        searchButton = primaryButton("Search");
        searchButton.setOnClickListener(ignored -> runSearch());
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48));
        searchParams.setMarginStart(dp(8));
        searchBar.addView(searchButton, searchParams);
        page.addView(searchBar);

        LinearLayout stateRow = new LinearLayout(this);
        stateRow.setGravity(Gravity.CENTER_VERTICAL);
        stateRow.setPadding(dp(18), 0, dp(18), dp(8));
        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        stateRow.addView(progress, new LinearLayout.LayoutParams(dp(28), dp(28)));
        status = new TextView(this);
        status.setText("Search all enabled sources at once.");
        status.setTextColor(Color.rgb(70, 80, 96));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(0, dp(40), 1);
        statusParams.setMarginStart(dp(8));
        stateRow.addView(status, statusParams);
        page.addView(stateRow);

        ScrollView scroll = new ScrollView(this);
        resultsContainer = new LinearLayout(this);
        resultsContainer.setOrientation(LinearLayout.VERTICAL);
        resultsContainer.setPadding(dp(14), 0, dp(14), dp(18));
        scroll.addView(resultsContainer);
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return page;
    }

    private void runSearch() {
        String query = queryField.getText().toString().trim();
        if (query.trim().isEmpty()) {
            queryField.setError("Enter a search");
            return;
        }
        setBusy(true, "Searching enabled sources…");
        resultsContainer.removeAllViews();
        background.execute(() -> {
            TorrentSearchService.SearchOutcome outcome = searchService.search(query);
            main.post(() -> showSearchOutcome(outcome));
        });
    }

    private void showSearchOutcome(TorrentSearchService.SearchOutcome outcome) {
        setBusy(false, outcome.results.size() + " normalized results");
        for (TorrentResult result : outcome.results) {
            resultsContainer.addView(resultRow(result));
        }
        if (outcome.results.isEmpty()) {
            TextView empty = bodyText("No matching torrents found.");
            empty.setPadding(dp(8), dp(20), dp(8), dp(20));
            resultsContainer.addView(empty);
        }
        if (!outcome.failures.isEmpty()) {
            status.setText(status.getText() + " • " + outcome.failures.size() + " source(s) unavailable");
        }
    }

    private View resultRow(TorrentResult result) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(13), dp(16), dp(13));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(12));
        background.setStroke(dp(1), Color.rgb(220, 215, 205));
        row.setBackground(background);

        TextView name = bodyText(result.name);
        name.setTextSize(17);
        name.setTextColor(Color.rgb(23, 32, 51));
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(name);

        TextView metadata = bodyText("Source: " + result.subtitle());
        metadata.setTextColor(Color.rgb(80, 90, 108));
        metadata.setPadding(0, dp(5), 0, dp(8));
        row.addView(metadata);

        boolean directTransfer = hasToken();
        Button add = primaryButton(directTransfer ? "Add directly to put.io" : "Open in put.io");
        add.setOnClickListener(ignored -> addTransfer(result, add));
        row.addView(add, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(10));
        row.setLayoutParams(params);
        return row;
    }

    private void addTransfer(TorrentResult result, Button button) {
        String token = preferences.getString(TOKEN_KEY, "");
        if (token == null || token.trim().isEmpty()) {
            String handoff = "https://put.io/default/magnet?url=" + Uri.encode(result.magnet);
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(handoff)));
            return;
        }
        button.setEnabled(false);
        button.setText("Adding…");
        background.execute(() -> {
            try {
                String message = putIoService.addTransfer(token, result.magnet);
                main.post(() -> {
                    button.setText("Added");
                    toast(message);
                });
            } catch (Exception error) {
                main.post(() -> {
                    button.setEnabled(true);
                    button.setText("Add directly to put.io");
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
                    toast("Torrent sources saved.");
                })
                .show();
    }

    private void showPutIoConnection() {
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
                        preferences.edit().putString(TOKEN_KEY, candidate).apply();
                        main.post(() -> {
                            updateConnectionButton();
                            dialog.dismiss();
                            toast("put.io connected.");
                        });
                    } catch (Exception error) {
                        main.post(() -> {
                            save.setEnabled(true);
                            save.setText("Test & save");
                            token.setError(error.getMessage());
                        });
                    }
                });
            });
        });
        dialog.show();
    }

    private void beginDeviceLink(AlertDialog connectionDialog, Button linkButton) {
        linkButton.setEnabled(false);
        linkButton.setText("Getting code…");
        background.execute(() -> {
            try {
                PutIoService.DeviceCode code =
                        putIoService.requestDeviceCode(BuildConfig.PUTIO_CLIENT_ID);
                main.post(() -> {
                    linkButton.setEnabled(true);
                    linkButton.setText("Link with put.io");
                    AlertDialog approval = new AlertDialog.Builder(this)
                            .setTitle("Enter this code at put.io")
                            .setMessage(code.code + "\n\nThe app will finish connecting after you approve it.")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Open put.io/link", (dialog, which) ->
                                    startActivity(new Intent(Intent.ACTION_VIEW,
                                            Uri.parse("https://put.io/link"))))
                            .create();
                    approval.show();
                    background.execute(() -> finishDeviceLink(code, connectionDialog, approval));
                });
            } catch (Exception error) {
                main.post(() -> {
                    linkButton.setEnabled(true);
                    linkButton.setText("Link with put.io");
                    showError(error.getMessage());
                });
            }
        });
    }

    private void finishDeviceLink(
            PutIoService.DeviceCode code,
            AlertDialog connectionDialog,
            AlertDialog approvalDialog
    ) {
        try {
            String token = putIoService.waitForDeviceToken(code);
            preferences.edit().putString(TOKEN_KEY, token).apply();
            main.post(() -> {
                approvalDialog.dismiss();
                connectionDialog.dismiss();
                updateConnectionButton();
                toast("put.io connected.");
            });
        } catch (Exception error) {
            main.post(() -> {
                if (approvalDialog.isShowing()) approvalDialog.dismiss();
                showError(error.getMessage());
            });
        }
    }

    private LinearLayout dialogContent() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(6), dp(24), 0);
        return content;
    }

    private void updateConnectionButton() {
        connectButton.setText(hasToken() ? "put.io ✓" : "Connect put.io");
    }

    private boolean hasToken() {
        String token = preferences.getString(TOKEN_KEY, "");
        return token != null && !token.trim().isEmpty();
    }

    private void setBusy(boolean busy, String message) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        searchButton.setEnabled(!busy);
        status.setText(message);
    }

    private Button primaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.rgb(23, 32, 51));
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(242, 184, 75));
        background.setCornerRadius(dp(9));
        button.setBackground(background);
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(28, 41, 61));
        background.setCornerRadius(dp(9));
        button.setBackground(background);
        return button;
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
