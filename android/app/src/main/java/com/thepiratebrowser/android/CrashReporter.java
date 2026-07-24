package com.thepiratebrowser.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

final class CrashReporter {
    private static final String PREFS = "pirate_browser_crash_reporter";
    private static final String PENDING_REPORT = "pending_report";
    private static final String ENDPOINT =
            "https://piratebrowser.2ez.club/api/crash-report";
    private static final int MAX_STACK_CHARS = 48_000;
    private static final ExecutorService NETWORK = Executors.newSingleThreadExecutor();
    private static final Pattern OAUTH_QUERY = Pattern.compile(
            "(?i)(oauth_token(?:%3[dD]|=))[^&\\s]+");
    private static final Pattern BEARER = Pattern.compile(
            "(?i)(Bearer\\s+)[A-Za-z0-9._~+/=-]+");

    private CrashReporter() {
    }

    static void install(Context context) {
        Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> {
            try {
                String report = buildReport(thread, failure).toString();
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit()
                        .putString(PENDING_REPORT, report)
                        .commit();
            } catch (Throwable ignored) {
                // Crash collection must never replace or delay the original crash.
            } finally {
                if (previous != null) {
                    previous.uncaughtException(thread, failure);
                }
            }
        });
    }

    static void offerPendingReport(Activity activity, Runnable afterPrompt) {
        SharedPreferences preferences =
                activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String report = preferences.getString(PENDING_REPORT, "");
        if (report == null || report.isBlank()) {
            afterPrompt.run();
            return;
        }

        new AlertDialog.Builder(activity)
                .setTitle("Send crash report?")
                .setMessage("Pirate Browser saved the previous crash. Send its app "
                        + "version, Android/device model, and stack trace so it can be "
                        + "fixed? OAuth tokens, searches, and put.io data are not included.")
                .setNegativeButton("Don't send", (dialog, which) -> {
                    preferences.edit().remove(PENDING_REPORT).apply();
                    afterPrompt.run();
                })
                .setPositiveButton("Send report", (dialog, which) -> {
                    NETWORK.execute(() -> sendPending(activity, preferences, report));
                    afterPrompt.run();
                })
                .setOnCancelListener(dialog -> afterPrompt.run())
                .show();
    }

    private static void sendPending(
            Activity activity,
            SharedPreferences preferences,
            String report
    ) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(ENDPOINT).toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(15_000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            byte[] body = report.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }
            int status = connection.getResponseCode();
            if (status >= 200 && status < 300) {
                preferences.edit().remove(PENDING_REPORT).apply();
                activity.runOnUiThread(() ->
                        Toast.makeText(activity, "Crash report sent.", Toast.LENGTH_SHORT).show());
            } else {
                throw new IllegalStateException("Crash endpoint returned " + status);
            }
        } catch (Exception failure) {
            activity.runOnUiThread(() -> Toast.makeText(
                    activity,
                    "Crash report could not be sent. It will remain on this device.",
                    Toast.LENGTH_LONG
            ).show());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static JSONObject buildReport(Thread thread, Throwable failure) throws Exception {
        StringWriter trace = new StringWriter();
        failure.printStackTrace(new PrintWriter(trace));
        String sanitizedStack = sanitize(trace.toString());
        if (sanitizedStack.length() > MAX_STACK_CHARS) {
            sanitizedStack = sanitizedStack.substring(0, MAX_STACK_CHARS)
                    + "\n[stack trace truncated]";
        }

        JSONObject report = new JSONObject();
        report.put("schema", 1);
        report.put("reportId", UUID.randomUUID().toString());
        report.put("occurredAtMs", System.currentTimeMillis());
        report.put("canary", BuildConfig.BUILD_CANARY);
        report.put("versionName", BuildConfig.VERSION_NAME);
        report.put("androidRelease", Build.VERSION.RELEASE);
        report.put("sdkInt", Build.VERSION.SDK_INT);
        report.put("manufacturer", sanitize(Build.MANUFACTURER));
        report.put("model", sanitize(Build.MODEL));
        report.put("threadName", sanitize(thread == null ? "unknown" : thread.getName()));
        report.put("exceptionType", failure.getClass().getName());
        report.put("exceptionMessage", sanitize(String.valueOf(failure.getMessage())));
        report.put("stackTrace", sanitizedStack);
        return report;
    }

    static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        String withoutQueryToken = OAUTH_QUERY.matcher(value).replaceAll("$1[REDACTED]");
        return BEARER.matcher(withoutQueryToken).replaceAll("$1[REDACTED]");
    }
}
