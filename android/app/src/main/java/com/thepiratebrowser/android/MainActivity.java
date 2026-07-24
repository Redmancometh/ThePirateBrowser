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
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
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

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
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
    private static final String STATE_MIN_SEEDERS = "state.minimum_seeders";
    private static final String STATE_PHASE = "state.phase";
    private static final String STATE_TAB = "state.tab";
    private static final String STATE_PUTIO_TAB = "state.putio_tab";
    private static final String STATE_SEND_KEYS = "state.send_keys";
    private static final String STATE_SEND_VALUES = "state.send_values";
    private static final long SAVED_MONITOR_INTERVAL_MS = 15 * 60 * 1000L;

    private final ExecutorService background = Executors.newCachedThreadPool();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LatestRequestGate requestGate = new LatestRequestGate();
    private final PutIoService putIoService = new PutIoService();
    private final Runnable savedMonitorTick = this::monitorSavedSearches;
    private final Runnable transferRefreshTick = this::loadTransfers;

    private SharedPreferences preferences;
    private TorrentSearchService searchService;
    private SavedSearchStore savedSearchStore;

    private int bg;
    private int surface;
    private int raised;
    private int line;
    private int text;
    private int muted;
    private int gold;
    private int goldDim;
    private int buttonBackground;
    private int buttonForeground;
    private int teal;
    private int coral;
    private int field;
    private int nav;
    private int navInactive;

    private FrameLayout destinations;
    private View searchScreen;
    private View savedScreen;
    private View putIoScreen;
    private View sourcesScreen;
    private final Map<Tab, NavItem> navItems = new HashMap<>();
    private Tab selectedTab = Tab.SEARCH;

    private EditText queryField;
    private TextView minimumSeedersValue;
    private int minimumSeeders;
    private Button searchButton;
    private ProgressBar searchProgress;
    private TextView status;
    private RecyclerView resultsList;
    private ResultAdapter resultsAdapter;
    private View statePanel;
    private TextView stateGlyph;
    private TextView stateTitle;
    private TextView stateBody;
    private Button stateRetry;
    private Phase phase = Phase.IDLE;
    private String lastQuery = "";
    private final List<TorrentResult> allResults = new ArrayList<>();

    private LinearLayout savedList;
    private LinearLayout sourcesList;
    private LinearLayout putIoContent;
    private TextView putIoStatus;
    private boolean putIoTransfersTab = true;
    private long putIoDirectoryId;
    private long putIoParentDirectoryId;
    private String putIoDirectoryName = "Your files";
    private long putIoGeneration;
    private int unseenSavedResults;

    private volatile boolean activityStarted;
    private volatile boolean savedMonitorRunning;
    private volatile long deviceLinkGeneration;
    private volatile Future<?> deviceLinkPolling;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        loadColors();
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        searchService = new TorrentSearchService(preferences);
        savedSearchStore = new SavedSearchStore(preferences);
        setContentView(buildScreen());

        if (state != null) {
            lastQuery = state.getString(STATE_LAST_QUERY, "");
            queryField.setText(state.getString(STATE_QUERY, lastQuery));
            setMinimumSeeders(state.getInt(STATE_MIN_SEEDERS, 0), false);
            phase = enumValue(Phase.class, state.getString(STATE_PHASE), Phase.IDLE);
            selectedTab = enumValue(Tab.class, state.getString(STATE_TAB), Tab.SEARCH);
            putIoTransfersTab = state.getBoolean(STATE_PUTIO_TAB, true);
            ArrayList<String> keys = state.getStringArrayList(STATE_SEND_KEYS);
            ArrayList<String> values = state.getStringArrayList(STATE_SEND_VALUES);
            if (keys != null && values != null) {
                resultsAdapter.restoreActionStates(keys, values);
            }
            if ((phase == Phase.LOADING || phase == Phase.RESULTS) && !lastQuery.isEmpty()) {
                runSearch(lastQuery, null);
            } else {
                renderPhase();
            }
        }
        selectTab(selectedTab);
    }

    @Override
    protected void onStart() {
        super.onStart();
        activityStarted = true;
        main.postDelayed(savedMonitorTick, 30_000);
    }

    @Override
    protected void onStop() {
        activityStarted = false;
        main.removeCallbacks(savedMonitorTick);
        main.removeCallbacks(transferRefreshTick);
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putString(STATE_QUERY, queryField.getText().toString());
        state.putString(STATE_LAST_QUERY, lastQuery);
        state.putInt(STATE_MIN_SEEDERS, minimumSeeders);
        state.putString(STATE_PHASE, phase.name());
        state.putString(STATE_TAB, selectedTab.name());
        state.putBoolean(STATE_PUTIO_TAB, putIoTransfersTab);
        state.putStringArrayList(STATE_SEND_KEYS, resultsAdapter.actionStateKeys());
        state.putStringArrayList(STATE_SEND_VALUES, resultsAdapter.actionStateValues());
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

    private void loadColors() {
        bg = getColor(R.color.pb_bg);
        surface = getColor(R.color.pb_surface);
        raised = getColor(R.color.pb_raised);
        line = getColor(R.color.pb_line);
        text = getColor(R.color.pb_text);
        muted = getColor(R.color.pb_muted);
        gold = getColor(R.color.pb_gold);
        goldDim = getColor(R.color.pb_gold_dim);
        buttonBackground = getColor(R.color.pb_btn_bg);
        buttonForeground = getColor(R.color.pb_btn_fg);
        teal = getColor(R.color.pb_teal);
        coral = getColor(R.color.pb_coral);
        field = getColor(R.color.pb_field);
        nav = getColor(R.color.pb_nav);
        navInactive = getColor(R.color.pb_nav_inactive);
    }

    private View buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.addView(buildMasthead());

        destinations = new FrameLayout(this);
        searchScreen = buildSearchScreen();
        savedScreen = buildSavedScreen();
        putIoScreen = buildPutIoScreen();
        sourcesScreen = buildSourcesScreen();
        destinations.addView(searchScreen, matchFrame());
        destinations.addView(savedScreen, matchFrame());
        destinations.addView(putIoScreen, matchFrame());
        destinations.addView(sourcesScreen, matchFrame());
        root.addView(destinations, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(buildBottomNavigation());
        return root;
    }

    private View buildMasthead() {
        LinearLayout masthead = new LinearLayout(this);
        masthead.setGravity(Gravity.CENTER_VERTICAL);
        masthead.setPadding(dp(16), dp(12), dp(16), dp(14));
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{surface, bg});
        background.setStroke(dp(1), line);
        masthead.setBackground(background);

        ImageView mascot = new ImageView(this);
        mascot.setImageResource(R.drawable.pirate_penguin_art);
        mascot.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        mascot.setContentDescription("Pirate Browser penguin mascot");
        masthead.addView(mascot, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(11), 0, 0, 0);
        TextView title = label("Pirate Browser", 20, text, Typeface.BOLD);
        title.setTypeface(Typeface.SERIF, Typeface.BOLD);
        copy.addView(title);
        TextView tagline = label(getString(R.string.tagline), 12.5f, muted, Typeface.NORMAL);
        copy.addView(tagline);
        masthead.addView(copy, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return masthead;
    }

    private View buildBottomNavigation() {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setBackgroundColor(nav);
        View divider = new View(this);
        divider.setBackgroundColor(line);
        wrapper.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        LinearLayout bar = new LinearLayout(this);
        bar.setPadding(dp(6), dp(8), dp(6), dp(10));
        addNavItem(bar, Tab.SEARCH, "Search", R.drawable.ic_nav_search);
        addNavItem(bar, Tab.SAVED, "Saved", R.drawable.ic_nav_saved);
        addNavItem(bar, Tab.PUTIO, "put.io", R.drawable.ic_nav_putio);
        addNavItem(bar, Tab.SOURCES, "Sources", R.drawable.ic_nav_sources);
        wrapper.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(73)));
        return wrapper;
    }

    private void addNavItem(LinearLayout bar, Tab tab, String title, int iconResource) {
        NavItem item = new NavItem(tab, title, iconResource);
        navItems.put(tab, item);
        bar.addView(item.root, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
    }

    private View buildSearchScreen() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(bg);

        LinearLayout deck = new LinearLayout(this);
        deck.setOrientation(LinearLayout.VERTICAL);
        deck.setPadding(dp(16), dp(16), dp(16), dp(18));
        deck.setBackgroundColor(surface);

        TextView eyebrow = label(getString(R.string.search_eyebrow), 11, gold, Typeface.BOLD);
        eyebrow.setLetterSpacing(0.12f);
        deck.addView(eyebrow);

        LinearLayout searchField = new LinearLayout(this);
        searchField.setGravity(Gravity.CENTER_VERTICAL);
        searchField.setPadding(dp(14), 0, dp(14), 0);
        searchField.setBackground(rounded(field, 14, line, 1.5f));
        ImageView searchIcon = new ImageView(this);
        searchIcon.setImageResource(R.drawable.ic_nav_search);
        searchIcon.setImageTintList(ColorStateList.valueOf(gold));
        searchIcon.setContentDescription(null);
        searchField.addView(searchIcon, new LinearLayout.LayoutParams(dp(20), dp(20)));

        queryField = new EditText(this);
        queryField.setSingleLine();
        queryField.setTextSize(17);
        queryField.setTextColor(text);
        queryField.setHintTextColor(muted);
        queryField.setHint(R.string.search_hint);
        queryField.setBackgroundColor(Color.TRANSPARENT);
        queryField.setPadding(dp(10), 0, 0, 0);
        queryField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        queryField.setOnFocusChangeListener((view, focused) ->
                searchField.setBackground(rounded(field, 14,
                        focused ? gold : line, focused ? 2 : 1.5f)));
        queryField.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                runSearch();
                return true;
            }
            return false;
        });
        searchField.addView(queryField, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        LinearLayout.LayoutParams searchFieldParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        searchFieldParams.setMargins(0, dp(12), 0, dp(12));
        deck.addView(searchField, searchFieldParams);

        LinearLayout seeders = new LinearLayout(this);
        seeders.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout seederCopy = new LinearLayout(this);
        seederCopy.setOrientation(LinearLayout.VERTICAL);
        seederCopy.addView(label(getString(R.string.seeders_label), 13, text, Typeface.BOLD));
        seederCopy.addView(label(getString(R.string.seeders_help), 11.5f, muted, Typeface.NORMAL));
        seeders.addView(seederCopy, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button down = squareButton("−", "Decrease minimum seeders");
        down.setOnClickListener(ignored -> setMinimumSeeders(minimumSeeders - 10, true));
        seeders.addView(down, new LinearLayout.LayoutParams(dp(40), dp(40)));
        minimumSeedersValue = label("0", 16, text, Typeface.BOLD);
        minimumSeedersValue.setGravity(Gravity.CENTER);
        minimumSeedersValue.setBackground(rounded(field, 11, line, 1.5f));
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(dp(52), dp(40));
        valueParams.setMargins(dp(8), 0, dp(8), 0);
        seeders.addView(minimumSeedersValue, valueParams);
        Button up = squareButton("+", "Increase minimum seeders");
        up.setOnClickListener(ignored -> setMinimumSeeders(minimumSeeders + 10, true));
        seeders.addView(up, new LinearLayout.LayoutParams(dp(40), dp(40)));
        deck.addView(seeders);

        searchButton = primaryButton("");
        searchButton.setOnClickListener(ignored -> runSearch());
        LinearLayout.LayoutParams searchButtonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        searchButtonParams.setMargins(0, dp(12), 0, 0);
        deck.addView(searchButton, searchButtonParams);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setPadding(0, dp(9), 0, 0);
        searchProgress = new ProgressBar(this);
        searchProgress.setVisibility(View.GONE);
        searchProgress.setIndeterminateTintList(ColorStateList.valueOf(gold));
        statusRow.addView(searchProgress, new LinearLayout.LayoutParams(dp(18), dp(18)));
        status = label("", 12.5f, muted, Typeface.NORMAL);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        statusParams.setMarginStart(dp(9));
        statusRow.addView(status, statusParams);
        deck.addView(statusRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(29)));
        page.addView(deck);

        FrameLayout resultsArea = new FrameLayout(this);
        resultsList = new RecyclerView(this);
        resultsList.setLayoutManager(new LinearLayoutManager(this));
        resultsList.setClipToPadding(false);
        resultsList.setPadding(dp(14), dp(14), dp(14), dp(24));
        resultsAdapter = new ResultAdapter();
        resultsList.setAdapter(resultsAdapter);
        resultsArea.addView(resultsList, matchFrame());
        statePanel = buildStatePanel();
        FrameLayout.LayoutParams stateParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        stateParams.setMargins(dp(16), dp(20), dp(16), dp(20));
        resultsArea.addView(statePanel, stateParams);
        page.addView(resultsArea, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        updateSearchChrome();
        renderPhase();
        if (getResources().getConfiguration().screenHeightDp >= 600) {
            return page;
        }
        page.removeView(resultsArea);
        page.addView(resultsArea, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(420)));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(page);
        return scroll;
    }

    private View buildStatePanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(24), dp(34), dp(24), dp(34));
        panel.setBackground(rounded(surface, 18, line, 1));

        stateGlyph = label("⚓", 22, gold, Typeface.NORMAL);
        stateGlyph.setGravity(Gravity.CENTER);
        stateGlyph.setBackground(rounded(raised, 16, line, 1.5f));
        panel.addView(stateGlyph, new LinearLayout.LayoutParams(dp(54), dp(54)));

        stateTitle = label("", 20, text, Typeface.BOLD);
        stateTitle.setTypeface(Typeface.SERIF, Typeface.BOLD);
        stateTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, dp(10), 0, 0);
        panel.addView(stateTitle, titleParams);

        stateBody = label("", 14, muted, Typeface.NORMAL);
        stateBody.setGravity(Gravity.CENTER);
        stateBody.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                dp(270), ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyParams.setMargins(0, dp(8), 0, 0);
        panel.addView(stateBody, bodyParams);

        stateRetry = primaryButton("Try again");
        stateRetry.setOnClickListener(ignored -> runSearch());
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(
                dp(150), dp(46));
        retryParams.setMargins(0, dp(18), 0, 0);
        panel.addView(stateRetry, retryParams);
        return panel;
    }

    private View buildSavedScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = screenColumn();
        TextView help = label(getString(R.string.saved_help), 13, muted, Typeface.NORMAL);
        content.addView(help);
        Button add = secondaryButton(getString(R.string.saved_add));
        add.setTextColor(gold);
        add.setBackground(dashed(Color.TRANSPARENT, 13, line));
        add.setOnClickListener(ignored -> showSavedSearchEditor(null, this::renderSavedScreen));
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        addParams.setMargins(0, dp(12), 0, dp(12));
        content.addView(add, addParams);
        savedList = new LinearLayout(this);
        savedList.setOrientation(LinearLayout.VERTICAL);
        content.addView(savedList);
        scroll.addView(content);
        return scroll;
    }

    private View buildSourcesScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = screenColumn();
        content.addView(label(getString(R.string.sources_help), 13, muted, Typeface.NORMAL));
        sourcesList = new LinearLayout(this);
        sourcesList.setOrientation(LinearLayout.VERTICAL);
        sourcesList.setPadding(0, dp(10), 0, 0);
        content.addView(sourcesList);
        TextView note = label(getString(R.string.sources_eztv_note),
                12, muted, Typeface.NORMAL);
        note.setPadding(dp(2), dp(4), dp(2), 0);
        content.addView(note);
        scroll.addView(content);
        return scroll;
    }

    private View buildPutIoScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        putIoContent = new LinearLayout(this);
        putIoContent.setOrientation(LinearLayout.VERTICAL);
        putIoContent.setBackgroundColor(bg);
        scroll.addView(putIoContent);
        return scroll;
    }

    private LinearLayout screenColumn() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(24));
        content.setBackgroundColor(bg);
        return content;
    }

    private void selectTab(Tab tab) {
        selectedTab = tab;
        searchScreen.setVisibility(tab == Tab.SEARCH ? View.VISIBLE : View.GONE);
        savedScreen.setVisibility(tab == Tab.SAVED ? View.VISIBLE : View.GONE);
        putIoScreen.setVisibility(tab == Tab.PUTIO ? View.VISIBLE : View.GONE);
        sourcesScreen.setVisibility(tab == Tab.SOURCES ? View.VISIBLE : View.GONE);
        for (NavItem item : navItems.values()) {
            item.setActive(item.tab == tab);
        }
        main.removeCallbacks(transferRefreshTick);
        putIoGeneration++;
        if (tab == Tab.SAVED) {
            unseenSavedResults = 0;
            renderSavedScreen();
        } else if (tab == Tab.SOURCES) {
            renderSourcesScreen();
        } else if (tab == Tab.PUTIO) {
            renderPutIoScreen();
        }
        updateNavBadges();
    }

    private void runSearch() {
        runSearch(queryField.getText().toString(), null);
    }

    private void runSearch(String requestedQuery, SavedSearch savedSearch) {
        String query = requestedQuery.trim();
        if (query.isEmpty()) {
            queryField.setError("Enter a search");
            return;
        }
        if (enabledSourceCount() == 0) {
            phase = Phase.NO_SOURCES;
            status.setTextColor(coral);
            status.setText(R.string.status_no_sources);
            renderPhase();
            return;
        }
        lastQuery = query;
        if (savedSearch != null) {
            setMinimumSeeders(savedSearch.minimumSeeders, false);
        }
        LatestRequestGate.Ticket ticket = requestGate.begin(query);
        phase = Phase.LOADING;
        allResults.clear();
        resultsAdapter.submit(Collections.emptyList());
        setSearchBusy(true);
        renderPhase();
        status.setText(getString(R.string.status_searching, enabledSourceCount(), query));
        hideKeyboard();
        selectTab(Tab.SEARCH);

        background.execute(() -> {
            TorrentSearchService.SearchOutcome outcome = searchService.search(query);
            if (savedSearch != null) {
                savedSearch.record(outcome.results, System.currentTimeMillis());
                savedSearchStore.upsert(savedSearch);
            }
            postToUi(() -> {
                if (requestGate.accept(ticket)) {
                    showSearchOutcome(query, outcome);
                }
            });
        });
    }

    private void showSearchOutcome(
            String query,
            TorrentSearchService.SearchOutcome outcome
    ) {
        allResults.clear();
        allResults.addAll(outcome.results);
        List<TorrentResult> filtered = filteredResults();
        resultsAdapter.submit(filtered);
        setSearchBusy(false);
        if (filtered.isEmpty()) {
            phase = outcome.failures.isEmpty() ? Phase.NO_RESULTS : Phase.ERROR;
        } else {
            phase = Phase.RESULTS;
        }
        if (!filtered.isEmpty() && !outcome.failures.isEmpty()) {
            int failures = outcome.failures.size();
            status.setText(filtered.size() + " results · " + failures + " source"
                    + (failures == 1 ? " didn’t" : "s didn’t") + " answer");
            status.setTextColor(coral);
        } else if (!filtered.isEmpty()) {
            status.setText(getString(R.string.status_found, filtered.size()));
            status.setTextColor(muted);
        } else if (phase == Phase.ERROR) {
            status.setText(R.string.panel_error_body);
            status.setTextColor(coral);
        } else {
            status.setText("Nothing matched “" + query + "”.");
            status.setTextColor(muted);
        }
        renderPhase();
    }

    private List<TorrentResult> filteredResults() {
        List<TorrentResult> filtered = new ArrayList<>();
        for (TorrentResult result : allResults) {
            if (result.seeders >= minimumSeeders) {
                filtered.add(result);
            }
        }
        return filtered;
    }

    private void setMinimumSeeders(int value, boolean refilter) {
        minimumSeeders = Math.max(0, value);
        if (minimumSeedersValue != null) {
            minimumSeedersValue.setText(String.valueOf(minimumSeeders));
        }
        if (refilter && !allResults.isEmpty()) {
            List<TorrentResult> filtered = filteredResults();
            resultsAdapter.submit(filtered);
            phase = filtered.isEmpty() ? Phase.NO_RESULTS : Phase.RESULTS;
            status.setText(filtered.isEmpty()
                    ? "Nothing matches this seeder threshold."
                    : getString(R.string.status_found, filtered.size()));
            renderPhase();
        }
    }

    private void setSearchBusy(boolean busy) {
        searchProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
        searchButton.setEnabled(!busy);
        searchButton.setAlpha(busy ? 0.65f : 1f);
        updateSearchChrome();
    }

    private void updateSearchChrome() {
        int count = enabledSourceCount();
        if (searchButton != null) {
            searchButton.setText(count == 1 ? "Search 1 source" : "Search " + count + " sources");
        }
        if (status != null && phase == Phase.IDLE) {
            status.setText(getString(R.string.status_ready, count));
            status.setTextColor(muted);
        }
    }

    private void renderPhase() {
        boolean results = phase == Phase.RESULTS && resultsAdapter.getItemCount() > 0;
        resultsList.setVisibility(results ? View.VISIBLE : View.GONE);
        statePanel.setVisibility(results ? View.GONE : View.VISIBLE);
        stateRetry.setVisibility(View.GONE);
        switch (phase) {
            case IDLE, LOADING -> {
                stateGlyph.setText("⚓");
                stateTitle.setText(R.string.panel_idle_title);
                stateBody.setText(R.string.panel_idle_body);
            }
            case NO_RESULTS -> {
                stateGlyph.setText("○");
                stateTitle.setText(R.string.panel_none_title);
                stateBody.setText("Nothing matched “" + lastQuery
                        + "”. Try fewer words or a shorter title.");
                stateRetry.setVisibility(View.VISIBLE);
            }
            case ERROR -> {
                stateGlyph.setText("!");
                stateTitle.setText(R.string.panel_error_title);
                stateBody.setText(R.string.panel_error_body);
                stateRetry.setVisibility(View.VISIBLE);
            }
            case NO_SOURCES -> {
                stateGlyph.setText("!");
                stateTitle.setText(R.string.panel_nosrc_title);
                stateBody.setText(R.string.panel_nosrc_body);
            }
            case RESULTS -> {
                // The result list is visible.
            }
        }
    }

    private void addTransfer(TorrentResult result) {
        if (!hasToken()) {
            String handoff = "https://put.io/default/magnet?url=" + Uri.encode(result.magnet);
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(handoff)));
            status.setText(R.string.status_handoff);
            status.setTextColor(muted);
            return;
        }
        resultsAdapter.setActionState(result, SendState.SENDING);
        background.execute(() -> {
            try {
                putIoService.addTransfer(oauthToken(), result.magnet);
                postToUi(() -> resultsAdapter.setActionState(result, SendState.SENT));
            } catch (Exception error) {
                postToUi(() -> {
                    resultsAdapter.setActionState(result, SendState.IDLE);
                    showError(error.getMessage());
                });
            }
        });
    }

    private void renderSavedScreen() {
        if (savedList == null) {
            return;
        }
        savedList.removeAllViews();
        List<SavedSearch> searches = savedSearchStore.all();
        if (searches.isEmpty()) {
            TextView empty = label(getString(R.string.saved_empty), 13, muted, Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(8), dp(28), dp(8), dp(28));
            savedList.addView(empty);
            return;
        }
        for (SavedSearch search : searches) {
            savedList.addView(savedSearchCard(search));
        }
    }

    private View savedSearchCard(SavedSearch search) {
        LinearLayout card = card();
        card.setOnClickListener(ignored -> showSavedSearchEditor(search, this::renderSavedScreen));
        card.setContentDescription("Edit saved search " + search.displayName());

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.TOP);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(label(search.displayName(), 16, text, Typeface.BOLD));
        copy.addView(label("“" + search.query + "” · min "
                + search.minimumSeeders + " seeders", 12.5f, muted, Typeface.NORMAL));
        header.addView(copy, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView state = label(search.enabled ? "WATCHING" : "PAUSED",
                11, search.enabled ? teal : muted, Typeface.BOLD);
        state.setLetterSpacing(0.06f);
        state.setGravity(Gravity.CENTER);
        state.setBackground(rounded(Color.TRANSPARENT, 7,
                search.enabled ? teal : line, 1));
        header.addView(state, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(30)));
        card.addView(header);

        String checked = search.lastChecked == 0
                ? getString(R.string.saved_baseline)
                : "Checked " + DateFormat.getTimeInstance(DateFormat.SHORT)
                .format(new Date(search.lastChecked));
        TextView checkedView = label(checked, 12.5f, muted, Typeface.NORMAL);
        checkedView.setPadding(0, dp(10), 0, dp(10));
        card.addView(checkedView);

        LinearLayout actions = new LinearLayout(this);
        Button run = primaryButton("Run now");
        run.setOnClickListener(ignored -> {
            queryField.setText(search.query);
            runSearch(search.query, search);
        });
        actions.addView(run, new LinearLayout.LayoutParams(0, dp(44), 1));
        Button toggle = secondaryButton(search.enabled ? "Pause" : "Resume");
        toggle.setOnClickListener(ignored -> {
            search.enabled = !search.enabled;
            savedSearchStore.upsert(search);
            renderSavedScreen();
            updateNavBadges();
        });
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(dp(96), dp(44));
        toggleParams.setMarginStart(dp(8));
        actions.addView(toggle, toggleParams);
        Button delete = secondaryButton("Del");
        delete.setTextColor(coral);
        delete.setOnClickListener(ignored -> confirmDeleteSavedSearch(search));
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(dp(52), dp(44));
        deleteParams.setMarginStart(dp(8));
        actions.addView(delete, deleteParams);
        card.addView(actions);
        return card;
    }

    private void showSavedSearchEditor(SavedSearch existing, Runnable onSaved) {
        LinearLayout content = dialogColumn();
        EditText name = dialogField("Display name (optional)",
                existing == null ? "" : existing.name, InputType.TYPE_CLASS_TEXT);
        content.addView(name, fieldParams());
        EditText query = dialogField("Search query",
                existing == null ? queryField.getText().toString() : existing.query,
                InputType.TYPE_CLASS_TEXT);
        content.addView(query, fieldParams());
        EditText seeders = dialogField("Minimum seeders",
                String.valueOf(existing == null ? minimumSeeders : existing.minimumSeeders),
                InputType.TYPE_CLASS_NUMBER);
        content.addView(seeders, fieldParams());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Add saved search" : "Edit saved search")
                .setView(content)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                    String queryValue = query.getText().toString().trim();
                    if (queryValue.isEmpty()) {
                        query.setError("Enter a search query");
                        return;
                    }
                    int threshold;
                    try {
                        threshold = Integer.parseInt(seeders.getText().toString().trim());
                    } catch (Exception invalid) {
                        seeders.setError("Enter a whole number");
                        return;
                    }
                    SavedSearch saved = existing == null
                            ? new SavedSearch(name.getText().toString().trim(),
                            queryValue, threshold)
                            : existing;
                    if (existing != null && (!existing.query.equals(queryValue)
                            || existing.minimumSeeders != threshold)) {
                        saved.seenResultIds.clear();
                        saved.lastChecked = 0;
                    }
                    saved.name = name.getText().toString().trim();
                    saved.query = queryValue;
                    saved.minimumSeeders = Math.max(0, threshold);
                    savedSearchStore.upsert(saved);
                    dialog.dismiss();
                    onSaved.run();
                    updateNavBadges();
                }));
        dialog.show();
    }

    private void confirmDeleteSavedSearch(SavedSearch search) {
        new AlertDialog.Builder(this)
                .setTitle("Delete saved search?")
                .setMessage("Remove “" + search.displayName() + "”?")
                .setNegativeButton("Keep", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    savedSearchStore.remove(search.id);
                    renderSavedScreen();
                    updateNavBadges();
                })
                .show();
    }

    private void renderSourcesScreen() {
        if (sourcesList == null) {
            return;
        }
        sourcesList.removeAllViews();
        for (String source : TorrentSearchService.SOURCES) {
            sourcesList.addView(sourceCard(source));
        }
    }

    private View sourceCard(String sourceName) {
        boolean enabled = searchService.enabled(sourceName);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));
        row.setBackground(rounded(surface, 16, line, 1));
        row.setContentDescription(sourceName + ", " + (enabled ? "enabled" : "disabled"));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(label(sourceName, 15.5f, text, Typeface.BOLD));
        copy.addView(label(sourceNote(sourceName), 12.5f, muted, Typeface.NORMAL));
        row.addView(copy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        FrameLayout toggle = new FrameLayout(this);
        toggle.setBackground(rounded(enabled ? gold : raised, 99,
                enabled ? gold : line, 1));
        View knob = new View(this);
        knob.setBackground(rounded(enabled ? buttonForeground : muted, 99, Color.TRANSPARENT, 0));
        FrameLayout.LayoutParams knobParams = new FrameLayout.LayoutParams(dp(25), dp(25),
                enabled ? Gravity.END | Gravity.CENTER_VERTICAL
                        : Gravity.START | Gravity.CENTER_VERTICAL);
        knobParams.setMargins(dp(3), 0, dp(3), 0);
        toggle.addView(knob, knobParams);
        row.addView(toggle, new LinearLayout.LayoutParams(dp(52), dp(31)));

        row.setOnClickListener(ignored -> {
            float target = enabled ? -dp(21) : dp(21);
            knob.animate().translationX(target).setDuration(150).withEndAction(() -> {
                searchService.setEnabled(sourceName, !enabled);
                renderSourcesScreen();
                updateSearchChrome();
            }).start();
        });
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 0, 0, dp(10));
        row.setLayoutParams(rowParams);
        return row;
    }

    private String sourceNote(String sourceName) {
        if (TorrentSearchService.SOURCE_TPB.equals(sourceName)) {
            return "Broadest catalogue. Best for films and older releases.";
        }
        if (TorrentSearchService.SOURCE_NYAA.equals(sourceName)) {
            return "Anime and Asian media, usually well seeded.";
        }
        if (TorrentSearchService.SOURCE_EZTV.equals(sourceName)) {
            return "TV episodes from the latest 100 releases.";
        }
        if (TorrentSearchService.SOURCE_YTS.equals(sourceName)) {
            return "Small, tidy film encodes.";
        }
        if (TorrentSearchService.SOURCE_KNABEN.equals(sourceName)) {
            return "Broad torrent index with strong availability data.";
        }
        if (TorrentSearchService.SOURCE_MAGNETZ.equals(sourceName)) {
            return "Fast magnet search across general releases.";
        }
        return "Broad catalogue backed by an open torrent database.";
    }

    private void renderPutIoScreen() {
        putIoGeneration++;
        main.removeCallbacks(transferRefreshTick);
        putIoContent.removeAllViews();
        if (!hasToken()) {
            renderPutIoConnection();
            return;
        }
        LinearLayout tabs = new LinearLayout(this);
        tabs.setPadding(dp(16), dp(14), dp(16), dp(14));
        tabs.setBackgroundColor(surface);
        Button transfers = segmentButton("Transfers", putIoTransfersTab);
        transfers.setOnClickListener(ignored -> {
            putIoTransfersTab = true;
            renderPutIoScreen();
        });
        tabs.addView(transfers, new LinearLayout.LayoutParams(0, dp(44), 1));
        Button files = segmentButton("Files", !putIoTransfersTab);
        files.setOnClickListener(ignored -> {
            putIoTransfersTab = false;
            renderPutIoScreen();
        });
        LinearLayout.LayoutParams fileParams = new LinearLayout.LayoutParams(0, dp(44), 1);
        fileParams.setMarginStart(dp(8));
        tabs.addView(files, fileParams);
        putIoContent.addView(tabs);
        View divider = new View(this);
        divider.setBackgroundColor(line);
        putIoContent.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        putIoStatus = label("", 12.5f, muted, Typeface.NORMAL);
        putIoStatus.setPadding(dp(16), dp(12), dp(16), 0);
        putIoStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        putIoContent.addView(putIoStatus);

        if (putIoTransfersTab) {
            loadTransfers();
        } else {
            loadFiles(putIoDirectoryId);
        }
        Button disconnect = secondaryButton("Disconnect put.io");
        disconnect.setTextColor(coral);
        disconnect.setOnClickListener(ignored -> confirmDisconnect());
        LinearLayout.LayoutParams disconnectParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        disconnectParams.setMargins(dp(16), dp(4), dp(16), dp(24));
        putIoContent.addView(disconnect, disconnectParams);
    }

    private void renderPutIoConnection() {
        LinearLayout content = screenColumn();
        LinearLayout tokenCard = card();
        TextView eyebrow = label(getString(R.string.putio_token_eyebrow),
                11, gold, Typeface.BOLD);
        eyebrow.setLetterSpacing(0.12f);
        tokenCard.addView(eyebrow);
        TextView tokenBody = label(getString(R.string.putio_token_body),
                13.5f, muted, Typeface.NORMAL);
        tokenBody.setPadding(0, dp(9), 0, dp(10));
        tokenCard.addView(tokenBody);
        EditText tokenField = dialogField(getString(R.string.putio_token_hint),
                "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tokenCard.addView(tokenField, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        Button save = primaryButton(getString(R.string.putio_token_save));
        save.setOnClickListener(ignored -> saveToken(tokenField, save));
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        saveParams.setMargins(0, dp(10), 0, 0);
        tokenCard.addView(save, saveParams);
        content.addView(tokenCard);

        if (!BuildConfig.PUTIO_CLIENT_ID.trim().isEmpty()) {
            LinearLayout wizardCard = card();
            TextView wizardTitle = label(getString(R.string.putio_link_title),
                    19, text, Typeface.BOLD);
            wizardTitle.setTypeface(Typeface.SERIF, Typeface.BOLD);
            wizardCard.addView(wizardTitle);
            TextView wizardBody = label(getString(R.string.putio_link_body),
                    13.5f, muted, Typeface.NORMAL);
            wizardBody.setPadding(0, dp(9), 0, dp(10));
            wizardCard.addView(wizardBody);
            Button wizard = secondaryButton(getString(R.string.putio_link_button));
            wizard.setTextColor(text);
            wizard.setOnClickListener(ignored -> beginDeviceLink(wizard));
            wizardCard.addView(wizard, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
            content.addView(wizardCard);
        }
        putIoContent.addView(content);
    }

    private void saveToken(EditText tokenField, Button saveButton) {
        String candidate = tokenField.getText().toString().trim();
        if (candidate.isEmpty()) {
            tokenField.setError("Paste an OAuth token");
            return;
        }
        saveButton.setEnabled(false);
        saveButton.setText("Testing…");
        background.execute(() -> {
            try {
                putIoService.verifyToken(candidate);
                postToUi(() -> {
                    preferences.edit().putString(TOKEN_KEY, candidate).apply();
                    toast("put.io connected.");
                    renderPutIoScreen();
                    updateNavBadges();
                });
            } catch (Exception error) {
                postToUi(() -> {
                    saveButton.setEnabled(true);
                    saveButton.setText(R.string.putio_token_save);
                    tokenField.setError(error.getMessage());
                });
            }
        });
    }

    private void beginDeviceLink(Button linkButton) {
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
                    linkButton.setText(R.string.putio_link_button);
                    AlertDialog approval = new AlertDialog.Builder(this)
                            .setTitle(R.string.putio_code_title)
                            .setMessage("Approve " + code.code
                                    + " at put.io/link. This screen finishes connecting on its own.")
                            .setNegativeButton("Cancel",
                                    (dialog, which) -> cancelDeviceLink(attempt))
                            .setPositiveButton("Open put.io/link", (dialog, which) ->
                                    startActivity(new Intent(Intent.ACTION_VIEW,
                                            Uri.parse("https://put.io/link"))))
                            .create();
                    approval.setOnCancelListener(ignored -> cancelDeviceLink(attempt));
                    approval.show();
                    deviceLinkPolling = background.submit(
                            () -> finishDeviceLink(attempt, code, approval));
                });
            } catch (Exception error) {
                postToUi(() -> {
                    if (isCurrentDeviceLink(attempt)) {
                        linkButton.setEnabled(true);
                        linkButton.setText(R.string.putio_link_button);
                        showError(error.getMessage());
                    }
                });
            }
        });
    }

    private void finishDeviceLink(
            long attempt,
            PutIoService.DeviceCode code,
            AlertDialog approval
    ) {
        try {
            String token = putIoService.waitForDeviceToken(code);
            postToUi(() -> {
                if (!isCurrentDeviceLink(attempt)) {
                    return;
                }
                preferences.edit().putString(TOKEN_KEY, token).apply();
                deviceLinkGeneration++;
                deviceLinkPolling = null;
                approval.dismiss();
                renderPutIoScreen();
                updateNavBadges();
                toast("put.io connected.");
            });
        } catch (Exception error) {
            if (error instanceof InterruptedException || !isCurrentDeviceLink(attempt)) {
                return;
            }
            postToUi(() -> {
                if (isCurrentDeviceLink(attempt)) {
                    approval.dismiss();
                    showError(error.getMessage());
                }
            });
        }
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

    private void loadTransfers() {
        if (selectedTab != Tab.PUTIO || !putIoTransfersTab || !hasToken()) {
            return;
        }
        long generation = ++putIoGeneration;
        main.removeCallbacks(transferRefreshTick);
        if (putIoStatus != null) {
            putIoStatus.setText("Loading transfers…");
        }
        background.execute(() -> {
            try {
                List<PutIoService.Transfer> transfers = putIoService.transfers(oauthToken());
                postToUi(() -> {
                    if (generation == putIoGeneration && selectedTab == Tab.PUTIO
                            && putIoTransfersTab) {
                        renderTransfers(transfers);
                    }
                });
            } catch (Exception error) {
                postToUi(() -> renderPutIoError(generation,
                        "Couldn’t load transfers", error.getMessage(), this::loadTransfers));
            }
        });
    }

    private void renderTransfers(List<PutIoService.Transfer> transfers) {
        removePutIoDynamicRows();
        putIoStatus.setText(transfers.size() + " transfer"
                + (transfers.size() == 1 ? "" : "s"));
        LinearLayout list = dynamicPutIoList();
        boolean active = false;
        if (transfers.isEmpty()) {
            list.addView(emptyMessage("No transfers yet."));
        }
        for (PutIoService.Transfer transfer : transfers) {
            list.addView(transferCard(transfer));
            active |= !transfer.isDone();
        }
        insertBeforeDisconnect(list);
        NavItem putio = navItems.get(Tab.PUTIO);
        putio.setDot(active, teal);
        if (active) {
            putIoStatus.setText(transfers.size() + " transfers · updating automatically");
            main.postDelayed(transferRefreshTick, 3_000);
        }
    }

    private View transferCard(PutIoService.Transfer transfer) {
        LinearLayout card = card();
        card.addView(label(transfer.name, 14.5f, text, Typeface.BOLD));
        ProgressBar progress = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(Math.max(0, Math.min(100, transfer.percentDone)));
        progress.setProgressTintList(ColorStateList.valueOf(gold));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(raised));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(8));
        progressParams.setMargins(0, dp(9), 0, dp(9));
        card.addView(progress, progressParams);

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        String detail = transfer.errorMessage.trim().isEmpty()
                ? transfer.status : transfer.errorMessage;
        footer.addView(label(detail, 12.5f,
                transfer.errorMessage.trim().isEmpty() ? muted : coral,
                Typeface.NORMAL), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        String state = transfer.isDone() ? "Done" : transfer.percentDone + "%";
        footer.addView(label(state, 12.5f, teal, Typeface.BOLD));
        card.addView(footer);

        Button action = secondaryButton(transfer.isDone() ? "Remove" : "Cancel");
        action.setTextColor(transfer.isDone() ? muted : coral);
        action.setOnClickListener(ignored -> confirmTransferCancellation(transfer));
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        actionParams.setMargins(0, dp(10), 0, 0);
        card.addView(action, actionParams);
        return card;
    }

    private void confirmTransferCancellation(PutIoService.Transfer transfer) {
        String verb = transfer.isDone() ? "Remove" : "Cancel";
        new AlertDialog.Builder(this)
                .setTitle(verb + " transfer?")
                .setMessage(verb + " “" + transfer.name + "”? Downloaded files are not deleted.")
                .setNegativeButton("Keep", null)
                .setPositiveButton(verb, (dialog, which) -> background.execute(() -> {
                    try {
                        putIoService.cancelTransfer(oauthToken(), transfer.id);
                        postToUi(this::loadTransfers);
                    } catch (Exception error) {
                        postToUi(() -> showError(error.getMessage()));
                    }
                }))
                .show();
    }

    private void loadFiles(long directoryId) {
        if (selectedTab != Tab.PUTIO || putIoTransfersTab || !hasToken()) {
            return;
        }
        long generation = ++putIoGeneration;
        if (putIoStatus != null) {
            putIoStatus.setText("Loading files…");
        }
        background.execute(() -> {
            try {
                PutIoService.FileListing listing =
                        putIoService.files(oauthToken(), directoryId);
                postToUi(() -> {
                    if (generation == putIoGeneration && selectedTab == Tab.PUTIO
                            && !putIoTransfersTab) {
                        renderFiles(listing);
                    }
                });
            } catch (Exception error) {
                postToUi(() -> renderPutIoError(generation,
                        "Couldn’t load files", error.getMessage(),
                        () -> loadFiles(putIoDirectoryId)));
            }
        });
    }

    private void renderFiles(PutIoService.FileListing listing) {
        putIoDirectoryId = listing.directoryId;
        putIoParentDirectoryId = listing.parentDirectoryId;
        putIoDirectoryName = listing.directoryName;
        removePutIoDynamicRows();
        putIoStatus.setText(listing.files.size() + " item"
                + (listing.files.size() == 1 ? "" : "s"));
        LinearLayout list = dynamicPutIoList();

        LinearLayout breadcrumb = new LinearLayout(this);
        breadcrumb.setGravity(Gravity.CENTER_VERTICAL);
        Button up = secondaryButton("Up");
        up.setEnabled(putIoDirectoryId != 0);
        up.setAlpha(putIoDirectoryId == 0 ? 0.5f : 1);
        up.setOnClickListener(ignored -> loadFiles(putIoParentDirectoryId));
        breadcrumb.addView(up, new LinearLayout.LayoutParams(dp(64), dp(44)));
        TextView directory = label(putIoDirectoryId == 0 ? "Your files" : putIoDirectoryName,
                12.5f, gold, Typeface.BOLD);
        directory.setPadding(dp(10), 0, 0, 0);
        breadcrumb.addView(directory, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button refresh = secondaryButton("Refresh");
        refresh.setOnClickListener(ignored -> loadFiles(putIoDirectoryId));
        breadcrumb.addView(refresh, new LinearLayout.LayoutParams(dp(88), dp(44)));
        list.addView(breadcrumb);

        if (listing.files.isEmpty()) {
            list.addView(emptyMessage("This folder is empty."));
        }
        for (PutIoService.FileItem fileItem : listing.files) {
            list.addView(fileRow(fileItem));
        }
        insertBeforeDisconnect(list);
    }

    private View fileRow(PutIoService.FileItem fileItem) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(13), dp(12), dp(13), dp(12));
        row.setBackground(rounded(surface, 14, line, 1));

        TextView glyph = label(fileItem.isDirectory() ? "▸" : "▪",
                15, gold, Typeface.NORMAL);
        glyph.setGravity(Gravity.CENTER);
        glyph.setBackground(rounded(raised, 10, line, 1));
        row.addView(glyph, new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, 0, 0);
        TextView title = label(fileItem.name, 14.5f, text, Typeface.BOLD);
        title.setSingleLine();
        title.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(title);
        String meta = fileItem.isDirectory() ? "Folder"
                : TorrentResult.readableSize(fileItem.size) + " · " + fileItem.contentType;
        copy.addView(label(meta, 12, muted, Typeface.NORMAL));
        row.addView(copy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(label("›", 18, muted, Typeface.NORMAL));
        row.setOnClickListener(ignored -> {
            if (fileItem.isDirectory()) {
                loadFiles(fileItem.id);
            } else {
                showFileActions(fileItem);
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(params);
        return row;
    }

    private void showFileActions(PutIoService.FileItem fileItem) {
        List<String> labels = new ArrayList<>();
        labels.add(fileItem.isVideo() ? "Play" : "Open");
        if (fileItem.isVideo()) {
            labels.add("Share / Cast");
        }
        labels.add("Rename");
        labels.add("Delete");
        new AlertDialog.Builder(this)
                .setTitle(fileItem.name)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    String action = labels.get(which);
                    if ("Play".equals(action) || "Open".equals(action)) {
                        openPutIoFile(fileItem);
                    } else if ("Share / Cast".equals(action)) {
                        sharePutIoFile(fileItem);
                    } else if ("Rename".equals(action)) {
                        renamePutIoFile(fileItem);
                    } else {
                        deletePutIoFile(fileItem);
                    }
                })
                .show();
    }

    private void openPutIoFile(PutIoService.FileItem fileItem) {
        try {
            String address = fileItem.isVideo()
                    ? putIoService.hlsStreamUrl(oauthToken(), fileItem.id)
                    : putIoService.downloadUrl(oauthToken(), fileItem.id);
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(address));
            if (fileItem.isVideo()) {
                intent.setDataAndType(Uri.parse(address), "video/*");
            }
            startActivity(intent);
        } catch (Exception error) {
            showError(error.getMessage());
        }
    }

    private void sharePutIoFile(PutIoService.FileItem fileItem) {
        try {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_SUBJECT, fileItem.name);
            share.putExtra(Intent.EXTRA_TEXT,
                    putIoService.hlsStreamUrl(oauthToken(), fileItem.id));
            startActivity(Intent.createChooser(share, "Share or cast video"));
        } catch (Exception error) {
            showError(error.getMessage());
        }
    }

    private void renamePutIoFile(PutIoService.FileItem fileItem) {
        EditText name = dialogField("File name", fileItem.name, InputType.TYPE_CLASS_TEXT);
        name.setSelectAllOnFocus(true);
        LinearLayout content = dialogColumn();
        content.addView(name, fieldParams());
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Rename")
                .setView(content)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                    String updated = name.getText().toString().trim();
                    if (updated.isEmpty()) {
                        name.setError("Enter a file name");
                        return;
                    }
                    dialog.dismiss();
                    background.execute(() -> {
                        try {
                            putIoService.renameFile(oauthToken(), fileItem.id, updated);
                            postToUi(() -> loadFiles(putIoDirectoryId));
                        } catch (Exception error) {
                            postToUi(() -> showError(error.getMessage()));
                        }
                    });
                }));
        dialog.show();
    }

    private void deletePutIoFile(PutIoService.FileItem fileItem) {
        new AlertDialog.Builder(this)
                .setTitle("Delete file?")
                .setMessage("Permanently delete “" + fileItem.name + "” from put.io?")
                .setNegativeButton("Keep", null)
                .setPositiveButton("Delete", (dialog, which) -> background.execute(() -> {
                    try {
                        putIoService.deleteFile(oauthToken(), fileItem.id);
                        postToUi(() -> loadFiles(putIoDirectoryId));
                    } catch (Exception error) {
                        postToUi(() -> showError(error.getMessage()));
                    }
                }))
                .show();
    }

    private void renderPutIoError(
            long generation,
            String title,
            String message,
            Runnable retry
    ) {
        if (generation != putIoGeneration || selectedTab != Tab.PUTIO) {
            return;
        }
        removePutIoDynamicRows();
        putIoStatus.setTextColor(coral);
        putIoStatus.setText(title);
        LinearLayout list = dynamicPutIoList();
        list.addView(emptyMessage(message == null ? title : message));
        Button button = primaryButton("Try again");
        button.setOnClickListener(ignored -> {
            putIoStatus.setTextColor(muted);
            retry.run();
        });
        list.addView(button, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        insertBeforeDisconnect(list);
    }

    private void removePutIoDynamicRows() {
        while (putIoContent.getChildCount() > 4) {
            putIoContent.removeViewAt(3);
        }
    }

    private LinearLayout dynamicPutIoList() {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(12), dp(16), dp(14));
        return list;
    }

    private void insertBeforeDisconnect(View view) {
        int index = Math.max(0, putIoContent.getChildCount() - 1);
        putIoContent.addView(view, index);
    }

    private TextView emptyMessage(String message) {
        TextView empty = label(message, 13, muted, Typeface.NORMAL);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(12), dp(28), dp(12), dp(28));
        return empty;
    }

    private void confirmDisconnect() {
        new AlertDialog.Builder(this)
                .setTitle("Disconnect put.io?")
                .setMessage("Remove the saved OAuth token from this device?")
                .setNegativeButton("Keep connected", null)
                .setPositiveButton("Disconnect", (dialog, which) -> {
                    preferences.edit().remove(TOKEN_KEY).apply();
                    putIoDirectoryId = 0;
                    renderPutIoScreen();
                    updateNavBadges();
                })
                .show();
    }

    private void monitorSavedSearches() {
        if (!activityStarted || savedMonitorRunning) {
            return;
        }
        List<SavedSearch> enabled = new ArrayList<>();
        for (SavedSearch search : savedSearchStore.all()) {
            if (search.enabled) {
                enabled.add(search);
            }
        }
        if (enabled.isEmpty()) {
            main.postDelayed(savedMonitorTick, SAVED_MONITOR_INTERVAL_MS);
            return;
        }
        savedMonitorRunning = true;
        background.execute(() -> {
            int found = 0;
            try {
                for (SavedSearch search : enabled) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    TorrentSearchService.SearchOutcome outcome = searchService.search(search.query);
                    if (!outcome.results.isEmpty() || outcome.failures.isEmpty()) {
                        found += search.record(outcome.results, System.currentTimeMillis());
                        savedSearchStore.upsert(search);
                    }
                }
            } finally {
                int newResults = found;
                savedMonitorRunning = false;
                postToUi(() -> {
                    if (newResults > 0) {
                        unseenSavedResults += newResults;
                        toast(newResults + " new result"
                                + (newResults == 1 ? "" : "s")
                                + " across saved searches.");
                    }
                    updateNavBadges();
                    if (selectedTab == Tab.SAVED) {
                        renderSavedScreen();
                    }
                    if (activityStarted) {
                        main.postDelayed(savedMonitorTick, SAVED_MONITOR_INTERVAL_MS);
                    }
                });
            }
        });
    }

    private void updateNavBadges() {
        NavItem saved = navItems.get(Tab.SAVED);
        if (saved != null) {
            saved.setDot(unseenSavedResults > 0, gold);
            saved.root.setContentDescription("Saved searches tab"
                    + (unseenSavedResults > 0 ? ", " + unseenSavedResults + " new results" : ""));
        }
        NavItem putio = navItems.get(Tab.PUTIO);
        if (putio != null && !hasToken()) {
            putio.setDot(false, teal);
        }
    }

    private int enabledSourceCount() {
        int count = 0;
        for (String sourceName : TorrentSearchService.SOURCES) {
            if (searchService.enabled(sourceName)) {
                count++;
            }
        }
        return count;
    }

    private boolean hasToken() {
        return !oauthToken().isEmpty();
    }

    private String oauthToken() {
        return preferences.getString(TOKEN_KEY, "").trim();
    }

    private void hideKeyboard() {
        InputMethodManager keyboard =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        keyboard.hideSoftInputFromWindow(queryField.getWindowToken(), 0);
    }

    private void postToUi(Runnable action) {
        main.post(() -> {
            if (requestGate.isAlive()) {
                action.run();
            }
        });
    }

    private LinearLayout dialogColumn() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(12), dp(20), dp(8));
        return content;
    }

    private EditText dialogField(String hint, String value, int inputType) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setHintTextColor(muted);
        editText.setTextColor(text);
        editText.setText(value);
        editText.setSingleLine();
        editText.setInputType(inputType);
        editText.setPadding(dp(14), 0, dp(14), 0);
        editText.setBackground(rounded(field, 12, line, 1.5f));
        return editText;
    }

    private LinearLayout.LayoutParams fieldParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        params.setMargins(0, 0, 0, dp(10));
        return params;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(rounded(surface, 16, line, 1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(params);
        return card;
    }

    private Button primaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(buttonForeground);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setMinHeight(dp(44));
        button.setBackground(actionBackground(buttonBackground, goldDim, 13));
        return button;
    }

    private Button secondaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(text);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setMinHeight(dp(44));
        button.setBackground(actionBackground(raised, line, 11));
        return button;
    }

    private Button segmentButton(String label, boolean selected) {
        Button button = selected ? primaryButton(label) : secondaryButton(label);
        button.setTextSize(14.5f);
        return button;
    }

    private Button squareButton(String label, String description) {
        Button button = secondaryButton(label);
        button.setTextSize(19);
        button.setContentDescription(description);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    private TextView label(String value, float size, int color, int style) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextSize(size);
        label.setTextColor(color);
        label.setTypeface(Typeface.DEFAULT, style);
        return label;
    }

    private Drawable actionBackground(int normal, int pressed, int radius) {
        return new RippleDrawable(
                ColorStateList.valueOf(pressed),
                rounded(normal, radius, Color.TRANSPARENT, 0),
                rounded(text, radius, Color.TRANSPARENT, 0));
    }

    private GradientDrawable rounded(int color, int radius, int strokeColor, float strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (strokeColor != Color.TRANSPARENT && strokeWidth > 0) {
            drawable.setStroke(Math.max(1, Math.round(dp(1) * strokeWidth)), strokeColor);
        }
        return drawable;
    }

    private GradientDrawable dashed(int color, int radius, int strokeColor) {
        GradientDrawable drawable = rounded(color, radius, Color.TRANSPARENT, 0);
        drawable.setStroke(dp(1), strokeColor, dp(6), dp(4));
        return drawable;
    }

    private FrameLayout.LayoutParams matchFrame() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void showError(String message) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.error_title)
                .setMessage(message == null ? "Unknown error" : message)
                .setPositiveButton("OK", null)
                .show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        try {
            return value == null ? fallback : Enum.valueOf(type, value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private enum Tab {
        SEARCH,
        SAVED,
        PUTIO,
        SOURCES
    }

    private enum Phase {
        IDLE,
        LOADING,
        RESULTS,
        NO_RESULTS,
        ERROR,
        NO_SOURCES
    }

    private enum SendState {
        IDLE,
        SENDING,
        SENT
    }

    private final class NavItem {
        final Tab tab;
        final LinearLayout root;
        final ImageView icon;
        final TextView label;
        final View dot;

        NavItem(Tab tab, String title, int iconResource) {
            this.tab = tab;
            root = new LinearLayout(MainActivity.this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER);
            root.setPadding(0, dp(4), 0, dp(2));
            root.setContentDescription(title + " tab");
            root.setBackground(new RippleDrawable(
                    ColorStateList.valueOf(line), null, null));
            root.setOnClickListener(ignored -> selectTab(tab));

            FrameLayout iconFrame = new FrameLayout(MainActivity.this);
            icon = new ImageView(MainActivity.this);
            icon.setImageResource(iconResource);
            icon.setContentDescription(null);
            FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                    dp(23), dp(23), Gravity.CENTER);
            iconFrame.addView(icon, iconParams);
            dot = new View(MainActivity.this);
            dot.setVisibility(View.GONE);
            FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(
                    dp(7), dp(7), Gravity.TOP | Gravity.END);
            dotParams.setMargins(0, 0, dp(4), 0);
            iconFrame.addView(dot, dotParams);
            root.addView(iconFrame, new LinearLayout.LayoutParams(dp(36), dp(27)));

            label = MainActivity.this.label(title, 11.5f, navInactive, Typeface.BOLD);
            label.setGravity(Gravity.CENTER);
            root.addView(label);
            setActive(false);
        }

        void setActive(boolean active) {
            int color = active ? gold : navInactive;
            icon.setImageTintList(ColorStateList.valueOf(color));
            label.setTextColor(color);
        }

        void setDot(boolean visible, int color) {
            dot.setVisibility(visible ? View.VISIBLE : View.GONE);
            dot.setBackground(rounded(color, 99, Color.TRANSPARENT, 0));
        }
    }

    private final class ResultAdapter extends RecyclerView.Adapter<ResultViewHolder> {
        private final List<TorrentResult> results = new ArrayList<>();
        private final Map<String, SendState> actionStates = new HashMap<>();

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
            notifyDataSetChanged();
        }

        void setActionState(TorrentResult result, SendState state) {
            actionStates.put(result.magnet, state);
            int index = indexOf(result.magnet);
            if (index >= 0) {
                notifyItemChanged(index);
            }
        }

        SendState actionState(TorrentResult result) {
            return actionStates.containsKey(result.magnet)
                    ? actionStates.get(result.magnet) : SendState.IDLE;
        }

        int indexOf(String magnet) {
            for (int index = 0; index < results.size(); index++) {
                if (results.get(index).magnet.equals(magnet)) {
                    return index;
                }
            }
            return -1;
        }

        ArrayList<String> actionStateKeys() {
            return new ArrayList<>(actionStates.keySet());
        }

        ArrayList<String> actionStateValues() {
            ArrayList<String> values = new ArrayList<>();
            for (String key : actionStates.keySet()) {
                values.add(actionStates.get(key).name());
            }
            return values;
        }

        void restoreActionStates(List<String> keys, List<String> values) {
            for (int index = 0; index < Math.min(keys.size(), values.size()); index++) {
                actionStates.put(keys.get(index),
                        enumValue(SendState.class, values.get(index), SendState.IDLE));
            }
        }
    }

    private final class ResultViewHolder extends RecyclerView.ViewHolder {
        private final LinearLayout container;
        private final TextView badge;
        private final TextView seeders;
        private final TextView size;
        private final TextView name;
        private final Button send;

        ResultViewHolder() {
            super(new LinearLayout(MainActivity.this));
            container = (LinearLayout) itemView;
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(dp(14), dp(13), dp(14), dp(14));
            container.setBackground(rounded(surface, 16, line, 1));
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 0, dp(10));
            container.setLayoutParams(params);

            LinearLayout metadata = new LinearLayout(MainActivity.this);
            metadata.setGravity(Gravity.CENTER_VERTICAL);
            badge = label("", 11, gold, Typeface.BOLD);
            badge.setGravity(Gravity.CENTER);
            badge.setLetterSpacing(0.06f);
            badge.setPadding(dp(9), dp(4), dp(9), dp(4));
            badge.setBackground(rounded(raised, 7, line, 1));
            metadata.addView(badge);
            seeders = label("", 13, teal, Typeface.BOLD);
            seeders.setPadding(dp(8), 0, 0, 0);
            metadata.addView(seeders);
            size = label("", 12.5f, muted, Typeface.NORMAL);
            size.setPadding(dp(8), 0, 0, 0);
            metadata.addView(size);
            container.addView(metadata);

            name = label("", 15.5f, text, Typeface.BOLD);
            name.setMaxLines(3);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setPadding(0, dp(11), 0, dp(11));
            container.addView(name);

            send = primaryButton("");
            container.addView(send, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        }

        void bind(TorrentResult result) {
            badge.setText(shortSource(result.source));
            seeders.setText("▲  " + String.format("%,d", result.seeders));
            size.setText(TorrentResult.readableSize(result.sizeBytes));
            name.setText(result.name);
            SendState state = resultsAdapter.actionState(result);
            if (state == SendState.SENT) {
                send.setText(R.string.sent);
                send.setTextColor(teal);
                send.setBackground(rounded(Color.TRANSPARENT, 12, teal, 1.5f));
                send.setEnabled(false);
            } else if (state == SendState.SENDING) {
                send.setText(R.string.sending);
                send.setTextColor(muted);
                send.setBackground(rounded(raised, 12, line, 1));
                send.setEnabled(false);
            } else {
                send.setText(R.string.send);
                send.setTextColor(buttonForeground);
                send.setBackground(actionBackground(buttonBackground, goldDim, 12));
                send.setEnabled(true);
                send.setOnClickListener(ignored -> addTransfer(result));
            }
            send.setContentDescription(send.getText() + ": " + result.name);
        }
    }

    private String shortSource(String sourceName) {
        if (TorrentSearchService.SOURCE_TPB.equals(sourceName)) {
            return "TPB";
        }
        if (TorrentSearchService.SOURCE_TORRENTS_CSV.equals(sourceName)) {
            return "CSV";
        }
        return sourceName;
    }
}
