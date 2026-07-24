package com.thepiratebrowser.android;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PutIoService {
    private static final String API_BASE = "https://api.put.io/v2";

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
        String response = postForm("/transfers/add", oauthToken, "url=" + encode(magnet));
        JSONObject json = new JSONObject(response);
        JSONObject transfer = json.optJSONObject("transfer");
        return transfer == null
                ? "Transfer added to put.io."
                : "Added: " + transfer.optString("name", "transfer");
    }

    public List<Transfer> transfers(String oauthToken) throws Exception {
        return parseTransfers(get(API_BASE + "/transfers/list", oauthToken, false));
    }

    public void cancelTransfer(String oauthToken, long transferId) throws Exception {
        requirePositiveId(transferId, "transfer");
        postForm("/transfers/cancel", oauthToken, "transfer_ids=" + transferId);
    }

    public FileListing files(String oauthToken, long parentId) throws Exception {
        String response = get(
                API_BASE + "/files/list?parent_id=" + parentId
                        + "&per_page=1000&mp4_status=true",
                oauthToken,
                false
        );
        return parseFiles(response, parentId);
    }

    public void deleteFile(String oauthToken, long fileId) throws Exception {
        requirePositiveId(fileId, "file");
        postForm("/files/delete", oauthToken, "file_ids=" + fileId);
    }

    public void renameFile(String oauthToken, long fileId, String name) throws Exception {
        requirePositiveId(fileId, "file");
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Enter a file name.");
        }
        postForm("/files/rename", oauthToken,
                "file_id=" + fileId + "&name=" + encode(name.trim()));
    }

    public String hlsStreamUrl(String oauthToken, long fileId) throws Exception {
        requireToken(oauthToken);
        requirePositiveId(fileId, "file");
        return API_BASE + "/files/" + fileId + "/hls/media.m3u8"
                + "?subtitle_key=all&oauth_token=" + encode(oauthToken.trim());
    }

    public String downloadUrl(String oauthToken, long fileId) throws Exception {
        requireToken(oauthToken);
        requirePositiveId(fileId, "file");
        return API_BASE + "/files/" + fileId + "/download"
                + "?oauth_token=" + encode(oauthToken.trim());
    }

    public void verifyToken(String oauthToken) throws Exception {
        get(API_BASE + "/account/info", oauthToken, false);
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
        checkStatus(status, response);
        return response;
    }

    private static String postForm(String path, String token, String formBody) throws Exception {
        requireToken(token);
        HttpURLConnection connection = (HttpURLConnection) URI
                .create(API_BASE + path)
                .toURL()
                .openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", "Bearer " + token.trim());
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setRequestProperty("Accept", "application/json");
        byte[] body = formBody.getBytes(StandardCharsets.UTF_8);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(body);
        }
        int status = connection.getResponseCode();
        String response = read(status >= 200 && status < 300
                ? connection.getInputStream()
                : connection.getErrorStream());
        connection.disconnect();
        checkStatus(status, response);
        return response;
    }

    static List<Transfer> parseTransfers(String response) throws Exception {
        JSONArray array = new JSONObject(response).optJSONArray("transfers");
        if (array == null) {
            throw new IllegalStateException("put.io response was missing transfers.");
        }
        List<Transfer> transfers = new ArrayList<>();
        for (int index = 0; index < array.length(); index++) {
            JSONObject item = array.getJSONObject(index);
            transfers.add(new Transfer(
                    item.optLong("id"),
                    item.optLong("file_id"),
                    item.optString("name", "(unnamed transfer)"),
                    item.optString("status"),
                    item.optInt("percent_done"),
                    item.optString("error_message")
            ));
        }
        return Collections.unmodifiableList(transfers);
    }

    static FileListing parseFiles(String response, long requestedParentId) throws Exception {
        JSONObject root = new JSONObject(response);
        JSONArray array = root.optJSONArray("files");
        if (array == null) {
            throw new IllegalStateException("put.io response was missing files.");
        }
        List<FileItem> files = new ArrayList<>();
        for (int index = 0; index < array.length(); index++) {
            JSONObject item = array.getJSONObject(index);
            files.add(new FileItem(
                    item.optLong("id"),
                    item.optLong("parent_id"),
                    item.optString("name", "(unnamed file)"),
                    item.optString("content_type", "application/octet-stream"),
                    item.optString("file_type"),
                    item.optLong("size"),
                    item.optBoolean("is_mp4_available"),
                    item.optBoolean("need_convert")
            ));
        }
        JSONObject parent = root.optJSONObject("parent");
        long directoryId = parent == null ? requestedParentId
                : parent.optLong("id", requestedParentId);
        long parentDirectoryId = parent == null ? 0 : parent.optLong("parent_id");
        String name = parent == null
                ? (requestedParentId == 0 ? "put.io files" : "Folder")
                : parent.optString("name", "put.io files");
        return new FileListing(files, directoryId, parentDirectoryId, name);
    }

    private static void checkStatus(int status, String response) {
        if (status == 401 || status == 403) {
            throw new IllegalStateException("put.io rejected the token. Reconnect and try again.");
        }
        if (status < 200 || status >= 300) {
            String detail;
            try {
                detail = new JSONObject(response).optString("error_message", response);
            } catch (Exception ignored) {
                detail = response;
            }
            throw new IllegalStateException("put.io returned HTTP " + status + ": " + detail);
        }
    }

    private static void requireToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalStateException("Connect put.io before using account controls.");
        }
    }

    private static void requirePositiveId(long id, String kind) {
        if (id <= 0) {
            throw new IllegalArgumentException("A valid put.io " + kind + " is required.");
        }
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

    public static final class Transfer {
        public final long id;
        public final long fileId;
        public final String name;
        public final String status;
        public final int percentDone;
        public final String errorMessage;

        Transfer(long id, long fileId, String name, String status,
                 int percentDone, String errorMessage) {
            this.id = id;
            this.fileId = fileId;
            this.name = name;
            this.status = status;
            this.percentDone = percentDone;
            this.errorMessage = errorMessage;
        }

        public boolean isDone() {
            return percentDone >= 100
                    || "DONE".equalsIgnoreCase(status)
                    || "COMPLETED".equalsIgnoreCase(status)
                    || "FINISHED".equalsIgnoreCase(status)
                    || "SEEDING".equalsIgnoreCase(status);
        }
    }

    public static final class FileItem {
        public final long id;
        public final long parentId;
        public final String name;
        public final String contentType;
        public final String fileType;
        public final long size;
        public final boolean mp4Available;
        public final boolean needsConversion;

        FileItem(long id, long parentId, String name, String contentType, String fileType,
                 long size, boolean mp4Available, boolean needsConversion) {
            this.id = id;
            this.parentId = parentId;
            this.name = name;
            this.contentType = contentType;
            this.fileType = fileType;
            this.size = size;
            this.mp4Available = mp4Available;
            this.needsConversion = needsConversion;
        }

        public boolean isDirectory() {
            return "application/x-directory".equalsIgnoreCase(contentType)
                    || "FOLDER".equalsIgnoreCase(fileType);
        }

        public boolean isVideo() {
            return !isDirectory() && (contentType.toLowerCase().startsWith("video/")
                    || "VIDEO".equalsIgnoreCase(fileType));
        }
    }

    public static final class FileListing {
        public final List<FileItem> files;
        public final long directoryId;
        public final long parentDirectoryId;
        public final String directoryName;

        FileListing(List<FileItem> files, long directoryId, long parentDirectoryId,
                    String directoryName) {
            this.files = Collections.unmodifiableList(new ArrayList<>(files));
            this.directoryId = directoryId;
            this.parentDirectoryId = parentDirectoryId;
            this.directoryName = directoryName;
        }
    }

    private static final class PendingAuthorization extends Exception {
    }
}
