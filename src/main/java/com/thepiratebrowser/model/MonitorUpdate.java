package com.thepiratebrowser.model;

import java.util.List;

public record MonitorUpdate(
        String searchId,
        String searchName,
        long searchRevision,
        long sequence,
        long uiRequestGeneration,
        List<TorrentResult> results,
        String error
) {
    public long newResultCount() {
        return results.stream().filter(TorrentResult::newMatch).count();
    }

    public boolean isCurrentFor(SavedSearch current, long lastAppliedSequence) {
        return current != null
                && searchId.equals(current.getId())
                && searchRevision == current.getRevision()
                && sequence > lastAppliedSequence;
    }
}
