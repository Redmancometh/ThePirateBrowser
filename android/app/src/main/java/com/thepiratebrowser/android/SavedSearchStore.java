package com.thepiratebrowser.android;

import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

public final class SavedSearchStore {
    private static final String KEY = "saved_searches.v1";
    private final SharedPreferences preferences;

    public SavedSearchStore(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    public synchronized List<SavedSearch> all() {
        return decode(preferences.getString(KEY, "[]"));
    }

    public synchronized void upsert(SavedSearch search) {
        List<SavedSearch> searches = all();
        boolean replaced = false;
        for (int index = 0; index < searches.size(); index++) {
            if (searches.get(index).id.equals(search.id)) {
                searches.set(index, search);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            searches.add(search);
        }
        save(searches);
    }

    public synchronized void remove(String id) {
        List<SavedSearch> searches = all();
        searches.removeIf(search -> search.id.equals(id));
        save(searches);
    }

    private void save(List<SavedSearch> searches) {
        preferences.edit().putString(KEY, encode(searches)).apply();
    }

    static String encode(List<SavedSearch> searches) {
        JSONArray array = new JSONArray();
        for (SavedSearch search : searches) {
            array.put(search.toJson());
        }
        return array.toString();
    }

    static List<SavedSearch> decode(String encoded) {
        List<SavedSearch> searches = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(encoded == null ? "[]" : encoded);
            for (int index = 0; index < array.length(); index++) {
                SavedSearch search = SavedSearch.fromJson(array.getJSONObject(index));
                if (!search.query.trim().isEmpty()) {
                    searches.add(search);
                }
            }
        } catch (Exception ignored) {
            // Corrupt preferences should not prevent the app from opening.
        }
        return searches;
    }
}
