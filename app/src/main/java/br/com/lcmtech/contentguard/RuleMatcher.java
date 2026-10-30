package br.com.lcmtech.contentguard;

import java.net.IDN;
import java.net.URI;
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
