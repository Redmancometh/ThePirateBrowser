package com.thepiratebrowser.android;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class SavedSearch {
    public final String id;
    public String name;
    public String query;
    public int minimumSeeders;
    public boolean enabled;
    public long lastChecked;
    public final Set<String> seenResultIds;

    public SavedSearch(String name, String query, int minimumSeeders) {
        this(UUID.randomUUID().toString(), name, query, minimumSeeders,
                true, 0, new LinkedHashSet<>());
    }

    SavedSearch(String id, String name, String query, int minimumSeeders,
                boolean enabled, long lastChecked, Set<String> seenResultIds) {
        this.id = id;
        this.name = name == null ? "" : name;
        this.query = query == null ? "" : query;
        this.minimumSeeders = Math.max(0, minimumSeeders);
        this.enabled = enabled;
        this.lastChecked = Math.max(0, lastChecked);
        this.seenResultIds = new LinkedHashSet<>(seenResultIds);
    }

    public String displayName() {
        return name.trim().isEmpty() ? query : name;
    }

    public List<TorrentResult> filter(List<TorrentResult> results) {
        List<TorrentResult> filtered = new java.util.ArrayList<>();
        for (TorrentResult result : results) {
            if (result.seeders >= minimumSeeders) {
                filtered.add(result);
            }
        }
        return filtered;
    }

    public int record(List<TorrentResult> results, long checkedAt) {
        Set<String> current = new LinkedHashSet<>();
        int newCount = 0;
        boolean hasBaseline = lastChecked > 0;
        for (TorrentResult result : filter(results)) {
            String identity = result.magnet.trim().toLowerCase();
            current.add(identity);
            if (hasBaseline && !seenResultIds.contains(identity)) {
                newCount++;
            }
        }
        seenResultIds.clear();
        seenResultIds.addAll(current);
        lastChecked = checkedAt;
        return newCount;
    }

    JSONObject toJson() {
        JSONArray seen = new JSONArray();
        for (String identity : seenResultIds) {
            seen.put(identity);
        }
        try {
            return new JSONObject()
                    .put("id", id)
                    .put("name", name)
                    .put("query", query)
                    .put("minimumSeeders", minimumSeeders)
                    .put("enabled", enabled)
                    .put("lastChecked", lastChecked)
                    .put("seenResultIds", seen);
        } catch (JSONException impossible) {
            throw new IllegalStateException("Could not save search.", impossible);
        }
    }

    static SavedSearch fromJson(JSONObject json) {
        Set<String> seen = new LinkedHashSet<>();
        JSONArray array = json.optJSONArray("seenResultIds");
        if (array != null) {
            for (int index = 0; index < array.length(); index++) {
                String identity = array.optString(index);
                if (!identity.trim().isEmpty()) {
                    seen.add(identity);
                }
            }
        }
        String id = json.optString("id");
        if (id.trim().isEmpty()) {
            id = UUID.randomUUID().toString();
        }
        return new SavedSearch(
                id,
                json.optString("name"),
                json.optString("query"),
                json.optInt("minimumSeeders"),
                json.optBoolean("enabled", true),
                json.optLong("lastChecked"),
                seen
        );
    }
}
