package com.crowmessenger.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LinkPreviewParserTest {
    @Test
    public void parse_prefersOpenGraphMetadataAndResolvesRelativeImage() {
        LinkPreview preview = LinkPreviewParser.parse(
                "<html><head>"
                        + "<title>Fallback title</title>"
                        + "<meta property='og:site_name' content='Example News'>"
                        + "<meta property='og:title' content='A better title'>"
                        + "<meta property='og:description' content='A useful description.'>"
                        + "<meta property='og:image' content='/images/story.jpg'>"
                        + "</head></html>",
                "https://example.com/news/story"
        );

        assertEquals("Example News", preview.siteName);
        assertEquals("A better title", preview.title);
        assertEquals("A useful description.", preview.description);
        assertEquals("https://example.com/images/story.jpg", preview.imageUrl);
        assertTrue(preview.hasPreviewContent());
    }

    @Test
    public void parse_usesStandardMetadataAndHostFallbacks() {
        LinkPreview preview = LinkPreviewParser.parse(
                "<html><head>"
                        + "<title>  Example   Page  </title>"
                        + "<meta name='description' content='  Clear   details here.  '>"
                        + "</head></html>",
                "https://www.example.org/page"
        );

        assertEquals("example.org", preview.siteName);
        assertEquals("Example Page", preview.title);
        assertEquals("Clear details here.", preview.description);
        assertEquals("", preview.imageUrl);
    }

    @Test
    public void parse_supportsTwitterMetadataFallback() {
        LinkPreview preview = LinkPreviewParser.parse(
                "<html><head>"
                        + "<meta name='twitter:title' content='Shared update'>"
                        + "<meta name='twitter:description' content='What happened today'>"
                        + "<meta name='twitter:image' content='https://cdn.example.com/card.png'>"
                        + "</head></html>",
                "https://example.com/update"
        );

        assertEquals("Shared update", preview.title);
        assertEquals("What happened today", preview.description);
        assertEquals("https://cdn.example.com/card.png", preview.imageUrl);
    }
}
