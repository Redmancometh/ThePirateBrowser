package com.thepiratebrowser.model;

public record PutIoFile(
        long id,
        long parentId,
        String name,
        String contentType,
        String fileType,
        long size,
        boolean mp4Available,
        boolean needsConversion
) {
    public boolean isDirectory() {
        return "application/x-directory".equalsIgnoreCase(contentType)
                || "FOLDER".equalsIgnoreCase(fileType);
    }

    public boolean isVideo() {
        return !isDirectory() && (contentType.toLowerCase().startsWith("video/")
                || "VIDEO".equalsIgnoreCase(fileType));
    }
}
