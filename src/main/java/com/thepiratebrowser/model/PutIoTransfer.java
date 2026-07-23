package com.thepiratebrowser.model;

public record PutIoTransfer(
        long id,
        long fileId,
        String name,
        String status,
        int percentDone,
        String errorMessage
) {
    public boolean isDone() {
        return percentDone >= 100
                || "DONE".equalsIgnoreCase(status)
                || "COMPLETED".equalsIgnoreCase(status)
                || "FINISHED".equalsIgnoreCase(status)
                || "SEEDING".equalsIgnoreCase(status);
    }
}
