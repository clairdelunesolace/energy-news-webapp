package com.carya.energynews.watchlistdiscovery;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;

@Component
public class DiscoveryUrlNormalizer {

    public Optional<String> normalize(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String normalized = value.strip();
        int fragmentStart = normalized.indexOf('#');
        if (fragmentStart >= 0) {
            normalized = normalized.substring(0, fragmentStart);
        }

        try {
            URI uri = URI.create(normalized);
            String scheme = uri.getScheme();
            if (scheme == null
                    || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null
                    || uri.getHost().isBlank()
                    || uri.getUserInfo() != null) {
                return Optional.empty();
            }
            return Optional.of(normalized);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    String origin(String normalizedUrl) {
        URI uri = URI.create(normalizedUrl);
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        boolean defaultPort = (scheme.equals("http") && port == 80)
                || (scheme.equals("https") && port == 443);
        return scheme + "://" + host + (port < 0 || defaultPort ? "" : ":" + port);
    }

    String hostKey(String url) {
        try {
            String host = URI.create(url.strip()).getHost();
            if (host == null) {
                return null;
            }
            String normalized = host.toLowerCase(Locale.ROOT);
            return normalized.startsWith("www.") ? normalized.substring(4) : normalized;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
