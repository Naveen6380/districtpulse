package org.example.districtpulse.source;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;

/** Blocks non-http(s) URLs and internal addresses so the Collector cannot point the server at its own network. */
@Component
public class UrlGuard {

    public void check(String raw) {
        URI uri;
        try {
            uri = new URI(raw == null ? "" : raw.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URL");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Only http and https URLs are allowed");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL has no host");
        }
        try {
            for (InetAddress a : InetAddress.getAllByName(host)) {
                if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress()
                        || a.isSiteLocalAddress() || a.isMulticastAddress()) {
                    throw new IllegalArgumentException("Internal or private addresses are not allowed");
                }
            }
        } catch (java.net.UnknownHostException e) {
            throw new IllegalArgumentException("Host could not be resolved: " + host);
        }
    }
}
