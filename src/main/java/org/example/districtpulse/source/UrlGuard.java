package org.example.districtpulse.source;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;

/** Blocks non-http(s) URLs and internal addresses so the Collector cannot point the server at its own network. */
@Component
public class UrlGuard {
    @org.springframework.beans.factory.annotation.Value("${source.allowed-hosts:news.google.com,imd.gov.in,mausam.imd.gov.in,agmarknet.gov.in,api.data.gov.in,pudukkottai.nic.in,tn.gov.in}")
    private java.util.List<String> allowedHosts;


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
        if(uri.getUserInfo()!=null) throw new IllegalArgumentException("Credentials in URLs are not allowed");
        if(uri.getPort()!=-1 && uri.getPort()!=80 && uri.getPort()!=443) throw new IllegalArgumentException("Only standard web ports are allowed");
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL has no host");
        }
        if (allowedHosts == null || allowedHosts.stream().noneMatch(h -> host.equalsIgnoreCase(h.trim()))) throw new IllegalArgumentException("Host is not approved. Add the exact authorised hostname to SOURCE_ALLOWED_HOSTS on the server");
        try {
            for (InetAddress a : InetAddress.getAllByName(host)) {
                if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress()
                        || a.isSiteLocalAddress() || a.isMulticastAddress() || (a.getAddress().length==16 && (a.getAddress()[0]&0xfe)==0xfc)) {
                    throw new IllegalArgumentException("Internal or private addresses are not allowed");
                }
            }
        } catch (java.net.UnknownHostException e) {
            throw new IllegalArgumentException("Host could not be resolved: " + host);
        }
    }
}
