package com.crowmessenger.messages;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;

final class LinkPreviewUrlPolicy {
    private LinkPreviewUrlPolicy() {
    }

    static String normalizedFetchUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.length() > 2_048) {
            return "";
        }
        try {
            URI source = new URI(rawUrl.trim());
            String scheme = source.getScheme();
            String host = source.getHost();
            if (scheme == null || host == null || source.getUserInfo() != null) {
                return "";
            }
            scheme = scheme.toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return "";
            }
            host = host.toLowerCase(Locale.ROOT);
            if (isLocalHostName(host)) {
                return "";
            }
            int port = source.getPort();
            if (port != -1 && port != 80 && port != 443) {
                return "";
            }
            return new URI(
                    "https",
                    null,
                    host,
                    port == 80 ? -1 : port,
                    source.getPath(),
                    source.getQuery(),
                    null
            ).toASCIIString();
        } catch (URISyntaxException | IllegalArgumentException ignored) {
            return "";
        }
    }

    static boolean isPublicHost(String host) {
        if (host == null || isLocalHostName(host.toLowerCase(Locale.ROOT))) {
            return false;
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) {
                return false;
            }
            for (InetAddress address : addresses) {
                if (!isPublicAddress(address)) {
                    return false;
                }
            }
            return true;
        } catch (UnknownHostException ignored) {
            return false;
        }
    }

    static boolean isPublicAddress(InetAddress address) {
        if (address == null
                || address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address && bytes.length == 4) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            return !(first == 100 && second >= 64 && second <= 127)
                    && !(first == 198 && (second == 18 || second == 19));
        }
        return bytes.length != 16 || (bytes[0] & 0xfe) != 0xfc;
    }

    private static boolean isLocalHostName(String host) {
        return host.isEmpty()
                || "localhost".equals(host)
                || host.endsWith(".localhost")
                || host.endsWith(".local")
                || host.endsWith(".internal");
    }
}
