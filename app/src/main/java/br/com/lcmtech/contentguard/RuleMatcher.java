package br.com.lcmtech.contentguard;

import java.net.IDN;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;

/** Pure Java matcher: deliberately independent from Android, so it can be unit-tested. */
public final class RuleMatcher {
    private RuleMatcher() {}

    /** Case/accent/punctuation insensitive phrase matching. */
    public static boolean containsPhrase(String visibleText, String forbiddenPhrase) {
        String haystack = normalize(visibleText);
        String needle = normalize(forbiddenPhrase);
        if (needle.isEmpty() || haystack.isEmpty()) return false;
        return (" " + haystack + " ").contains(" " + needle + " ");
    }

    public static String normalize(String value) {
        if (value == null) return "";
        String ascii = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ");
        return ascii.trim().replaceAll("\\s+", " ");
    }

    /**
     * Recognizes a query already submitted through a Google suggestion.
     * Only Google /search URLs are inspected; ordinary links and YouTube
     * video IDs cannot be classified by their URL alone.
     */
    public static boolean matchesGoogleSearchUrl(String address, String forbiddenPhrase) {
        if (address == null || address.trim().isEmpty()) return false;
        String value = address.trim();
        if (!value.matches("(?i)^https?://.*")) value = "https://" + value;
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (host == null || !host.toLowerCase(Locale.ROOT)
                    .matches("(?:www\\\\.)?google\\\\.[a-z]{2,3}(?:\\\\.[a-z]{2})?")) return false;
            if (!"/search".equals(uri.getPath())) return false;
            String query = uri.getRawQuery();
            if (query == null) return false;
            for (String pair : query.split("&")) {
                int equals = pair.indexOf('=');
                if (equals <= 0) continue;
                String key = URLDecoder.decode(pair.substring(0, equals), StandardCharsets.UTF_8);
                if (!"q".equals(key)) continue;
                String term = URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8);
                return containsPhrase(term, forbiddenPhrase);
            }
        } catch (RuntimeException ignored) {
            // Invalid URL, malformed percent encoding or invalid host.
        }
        return false;
    }

    /** Blocks exact domain and subdomains, never arbitrary strings containing the domain. */
    public static boolean matchesDomain(String urlOrDomain, String prohibitedDomain) {
        String host = extractHost(urlOrDomain);
        String wanted = canonicalDomain(prohibitedDomain);
        return !host.isEmpty() && !wanted.isEmpty()
                && (host.equals(wanted) || host.endsWith("." + wanted));
    }

    private static String canonicalDomain(String candidate) {
        String host = extractHost(candidate);
        if (!host.isEmpty()) return host.replaceFirst("^www\\.", "");
        return "";
    }

    private static String extractHost(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        String candidate = value.trim();
        if (!candidate.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) candidate = "https://" + candidate;
        try {
            URI uri = URI.create(candidate);
            String host = uri.getHost();
            if (host == null) return "";
            return IDN.toASCII(host.toLowerCase(Locale.ROOT)).replaceFirst("\\.$", "");
        } catch (Exception ignored) {
            return "";
        }
    }
}
