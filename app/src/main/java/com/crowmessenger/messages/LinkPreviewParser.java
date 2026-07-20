package com.crowmessenger.messages;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class LinkPreviewParser {
    private static final int MAX_SITE_NAME = 60;
    private static final int MAX_TITLE = 140;
    private static final int MAX_DESCRIPTION = 220;

    private LinkPreviewParser() {
    }

    static LinkPreview parse(String html, String pageUrl) {
        return parse(Jsoup.parse(html == null ? "" : html, pageUrl), pageUrl);
    }

    static LinkPreview parse(Document document, String pageUrl) {
        Map<String, Element> metadata = metadata(document);
        String title = firstContent(metadata, "og:title", "twitter:title");
        if (title.isEmpty()) {
            title = document.title();
        }
        String description = firstContent(
                metadata,
                "og:description",
                "twitter:description",
                "description"
        );
        String siteName = firstContent(metadata, "og:site_name", "application-name");
        if (siteName.isEmpty()) {
            siteName = hostName(pageUrl);
        }
        Element image = firstElement(
                metadata,
                "og:image:secure_url",
                "og:image",
                "twitter:image",
                "twitter:image:src"
        );
        String imageUrl = absoluteContentUrl(image);
        if (imageUrl.isEmpty()) {
            Element imageLink = document.selectFirst("link[rel=image_src][href]");
            imageUrl = imageLink == null ? "" : imageLink.absUrl("href");
        }
        return new LinkPreview(
                pageUrl,
                clean(siteName, MAX_SITE_NAME),
                clean(title, MAX_TITLE),
                clean(description, MAX_DESCRIPTION),
                imageUrl
        );
    }

    private static Map<String, Element> metadata(Document document) {
        HashMap<String, Element> metadata = new HashMap<>();
        for (Element element : document.select("meta[content]")) {
            String key = element.hasAttr("property")
                    ? element.attr("property")
                    : element.attr("name");
            key = key.trim().toLowerCase(Locale.ROOT);
            if (!key.isEmpty()) {
                metadata.putIfAbsent(key, element);
            }
        }
        return metadata;
    }

    private static String firstContent(Map<String, Element> metadata, String... keys) {
        Element element = firstElement(metadata, keys);
        return element == null ? "" : element.attr("content");
    }

    private static Element firstElement(Map<String, Element> metadata, String... keys) {
        for (String key : keys) {
            Element element = metadata.get(key);
            if (element != null && !element.attr("content").trim().isEmpty()) {
                return element;
            }
        }
        return null;
    }

    private static String absoluteContentUrl(Element element) {
        if (element == null) {
            return "";
        }
        String absolute = element.absUrl("content");
        return absolute.isEmpty() ? element.attr("content").trim() : absolute;
    }

    private static String hostName(String pageUrl) {
        try {
            String host = URI.create(pageUrl).getHost();
            if (host == null) {
                return "";
            }
            return host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private static String clean(String value, int maximumLength) {
        String cleaned = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (cleaned.length() <= maximumLength) {
            return cleaned;
        }
        return cleaned.substring(0, Math.max(0, maximumLength - 1)).trim() + "\u2026";
    }
}
