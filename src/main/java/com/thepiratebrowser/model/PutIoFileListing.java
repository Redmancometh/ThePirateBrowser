package com.thepiratebrowser.model;

import java.util.List;

public record PutIoFileListing(
        List<PutIoFile> files,
        long directoryId,
        long parentDirectoryId,
        String directoryName
) {
    public PutIoFileListing {
        files = List.copyOf(files);
    }
}
