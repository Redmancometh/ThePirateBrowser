package com.thepiratebrowser.service;

import com.thepiratebrowser.model.TorrentResult;

import java.util.List;

public interface TorrentSource {
    String id();

    String name();

    List<TorrentResult> search(String query, int minimumSeeders);
}
