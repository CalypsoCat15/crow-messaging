package com.crowmessenger.messages;

final class LinkPreview {
    final String pageUrl;
    final String siteName;
    final String title;
    final String description;
    final String imageUrl;

    LinkPreview(
            String pageUrl,
            String siteName,
            String title,
            String description,
            String imageUrl
    ) {
        this.pageUrl = safe(pageUrl);
        this.siteName = safe(siteName);
        this.title = safe(title);
        this.description = safe(description);
        this.imageUrl = safe(imageUrl);
    }

    boolean hasPreviewContent() {
        return !title.isEmpty() || !description.isEmpty() || !imageUrl.isEmpty();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
