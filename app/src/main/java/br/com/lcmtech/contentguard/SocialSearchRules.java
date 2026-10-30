package br.com.lcmtech.contentguard;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Pure Java policy for TikTok and Kwai: in-app searches and, since 0.7, the
 * video visible in the feed. Feed checks read only what is on screen and
 * never store it.
 */
public final class SocialSearchRules {
    private static final Set<String> PACKAGES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "com.zhiliaoapp.musically",    // TikTok (Brazil and global)
                    "com.zhiliaoapp.musically.go", // TikTok Lite
                    "com.ss.android.ugc.trill",    // TikTok regional variant
                    "com.kwai.video"               // Kwai Brazil
            )));

    /** Feed text changes constantly; scanning more often only costs battery. */
    public static final long FEED_SCAN_INTERVAL_MS = 400L;
    /**
     * A second match this soon after skipping means the blocked content is not
     * a single feed video (search results, profile, another blocked video).
     */
    public static final long FEED_ESCALATE_WINDOW_MS = 4000L;

    private SocialSearchRules() {}

    /** True: leave the screen (BACK). False: swipe to the next video. */
    public static boolean shouldLeaveFeed(long lastSkipElapsed, long nowElapsed) {
        return lastSkipElapsed > 0L
                && nowElapsed - lastSkipElapsed <= FEED_ESCALATE_WINDOW_MS;
    }

    public static boolean supportsPackage(String packageName) {
        return packageName != null && PACKAGES.contains(packageName);
    }

    /**
     * A recognisable search input (resource id, hint or description).
     * Words such as "edit", "text" and "input" alone are deliberately ignored
     * because those also apply to comments, messages and captions.
     */
    public static boolean isSearchFieldLabel(String label) {
        if (label == null || label.trim().isEmpty()) return false;
        String value = RuleMatcher.normalize(label);
        String padded = " " + value + " ";
        for (String word : new String[] {
                "search", "pesquisar", "pesquisa", "buscar", "busca",
                "query", "keyword"
        }) {
            if (padded.contains(" " + word + " ")) return true;
        }
        // Resource IDs sometimes concatenate words: searchEditText, searchBar.
        return value.contains("searchbar") || value.contains("searchbox")
                || value.contains("searchfield") || value.contains("searchinput")
                || value.contains("searchtext") || value.contains("searchview")
                || value.contains("searchhint")
                || value.contains("searchheader");
    }
}
