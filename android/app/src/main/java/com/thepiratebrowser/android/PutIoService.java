package com.thepiratebrowser.android;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class PutIoService {
    public DeviceCode requestDeviceCode(String clientId) throws Exception {
        String response = get(
                "https://api.put.io/v2/oauth2/oob/code?app_id="
                        + encode(clientId)
                        + "&client_name="
                        + encode("Pirate Browser Android"),
                null,
                false
        );
        JSONObject json = new JSONObject(response);
        String code = json.optString("code");
        if (code.trim().isEmpty()) {
            throw new IllegalStateException("put.io did not provide a link code.");
        }
        return new DeviceCode(code);
    }

    public String waitForDeviceToken(DeviceCode deviceCode) throws Exception {
        for (int attempt = 0; attempt < 90; attempt++) {
            try {
                String response = get(
                        "https://api.put.io/v2/oauth2/oob/code/"
                                + encode(deviceCode.code),
                        null,
                        true
                );
                String token = new JSONObject(response).optString("oauth_token");
                if (!token.trim().isEmpty()) return token;
            } catch (PendingAuthorization ignored) {
                // The user has not approved the displayed code yet.
            }
            Thread.sleep(2_000);
        }
        throw new IllegalStateException("put.io linking timed out. Try again.");
    }

    public String addTransfer(String oauthToken, String magnet) throws Exception {
        if (oauthToken == null || oauthToken.trim().isEmpty()) {
            throw new IllegalStateException("Connect put.io before adding a transfer.");
        }

        HttpURLConnection connection = (HttpURLConnection) URI
                .create("https://api.put.io/v2/transfers/add")
                .toURL()
                .openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", "Bearer " + oauthToken.trim());
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setRequestProperty("Accept", "application/json");
        byte[] body = ("url=" + encode(magnet))
                .getBytes(StandardCharsets.UTF_8);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(body);
        }

        int status = connection.getResponseCode();
        String response = read(status >= 200 && status < 300
                ? connection.getInputStream()
                : connection.getErrorStream());
        connection.disconnect();
        if (status == 401 || status == 403) {
            throw new IllegalStateException("put.io rejected the token. Reconnect and try again.");
        }
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("put.io returned HTTP " + status + ": " + response);
        }
        JSONObject json = new JSONObject(response);
        JSONObject transfer = json.optJSONObject("transfer");
        return transfer == null
                ? "Transfer added to put.io."
                : "Added: " + transfer.optString("name", "transfer");
    }

    public void verifyToken(String oauthToken) throws Exception {
        get("https://api.put.io/v2/account/info", oauthToken, false);
    }

    private static String get(String address, String token, boolean pendingAllowed) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(address).toURL().openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("Accept", "application/json");
        if (token != null) connection.setRequestProperty("Authorization", "Bearer " + token.trim());
        int status = connection.getResponseCode();
        String response = read(status >= 200 && status < 300
                ? connection.getInputStream()
                : connection.getErrorStream());
        connection.disconnect();
        if (pendingAllowed && (status == 400 || status == 404)) {
            throw new PendingAuthorization();
        }
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("put.io returned HTTP " + status + ": " + response);
        }
        return response;
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) return "";
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
            return result.toString();
        }
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    public static final class DeviceCode {
        public final String code;

        public DeviceCode(String code) {
            this.code = code;
        }
    }

    private static final class PendingAuthorization extends Exception {
    }
}
